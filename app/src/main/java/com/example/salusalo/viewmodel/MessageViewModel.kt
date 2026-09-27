package com.example.salusalo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.MessageRepository
import com.example.salusalo.model.ChatMessage
import com.example.salusalo.model.ConversationSummary
import com.example.salusalo.model.parseTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class MessageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository()
    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations = _conversations.asStateFlow()
    private val _conversationsLoading = MutableStateFlow(false)
    val conversationsLoading = _conversationsLoading.asStateFlow()

    /** The open thread; null when no chat is open. */
    private val _active = MutableStateFlow<ConversationSummary?>(null)
    val active = _active.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _opening = MutableStateFlow(false)
    val opening = _opening.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()
    /** Text to prefill the input with when a chat opens (for example an allergens question). */
    private val _draft = MutableStateFlow("")
    val draft = _draft.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var threadJob: Job? = null
    private var sessionJob: Job? = null
    private var sessionProfileId: String? = null
    /** Whether the running inbox load shows its error (a quiet load becomes visible when the page asks too). */
    private var reportLoadError = false

    /**
     * Loads the inbox and keeps it live for the signed-in profile, so the chat
     * badge on Home is current before Messages is opened: a new message in any
     * of the caller's threads updates that thread's preview, time and unread
     * count. The initial load is quiet; a failure only leaves the badge as it is.
     */
    fun start(profileId: String) {
        if (sessionJob?.isActive == true && sessionProfileId == profileId) return
        sessionJob?.cancel()
        sessionProfileId = profileId
        loadConversations(quiet = true)
        sessionJob = viewModelScope.launch {
            repository.allMessageInserts()
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) false
                    else { delay(minOf(30_000L, 2_000L shl attempt.toInt().coerceAtMost(4))); true }
                }
                .collect { applyIncoming(it, profileId) }
        }
    }

    /** Reloads the inbox; [quiet] loads (Home start, return to the app) never show an error. */
    fun loadConversations(quiet: Boolean = false) {
        if (_conversationsLoading.value) {
            if (!quiet) reportLoadError = true
            return
        }
        reportLoadError = !quiet
        viewModelScope.launch {
            _conversationsLoading.value = true
            try {
                mergeConversations(repository.getMyConversations(), replace = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (reportLoadError) _message.value = errorText(error)
            } finally {
                _conversationsLoading.value = false
            }
        }
    }

    /** Applies a message that arrived through Realtime to its thread in the inbox. */
    private suspend fun applyIncoming(message: ChatMessage, profileId: String) {
        if (_conversations.value.none { it.id == message.conversationId }) {
            // A thread the inbox does not have yet, for example a chat someone just started.
            val summary = try {
                repository.getConversation(message.conversationId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            } ?: return
            mergeConversations(listOf(summary), replace = false)
            return
        }
        val sentAt = parseTimestamp(message.createdAt)
        val mine = message.senderId == profileId
        _conversations.update { list ->
            val thread = list.firstOrNull { it.id == message.conversationId } ?: return@update list
            // Already in the thread (a reload that ran after it was sent) or older than its last message.
            if (sentAt != null && lastMessageTime(thread) >= sentAt) return@update list
            val updated = thread.copy(
                lastMessage = message.body,
                lastMessageAt = message.createdAt,
                lastMessageMine = mine,
                unreadCount = if (mine || _active.value?.id == thread.id) thread.unreadCount else thread.unreadCount + 1
            )
            listOf(updated) + list.filterNot { it.id == thread.id }
        }
    }

    /**
     * Puts [loaded] threads into the inbox. A thread that Realtime already moved
     * past the loaded copy keeps its newer state, and the open chat stays read.
     * [replace] drops the threads that [loaded] does not contain.
     */
    private fun mergeConversations(loaded: List<ConversationSummary>, replace: Boolean) {
        _conversations.update { current ->
            val known = current.associateBy { it.id }
            val activeId = _active.value?.id
            val merged = loaded.map { fresh ->
                val kept = known[fresh.id]?.takeIf { lastMessageTime(it) > lastMessageTime(fresh) } ?: fresh
                if (kept.id == activeId) kept.copy(unreadCount = 0) else kept
            }
            val others = if (replace) emptyList() else current.filterNot { thread -> loaded.any { it.id == thread.id } }
            (merged + others).sortedByDescending { parseTimestamp(it.lastMessageAt ?: it.createdAt) ?: Instant.EPOCH }
        }
    }

    private fun lastMessageTime(thread: ConversationSummary) = parseTimestamp(thread.lastMessageAt) ?: Instant.EPOCH

    fun openForListing(foodPostId: String, draft: String = "") =
        open(draft) { repository.startListingConversation(foodPostId).id }

    fun openForReservation(reservationId: String) =
        open("") { repository.openReservationConversation(reservationId).id }

    fun openConversation(conversationId: String) = open("") { conversationId }

    private fun open(draft: String, resolveId: suspend () -> String) {
        threadJob?.cancel()
        _active.value = null
        _messages.value = emptyList()
        _draft.value = draft
        _opening.value = true
        threadJob = viewModelScope.launch {
            val id = try {
                val conversationId = resolveId()
                val summary = repository.getConversation(conversationId) ?: error("Conversation not found.")
                _messages.value = repository.getMessages(conversationId)
                _active.value = summary
                conversationId
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = errorText(error)
                null
            } finally {
                _opening.value = false
            }
            if (id == null) return@launch
            markActiveRead()
            repository.messageInserts(id)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) false
                    else { delay(minOf(30_000L, 2_000L shl attempt.toInt().coerceAtMost(4))); true }
                }
                .collect { incoming ->
                    addMessage(incoming)
                    if (_active.value?.id == id) markActiveRead()
                }
        }
    }

    /** Sends [body] in the open thread; [onSent] runs after the server accepted it. */
    fun send(body: String, onSent: () -> Unit = {}) {
        val conversation = _active.value ?: return
        if (_sending.value || body.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            runCatching { repository.sendMessage(conversation.id, body) }
                .onSuccess { sent -> addMessage(sent); _draft.value = ""; onSent() }
                .onFailure { _message.value = errorText(it) }
            _sending.value = false
        }
    }

    /** Marks the open thread read on the server (this also marks its message alerts read). */
    fun markActiveRead() {
        val id = _active.value?.id ?: return
        _conversations.update { list -> list.map { if (it.id == id) it.copy(unreadCount = 0) else it } }
        viewModelScope.launch { runCatching { repository.markRead(id) } }
    }

    fun close() {
        threadJob?.cancel()
        threadJob = null
        _active.value = null
        _messages.value = emptyList()
        _draft.value = ""
        _opening.value = false
        loadConversations()
    }

    fun clearMessage() { _message.value = null }

    fun clearSession() {
        sessionJob?.cancel()
        sessionJob = null
        sessionProfileId = null
        threadJob?.cancel()
        threadJob = null
        _conversations.value = emptyList()
        _active.value = null
        _messages.value = emptyList()
        _draft.value = ""
        _message.value = null
    }

    private fun addMessage(message: ChatMessage) {
        _messages.update { list ->
            if (list.any { it.id == message.id }) list
            else (list + message).sortedBy { parseTimestamp(it.createdAt) ?: Instant.EPOCH }
        }
    }

    private fun errorText(error: Throwable) = error.message?.takeIf { it.isNotBlank() }
        ?: "Could not complete the message request. Check your connection and try again."
}

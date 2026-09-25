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

    fun loadConversations() {
        if (_conversationsLoading.value) return
        viewModelScope.launch {
            _conversationsLoading.value = true
            runCatching { repository.getMyConversations() }
                .onSuccess { _conversations.value = it }
                .onFailure { _message.value = errorText(it) }
            _conversationsLoading.value = false
        }
    }

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

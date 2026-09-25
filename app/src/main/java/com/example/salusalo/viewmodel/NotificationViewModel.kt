package com.example.salusalo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.NotificationRepository
import com.example.salusalo.data.SupabaseClientProvider
import com.example.salusalo.model.AppNotification
import com.example.salusalo.model.NotificationPreferences
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NotificationRepository()
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()
    val unreadCount = _notifications.map { list -> list.count { it.readAt == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val _preferences = MutableStateFlow<NotificationPreferences?>(null)
    val preferences = _preferences.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var sessionJob: Job? = null
    private var sessionProfileId: String? = null

    /**
     * Loads the alerts and keeps them live for the signed-in profile: new alerts
     * arrive through Realtime, and the Realtime token is re-sent every few
     * minutes because, without the Supabase Auth plugin, Realtime never asks
     * for a fresh Firebase ID token on its own.
     */
    fun start(profileId: String) {
        if (sessionJob?.isActive == true && sessionProfileId == profileId) return
        sessionJob?.cancel()
        sessionProfileId = profileId
        sessionJob = viewModelScope.launch {
            launch { load() }
            launch {
                repository.inserts(profileId)
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) false
                        else { delay(minOf(30_000L, 2_000L shl attempt.toInt().coerceAtMost(4))); true }
                    }
                    .collect { alert -> _notifications.update { list -> listOf(alert) + list.filterNot { it.id == alert.id } } }
            }
            launch {
                while (true) {
                    delay(5 * 60_000L)
                    runCatching { SupabaseClientProvider.client.realtime.setAuth() }
                }
            }
        }
    }

    suspend fun load() {
        runCatching { repository.getNotifications() }
            .onSuccess { _notifications.value = it }
            .onFailure { if (it is CancellationException) throw it else _message.value = errorText(it) }
    }

    fun refresh() { viewModelScope.launch { load() } }

    fun markRead(ids: List<String>) {
        val unread = ids.filter { id -> _notifications.value.any { it.id == id && it.readAt == null } }
        if (unread.isEmpty()) return
        markLocal { it.id in unread }
        viewModelScope.launch { runCatching { repository.markRead(unread) } }
    }

    fun markAllRead() {
        if (_notifications.value.none { it.readAt == null }) return
        markLocal { true }
        viewModelScope.launch { runCatching { repository.markRead(null) } }
    }

    /** Mirrors mark_conversation_read, which already marked these alerts read on the server. */
    fun markConversationReadLocally(conversationId: String) = markLocal { it.conversationId == conversationId }

    fun loadPreferences() {
        viewModelScope.launch {
            runCatching { repository.getPreferences() }
                .onSuccess { _preferences.value = it }
                .onFailure { _message.value = errorText(it) }
        }
    }

    fun savePreferences(preferences: NotificationPreferences, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.savePreferences(preferences) }
                .onSuccess { _preferences.value = it; onSaved() }
                .onFailure { _message.value = errorText(it) }
        }
    }

    fun clearMessage() { _message.value = null }

    fun clearSession() {
        sessionJob?.cancel()
        sessionJob = null
        sessionProfileId = null
        _notifications.value = emptyList()
        _preferences.value = null
        _message.value = null
    }

    private fun markLocal(predicate: (AppNotification) -> Boolean) {
        val now = Instant.now().toString()
        _notifications.update { list -> list.map { if (it.readAt == null && predicate(it)) it.copy(readAt = now) else it } }
    }

    private fun errorText(error: Throwable) = error.message?.takeIf { it.isNotBlank() }
        ?: "Could not load alerts. Check your connection and try again."
}

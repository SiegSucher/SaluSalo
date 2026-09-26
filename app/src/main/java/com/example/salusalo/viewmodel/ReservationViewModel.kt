package com.example.salusalo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.MessageRepository
import com.example.salusalo.data.ReservationRepository
import com.example.salusalo.data.shortErrorText
import com.example.salusalo.model.Reservation
import com.example.salusalo.model.ReservationDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The outcome of Confirm reservation, shown only on the review page. */
sealed interface ReserveResult {
    /**
     * [noteSent] is null when there was no note, true when it was sent in the
     * reservation's chat and false when sending failed (the reservation stands).
     */
    data class Reserved(val reservation: Reservation, val noteSent: Boolean?) : ReserveResult
    data class Failed(val message: String) : ReserveResult
}

class ReservationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReservationRepository()
    private val messageRepository = MessageRepository()
    private val _myReservations = MutableStateFlow<List<ReservationDisplay>>(emptyList())
    val myReservations = _myReservations.asStateFlow()
    private val _donorReservations = MutableStateFlow<List<ReservationDisplay>>(emptyList())
    val donorReservations = _donorReservations.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    /** True after both lists have loaded at least once for this session. */
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    /** True when the last load of either list failed. */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()
    /** Result of a cancel or claim, for the Activity "Reservation" dialog. */
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _reserveResult = MutableStateFlow<ReserveResult?>(null)
    val reserveResult = _reserveResult.asStateFlow()
    /** True from the moment a reserve, cancel or claim is accepted until it has finished. */
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    // Requests run one at a time. A write asked for while a load runs waits for
    // it. A load asked for while a request runs or waits is queued and runs
    // right after that request, so a refresh is never dropped.
    private val requestMutex = Mutex()
    private var refreshPending = false

    fun refresh() {
        refreshPending = true
        if (!requestMutex.isLocked) launchRequest {}
    }

    /**
     * Reserves [quantity] of a listing, then sends [note] (if not blank) in the
     * reservation's chat. Returns false when ignored because another write has
     * not finished yet.
     */
    fun reserve(foodPostId: String, quantity: Int, note: String): Boolean = launchWrite {
        _reserveResult.value = null
        _reserveResult.value = try {
            val reservation = repository.createReservation(foodPostId, quantity)
            refreshPending = true
            val text = note.trim()
            val noteSent = if (text.isEmpty()) null else try {
                val conversation = messageRepository.openReservationConversation(reservation.id)
                messageRepository.sendMessage(conversation.id, text)
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                false
            }
            ReserveResult.Reserved(reservation, noteSent)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ReserveResult.Failed(
                shortErrorText(error, "Your reservation could not be made. Check your internet connection and try again.")
            )
        }
    }

    /** Returns false when ignored because another write has not finished yet. */
    fun cancel(reservationId: String): Boolean = launchWrite {
        _message.value = try {
            repository.cancelReservation(reservationId)
            refreshPending = true
            "Reservation cancelled. The quantity is available again."
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            refreshPending = true
            shortErrorText(error, "Your reservation could not be cancelled. Check your internet connection and try again.")
        }
    }

    /** Returns false when ignored because another write has not finished yet. */
    fun claim(reservationId: String): Boolean = launchWrite {
        _message.value = try {
            repository.claimReservation(reservationId)
            refreshPending = true
            "Reservation marked as claimed."
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            refreshPending = true
            shortErrorText(error, "The reservation could not be marked as claimed. Check your internet connection and try again.")
        }
    }

    fun clearSession() {
        refreshPending = false
        _myReservations.value = emptyList()
        _donorReservations.value = emptyList()
        _loaded.value = false
        _loadFailed.value = false
        _message.value = null
        _reserveResult.value = null
    }

    fun clearMessage() { _message.value = null }
    fun clearReserveResult() { _reserveResult.value = null }

    // A second write asked for before the first has finished (for example a
    // double tap) is ignored, so the same write is never sent twice. The first
    // write always ends with its own result.
    private fun launchWrite(block: suspend () -> Unit): Boolean {
        if (_saving.value) return false
        _saving.value = true
        launchRequest {
            try { block() } finally { _saving.value = false }
        }
        return true
    }

    private fun launchRequest(block: suspend () -> Unit) {
        viewModelScope.launch {
            requestMutex.withLock {
                _loading.value = true
                try {
                    block()
                    while (refreshPending) {
                        refreshPending = false
                        refreshLists()
                    }
                } finally {
                    _loading.value = false
                }
            }
        }
    }

    // Loads each list on its own, so a failure in one cannot stop the other from
    // loading. A failure is shown through [loadFailed].
    private suspend fun refreshLists() {
        val mine = runCatching { repository.getMyReservations() }
            .onSuccess { _myReservations.value = it }
        val donor = runCatching { repository.getReservationsForMyFoodPosts() }
            .onSuccess { _donorReservations.value = it }
        listOfNotNull(mine.exceptionOrNull(), donor.exceptionOrNull())
            .firstOrNull { it is CancellationException }?.let { throw it }
        val failed = mine.isFailure || donor.isFailure
        _loadFailed.value = failed
        if (!failed) _loaded.value = true
    }

    companion object {
        const val LOAD_FAILED = "Your reservations could not be loaded. Check your internet connection and try again."
    }
}

package com.example.salusalo.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.MessageRepository
import com.example.salusalo.data.ReservationRepository
import com.example.salusalo.data.ReviewRepository
import com.example.salusalo.data.shortErrorText
import com.example.salusalo.model.MyReview
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

/** The outcome of a cancel, claim or release, for the Activity "Reservation" dialog. */
sealed interface ReservationAction {
    data object Cancelled : ReservationAction
    data object Claimed : ReservationAction
    data object Released : ReservationAction
    data class Failed(val message: String) : ReservationAction
}

fun ReservationAction.text(): String = when (this) {
    ReservationAction.Cancelled -> "Reservation cancelled. The quantity is available again."
    // The button says "Mark as picked up", so its result does too.
    ReservationAction.Claimed -> "Marked as picked up. You'll find it under Completed."
    ReservationAction.Released -> "Reservation released. The quantity is available again, and the recipient was told."
    is ReservationAction.Failed -> message
}

/** The outcome of sending a review, shown only on the Rate page. */
sealed interface ReviewResult {
    /** [review].created tells whether it was new or changed. */
    data class Saved(val review: MyReview) : ReviewResult
    data class Failed(val message: String) : ReviewResult
}

class ReservationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReservationRepository()
    private val messageRepository = MessageRepository()
    private val reviewRepository = ReviewRepository()
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
    /** Result of a cancel, claim or release, for the Activity "Reservation" dialog. */
    private val _actionResult = MutableStateFlow<ReservationAction?>(null)
    val actionResult = _actionResult.asStateFlow()
    private val _reserveResult = MutableStateFlow<ReserveResult?>(null)
    val reserveResult = _reserveResult.asStateFlow()
    /** This account's own reviews, by reservation ID, for "Change your review". */
    private val _myReviews = MutableStateFlow<Map<String, MyReview>>(emptyMap())
    val myReviews = _myReviews.asStateFlow()
    private val _reviewResult = MutableStateFlow<ReviewResult?>(null)
    val reviewResult = _reviewResult.asStateFlow()
    /** True from the moment a reserve, cancel, claim or review is accepted until it has finished. */
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    // Requests run one at a time. A write asked for while a load runs waits for
    // it. A load asked for while a request runs or waits is queued and runs
    // right after that request, so a refresh is never dropped.
    private val requestMutex = Mutex()
    private var refreshPending = false
    // When the lists were last asked for (SystemClock.elapsedRealtime()).
    private var refreshRequestedAt = 0L

    fun refresh() {
        refreshRequestedAt = SystemClock.elapsedRealtime()
        refreshPending = true
        if (!requestMutex.isLocked) launchRequest {}
    }

    /** For Activity tab taps: reloads unless the lists were asked for in the last 10 seconds. */
    fun refreshIfStale() {
        if (SystemClock.elapsedRealtime() - refreshRequestedAt >= 10_000L) refresh()
    }

    /**
     * Reserves [quantity] of a listing at [unitPrice], the price shown (the
     * server refuses if it has changed), then sends [note] (if not blank) in
     * the reservation's chat. Returns false when ignored because another write
     * has not finished yet.
     */
    fun reserve(foodPostId: String, quantity: Int, unitPrice: Double, note: String): Boolean = launchWrite {
        _reserveResult.value = null
        _reserveResult.value = try {
            val reservation = repository.createReservation(foodPostId, quantity, unitPrice)
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
        _actionResult.value = try {
            repository.cancelReservation(reservationId)
            refreshPending = true
            ReservationAction.Cancelled
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            refreshPending = true
            ReservationAction.Failed(
                shortErrorText(error, "Your reservation could not be cancelled. Check your internet connection and try again.")
            )
        }
    }

    /** Returns false when ignored because another write has not finished yet. */
    fun claim(reservationId: String): Boolean = launchWrite {
        _actionResult.value = try {
            repository.claimReservation(reservationId)
            refreshPending = true
            ReservationAction.Claimed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            refreshPending = true
            ReservationAction.Failed(
                shortErrorText(error, "The reservation could not be marked as claimed. Check your internet connection and try again.")
            )
        }
    }

    /** The donor releases an open reservation. Returns false when ignored because another write has not finished yet. */
    fun release(reservationId: String): Boolean = launchWrite {
        _actionResult.value = try {
            repository.releaseReservation(reservationId)
            refreshPending = true
            ReservationAction.Released
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            refreshPending = true
            ReservationAction.Failed(
                shortErrorText(error, "The reservation could not be released. Check your internet connection and try again.")
            )
        }
    }

    /**
     * Rates the donor of a claimed reservation, or changes the review. Reports
     * only to the Rate page ([reviewResult]). Returns false when ignored because
     * another write has not finished yet.
     */
    fun submitReview(reservationId: String, rating: Int, comment: String): Boolean = launchWrite {
        _reviewResult.value = null
        _reviewResult.value = try {
            val review = reviewRepository.submitReview(reservationId, rating, comment)
            _myReviews.value = _myReviews.value + (review.reservationId to review)
            ReviewResult.Saved(review)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ReviewResult.Failed(
                shortErrorText(error, "Your review could not be sent. Check your internet connection and try again.")
            )
        }
    }

    fun clearSession() {
        refreshPending = false
        refreshRequestedAt = 0L
        _myReservations.value = emptyList()
        _donorReservations.value = emptyList()
        _loaded.value = false
        _loadFailed.value = false
        _actionResult.value = null
        _reserveResult.value = null
        _myReviews.value = emptyMap()
        _reviewResult.value = null
    }

    fun clearActionResult() { _actionResult.value = null }
    fun clearReserveResult() { _reserveResult.value = null }
    fun clearReviewResult() { _reviewResult.value = null }

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
    // loading. A failure is shown through [loadFailed]. The caller's own reviews
    // load alongside; if they fail, the last ones are kept and nothing is shown.
    private suspend fun refreshLists() {
        val mine = runCatching { repository.getMyReservations() }
            .onSuccess { _myReservations.value = it }
        val donor = runCatching { repository.getReservationsForMyFoodPosts() }
            .onSuccess { _donorReservations.value = it }
        val reviews = runCatching { reviewRepository.getMyGivenReviews() }
            .onSuccess { list -> _myReviews.value = list.associateBy { it.reservationId } }
        listOfNotNull(mine.exceptionOrNull(), donor.exceptionOrNull(), reviews.exceptionOrNull())
            .firstOrNull { it is CancellationException }?.let { throw it }
        val failed = mine.isFailure || donor.isFailure
        _loadFailed.value = failed
        if (!failed) _loaded.value = true
    }

    companion object {
        const val LOAD_FAILED = "Your reservations could not be loaded. Check your internet connection and try again."
    }
}

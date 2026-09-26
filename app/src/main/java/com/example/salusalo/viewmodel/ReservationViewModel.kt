package com.example.salusalo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.ReservationRepository
import com.example.salusalo.model.ReservationDisplay
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.example.salusalo.model.Reservation

data class ClaimSubmissionState(
    val foodPostId: String? = null,
    val isSubmitting: Boolean = false,
    val savedReservation: Reservation? = null,
    val error: String? = null
)

class ReservationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReservationRepository()
    private val _myReservations = MutableStateFlow<List<ReservationDisplay>>(emptyList())
    val myReservations = _myReservations.asStateFlow()
    private val _donorReservations = MutableStateFlow<List<ReservationDisplay>>(emptyList())
    val donorReservations = _donorReservations.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var activeRequest: Job? = null
    private val _claimSubmission = MutableStateFlow(ClaimSubmissionState())
    val claimSubmission = _claimSubmission.asStateFlow()

    fun prepareClaim(foodPostId: String) {
        if (!_claimSubmission.value.isSubmitting) {
            _claimSubmission.value = ClaimSubmissionState(foodPostId = foodPostId)
        }
    }

    fun submitClaim(foodPostId: String, quantity: Int) {
        val current = _claimSubmission.value
        if (current.isSubmitting || current.savedReservation != null) return
        if (_loading.value) {
            _claimSubmission.value = ClaimSubmissionState(foodPostId = foodPostId,
                error = "Please wait for the current request to finish, then confirm again.")
            return
        }
        if (quantity <= 0) {
            _claimSubmission.value = ClaimSubmissionState(foodPostId = foodPostId,
                error = "Choose a quantity greater than zero.")
            return
        }
        // Set before launch: two quick taps cannot start two writes.
        _loading.value = true
        _claimSubmission.value = ClaimSubmissionState(foodPostId, isSubmitting = true)
        activeRequest = viewModelScope.launch {
            try {
                val saved = repository.createReservation(foodPostId, quantity)
                _claimSubmission.value = ClaimSubmissionState(foodPostId, savedReservation = saved)
                // Activity reloads on entry. Its read failure must never turn a
                // saved reservation into a failed submission that invites retry.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _claimSubmission.value = ClaimSubmissionState(foodPostId,
                    error = "We could not confirm the result. Check My Requests before trying again. " +
                            (error.message ?: "Check your connection."))
            } finally {
                _loading.value = false
            }
        }
    }

    fun refresh() = request { refreshLists() }

    fun reserve(foodPostId: String, quantity: Int) = request {
        val reservation = repository.createReservation(foodPostId, quantity)
        // unit_price is set by the database from the listing's current price, so
        // this is the amount actually owed even if the listing changed meanwhile.
        _message.value = if (reservation.unitPrice > 0) {
            "Reservation created. Pay ${formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))} at pickup."
        } else {
            "Reservation created. Please coordinate pickup with the donor."
        }
        _myReservations.value = repository.getMyReservations()
    }

    fun cancel(reservationId: String) = request {
        repository.cancelReservation(reservationId)
        _message.value = "Reservation cancelled. The quantity is available again."
        refreshLists()
    }

    fun claim(reservationId: String) = request {
        repository.claimReservation(reservationId)
        _message.value = "Reservation marked as claimed."
        refreshLists()
    }

    fun clearSession() {
        activeRequest?.cancel()
        activeRequest = null
        _loading.value = false
        _claimSubmission.value = ClaimSubmissionState()
        _myReservations.value = emptyList()
        _donorReservations.value = emptyList()
        _message.value = null
    }

    fun clearMessage() { _message.value = null }

    // Loads each list on its own, so a failure in one cannot stop the other from
    // loading. The first failure is still reported through [message].
    private suspend fun refreshLists() {
        val mine = runCatching { repository.getMyReservations() }
            .onSuccess { _myReservations.value = it }
        val donor = runCatching { repository.getReservationsForMyFoodPosts() }
            .onSuccess { _donorReservations.value = it }
        (mine.exceptionOrNull() ?: donor.exceptionOrNull())?.let { throw it }
    }

    private fun request(block: suspend () -> Unit) {
        if (_loading.value) return
        _loading.value = true
        activeRequest = viewModelScope.launch {
            _message.value = null
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _message.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not complete the reservation request. Check your connection and try again."
            } finally {
                _loading.value = false
            }
        }
    }
}


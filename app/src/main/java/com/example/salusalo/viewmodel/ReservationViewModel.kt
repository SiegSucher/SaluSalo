package com.example.salusalo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.ReservationRepository
import com.example.salusalo.model.ReservationDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun refresh() = request { refreshLists() }

    fun reserve(foodPostId: String, quantity: Int) = request {
        repository.createReservation(foodPostId, quantity)
        _message.value = "Reservation created. Please coordinate pickup with the donor."
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
        viewModelScope.launch {
            _loading.value = true
            _message.value = null
            runCatching { block() }.onFailure { error ->
                _message.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not complete the reservation request. Check your connection and try again."
            }
            _loading.value = false
        }
    }
}


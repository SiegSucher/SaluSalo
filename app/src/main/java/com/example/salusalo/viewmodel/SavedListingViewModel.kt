package com.example.salusalo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.SavedListingRepository
import com.example.salusalo.model.SavedListingRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavedListingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SavedListingRepository()
    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    /** IDs of the listings the signed-in user bookmarked, for the bookmark icons. */
    val savedIds = _savedIds.asStateFlow()
    private val _saved = MutableStateFlow<List<SavedListingRow>>(emptyList())
    val saved = _saved.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var startedProfileId: String? = null
    // Listings with a save or remove on its way, so a quick second tap is ignored.
    private val pending = mutableSetOf<String>()
    // Bookmarks changed on this phone that a running load may not include yet
    // (listing ID → saved): those still on their way, and those that finished
    // after the load began. They win over that load's older answer.
    private val localChanges = mutableMapOf<String, Boolean>()

    /** Loads the bookmarks once per signed-in profile, so the icons are right from the start. */
    fun start(profileId: String) {
        if (startedProfileId == profileId) return
        startedProfileId = profileId
        load()
    }

    fun load() {
        if (_loading.value) return
        // Changes that finished before this load began are in its answer.
        localChanges.keys.retainAll(pending)
        viewModelScope.launch {
            _loading.value = true
            try {
                val rows = repository.getSavedListings()
                _saved.value = rows.filterNot { localChanges[it.foodPostId] == false }
                _savedIds.value = rows.map { it.foodPostId }.toMutableSet().apply {
                    localChanges.forEach { (id, saved) -> if (saved) add(id) else remove(id) }
                }
                _loadFailed.value = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _loadFailed.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    /** Saves or removes the bookmark at once; if the server refuses, the icon switches back. */
    fun toggle(foodPostId: String) {
        if (!pending.add(foodPostId)) return
        val wasSaved = foodPostId in _savedIds.value
        _savedIds.update { if (wasSaved) it - foodPostId else it + foodPostId }
        localChanges[foodPostId] = !wasSaved
        viewModelScope.launch {
            try {
                if (wasSaved) {
                    repository.remove(foodPostId)
                    _saved.update { rows -> rows.filterNot { it.foodPostId == foodPostId } }
                } else {
                    repository.save(foodPostId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _savedIds.update { if (wasSaved) it + foodPostId else it - foodPostId }
                localChanges[foodPostId] = wasSaved
                _message.value = "Your bookmark could not be saved. Check your internet connection and try again."
            } finally {
                pending.remove(foodPostId)
            }
        }
    }

    /** Removes a bookmark from the Saved Listings page. */
    fun remove(foodPostId: String) {
        if (foodPostId in _savedIds.value || _saved.value.any { it.foodPostId == foodPostId }) {
            _savedIds.update { it + foodPostId }
            toggle(foodPostId)
        }
    }

    fun clearMessage() { _message.value = null }

    fun clearSession() {
        startedProfileId = null
        pending.clear()
        localChanges.clear()
        _savedIds.value = emptySet()
        _saved.value = emptyList()
        _loadFailed.value = false
        _message.value = null
    }
}

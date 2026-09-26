package com.example.salusalo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.FoodRepository
import com.example.salusalo.data.encodeListingPhoto
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import com.example.salusalo.model.FoodPostEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The outcome of an edit, for the Edit Listing dialog. */
sealed interface EditResult {
    data object Updated : EditResult
    data class Failed(val message: String) : EditResult
}

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FoodRepository()
    private val _availablePosts = MutableStateFlow<List<FoodPost>>(emptyList())
    val availablePosts = _availablePosts.asStateFlow()
    private val _myPosts = MutableStateFlow<List<FoodPost>>(emptyList())
    val myPosts = _myPosts.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _createdPost = MutableStateFlow<FoodPost?>(null)
    val createdPost = _createdPost.asStateFlow()
    private val _createError = MutableStateFlow<String?>(null)
    val createError = _createError.asStateFlow()
    private val _editResult = MutableStateFlow<EditResult?>(null)
    val editResult = _editResult.asStateFlow()

    /** True from the moment a create, edit or delete is accepted until it has finished. */
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    // Requests run one at a time. A write asked for while another request runs
    // waits for it instead of being dropped. A load asked for while a request
    // runs or waits is queued and runs right after that request (My Listings
    // first), so an older load can never overwrite a newer list.
    private val requestMutex = Mutex()
    private var myPostsLoadPending = false
    private var feedLoadPending = false

    fun loadAvailablePosts() {
        feedLoadPending = true
        if (!requestMutex.isLocked) launchRequest {}
    }

    fun loadMyPosts() {
        myPostsLoadPending = true
        if (!requestMutex.isLocked) launchRequest {}
    }

    /** Returns false when ignored because another write has not finished yet. */
    fun createPost(draft: FoodPostDraft, photoUris: List<Uri>): Boolean = launchWrite {
        _createError.value = null
        try {
            // Shrinks and re-encodes every photo as JPEG off the main thread.
            val photos = photoUris.map { uri ->
                encodeListingPhoto(getApplication(), uri) ?: throw UnreadablePhotoException()
            }
            _createdPost.value = repository.createFoodPost(draft, photos)
            // Reload My Listings, then the feed, as separate loads at the end of this
            // request, so a failed feed reload cannot stop My Listings from refreshing.
            myPostsLoadPending = true
            feedLoadPending = true
        } catch (error: UnreadablePhotoException) {
            _createError.value = "One of the photos could not be read. Please choose another photo."
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _createError.value = errorText(error)
        }
    }

    /** Returns false when ignored because another write has not finished yet. */
    fun updatePost(postId: String, edit: FoodPostEdit): Boolean = launchWrite {
        _editResult.value = try {
            repository.updateMyFoodPost(postId, edit)
            myPostsLoadPending = true
            feedLoadPending = true
            EditResult.Updated
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            EditResult.Failed(
                supabaseErrorText(
                    error,
                    "Your listing could not be updated. Check your internet connection and try again."
                )
            )
        }
    }

    fun deletePost(post: FoodPost): Boolean = launchWrite {
        runCatching {
            repository.deleteFoodPost(post)
            _myPosts.value = _myPosts.value.filterNot { it.id == post.id }
            _availablePosts.value = _availablePosts.value.filterNot { it.id == post.id }
        }.onFailure { reportError(it) }
    }

    /** Signed URLs for a listing's photos 2–4, for the details gallery; a photo that can't be signed is null. */
    suspend fun extraPhotoUrls(post: FoodPost): List<String?> = repository.signedExtraPhotoUrls(post)

    fun clearSession() {
        myPostsLoadPending = false
        feedLoadPending = false
        _availablePosts.value = emptyList()
        _myPosts.value = emptyList()
        _createdPost.value = null
        _createError.value = null
        _editResult.value = null
        _error.value = null
    }

    fun clearCreatedPost() { _createdPost.value = null }
    fun clearCreateError() { _createError.value = null }
    fun clearEditResult() { _editResult.value = null }
    fun clearError() { _error.value = null }

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
                _error.value = null
                runCatching { block() }.onFailure { reportError(it) }
                // Each queued load succeeds or fails on its own.
                while (myPostsLoadPending || feedLoadPending) {
                    if (myPostsLoadPending) {
                        myPostsLoadPending = false
                        runCatching { _myPosts.value = repository.getUserFoodPosts() }
                            .onFailure { reportError(it) }
                    } else {
                        feedLoadPending = false
                        runCatching { _availablePosts.value = repository.getAvailableFoodPosts() }
                            .onFailure { reportError(it) }
                    }
                }
                _loading.value = false
            }
        }
    }

    // Keeps the first failure of the current request for the UI.
    private fun reportError(throwable: Throwable) {
        if (_error.value != null) return
        _error.value = errorText(throwable)
    }

    private fun errorText(throwable: Throwable): String =
        throwable.message?.takeIf { it.isNotBlank() }
            ?: "Could not complete this request. Check your internet connection and try again."

    private class UnreadablePhotoException : Exception()
}

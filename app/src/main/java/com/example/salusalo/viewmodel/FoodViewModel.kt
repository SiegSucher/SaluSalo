package com.example.salusalo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.FoodRepository
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

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

    // A load requested while another request is running is queued instead of
    // dropped. Queued loads run one at a time right after that request (My
    // Listings first), so an older load can never overwrite a newer list.
    private var myPostsLoadPending = false
    private var feedLoadPending = false

    fun loadAvailablePosts() {
        feedLoadPending = true
        if (!_loading.value) launchRequest {}
    }

    fun loadMyPosts() {
        myPostsLoadPending = true
        if (!_loading.value) launchRequest {}
    }

    fun createPost(draft: FoodPostDraft, imageUri: Uri) = launchRequest {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: error("The selected image could not be read.")
        val mime = resolver.getType(imageUri).orEmpty()
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        _createdPost.value = repository.createFoodPost(draft, bytes, extension)
        // Reload My Listings, then the feed, as separate loads at the end of this
        // request, so a failed feed reload cannot stop My Listings from refreshing.
        myPostsLoadPending = true
        feedLoadPending = true
    }

    fun deletePost(post: FoodPost) = launchRequest {
        repository.deleteFoodPost(post)
        _myPosts.value = _myPosts.value.filterNot { it.id == post.id }
        _availablePosts.value = _availablePosts.value.filterNot { it.id == post.id }
    }

    fun clearSession() {
        myPostsLoadPending = false
        feedLoadPending = false
        _availablePosts.value = emptyList()
        _myPosts.value = emptyList()
        _createdPost.value = null
        _error.value = null
    }

    fun clearCreatedPost() { _createdPost.value = null }
    fun clearError() { _error.value = null }

    // Create and delete are still ignored while another request is running, so
    // the same write cannot be sent twice or overlap another request.
    private fun launchRequest(block: suspend () -> Unit) {
        if (_loading.value) return
        viewModelScope.launch {
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

    // Keeps the first failure of the current request for the UI.
    private fun reportError(throwable: Throwable) {
        if (_error.value != null) return
        _error.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: "Could not complete this request. Check your internet connection and try again."
    }
}






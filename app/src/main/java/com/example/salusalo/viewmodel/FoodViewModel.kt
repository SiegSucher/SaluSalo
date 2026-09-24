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

    fun loadAvailablePosts() = launchRequest {
        _availablePosts.value = repository.getAvailableFoodPosts()
    }

    fun loadMyPosts() = launchRequest {
        _myPosts.value = repository.getUserFoodPosts()
    }

    fun createPost(draft: FoodPostDraft, imageUri: Uri) = launchRequest {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: error("The selected image could not be read.")
        val mime = resolver.getType(imageUri).orEmpty()
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        _createdPost.value = repository.createFoodPost(draft, bytes, extension)
        // Refresh inside the current request. Calling load... here would be ignored
        // while the loading flag is true.
        _availablePosts.value = repository.getAvailableFoodPosts()
        _myPosts.value = repository.getUserFoodPosts()
    }

    fun deletePost(post: FoodPost) = launchRequest {
        repository.deleteFoodPost(post)
        _myPosts.value = _myPosts.value.filterNot { it.id == post.id }
        _availablePosts.value = _availablePosts.value.filterNot { it.id == post.id }
    }

    fun clearSession() {
        _availablePosts.value = emptyList()
        _myPosts.value = emptyList()
        _createdPost.value = null
        _error.value = null
    }

    fun clearCreatedPost() { _createdPost.value = null }
    fun clearError() { _error.value = null }

    private fun launchRequest(block: suspend () -> Unit) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { block() }.onFailure { throwable ->
                _error.value = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Could not complete this request. Check your internet connection and try again."
            }
            _loading.value = false
        }
    }
}






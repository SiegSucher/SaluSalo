package com.example.salusalo.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salusalo.data.FoodRepository
import com.example.salusalo.data.PublishUnconfirmedException
import com.example.salusalo.data.encodeListingPhoto
import com.example.salusalo.data.shortErrorText
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import com.example.salusalo.model.FoodPostEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/**
 * The outcome of Post listing for the form with [draftId]. Another form (after
 * Post another, or leaving Post) has another ID and never shows it.
 */
sealed interface PostResult {
    val draftId: String
    data class Published(override val draftId: String, val post: FoodPost) : PostResult
    data class Failed(override val draftId: String, val message: String) : PostResult
}

/**
 * The listing whose details page is open. [listed] is true when the feed listed
 * it as the page opened: if a later feed no longer does, it has been fully
 * reserved, removed or has ended since, and the page must not offer Reserve.
 */
data class OpenedListing(val post: FoodPost, val listed: Boolean)

/** The outcome of an edit, for the Edit Listing dialog. */
sealed interface EditResult {
    data object Updated : EditResult
    data class Failed(val message: String) : EditResult
}

/** The outcome of Delete, for the "Delete listing" result dialog. */
sealed interface DeleteResult {
    /** Never reserved, reported or messaged about: the listing and its photos are gone. */
    data object Deleted : DeleteResult
    /** Reserved, reported or messaged about: hidden, with [cancelledReservations] open reservations cancelled. */
    data class Removed(val cancelledReservations: Int) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

fun DeleteResult.text(): String = when (this) {
    DeleteResult.Deleted -> "Listing deleted."
    is DeleteResult.Removed -> when (cancelledReservations) {
        0 -> "Listing removed. Its past reservations and chats are kept."
        1 -> "Listing removed. 1 open reservation was cancelled, and its recipient was told. " +
            "Its past reservations and chats are kept."
        else -> "Listing removed. $cancelledReservations open reservations were cancelled, and their " +
            "recipients were told. Its past reservations and chats are kept."
    }
    is DeleteResult.Failed -> message
}

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FoodRepository()
    private val _availablePosts = MutableStateFlow<List<FoodPost>>(emptyList())
    val availablePosts = _availablePosts.asStateFlow()
    private val _myPosts = MutableStateFlow<List<FoodPost>>(emptyList())
    val myPosts = _myPosts.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    // Each list: whether it has loaded once this session (it may be empty), and
    // why its last load failed (null after a good one). A failed load keeps the
    // last good list, so a failure is never shown as "nothing here".
    private val _feedLoaded = MutableStateFlow(false)
    val feedLoaded = _feedLoaded.asStateFlow()
    private val _feedError = MutableStateFlow<String?>(null)
    val feedError = _feedError.asStateFlow()
    private val _myPostsLoaded = MutableStateFlow(false)
    val myPostsLoaded = _myPostsLoaded.asStateFlow()
    private val _myPostsError = MutableStateFlow<String?>(null)
    val myPostsError = _myPostsError.asStateFlow()
    /** The draft being posted now, or null. */
    private val _postingDraftId = MutableStateFlow<String?>(null)
    val postingDraftId = _postingDraftId.asStateFlow()
    private val _postResult = MutableStateFlow<PostResult?>(null)
    val postResult = _postResult.asStateFlow()
    /** Drafts published this session (a draft's ID is its listing's ID), so a kept form shows "published". */
    private val _publishedDraftIds = MutableStateFlow<Set<String>>(emptySet())
    val publishedDraftIds = _publishedDraftIds.asStateFlow()
    private val _editResult = MutableStateFlow<EditResult?>(null)
    val editResult = _editResult.asStateFlow()
    private val _deleteResult = MutableStateFlow<DeleteResult?>(null)
    val deleteResult = _deleteResult.asStateFlow()
    // Held here, not in the screen, so the details page stays open through
    // rotation and other configuration changes.
    private val _openedListing = MutableStateFlow<OpenedListing?>(null)
    val openedListing = _openedListing.asStateFlow()

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
    // When the feed was last asked for, and last loaded with fresh photo URLs
    // (SystemClock.elapsedRealtime()).
    private var feedRequestedAt = 0L
    private var feedLoadedAt = 0L

    fun loadAvailablePosts() {
        queueFeedLoad()
        if (!requestMutex.isLocked) launchRequest {}
    }

    /**
     * For Home opening and the app coming back: reloads the feed unless it was
     * asked for less than [FEED_REFRESH_INTERVAL_MILLIS] ago, so switching tabs
     * or recomposing never floods Supabase.
     */
    fun refreshFeedIfStale() {
        if (SystemClock.elapsedRealtime() - feedRequestedAt >= FEED_REFRESH_INTERVAL_MILLIS) loadAvailablePosts()
    }

    /**
     * A feed photo failed to load. Its 1-hour signed URL may have expired, so
     * the feed reloads for fresh URLs, but only when it is old enough for that.
     * On fresh URLs a failing photo is really unavailable and causes no reload,
     * so a photo is retried once, never in a loop.
     */
    fun refreshExpiredPhotoUrls() {
        if (SystemClock.elapsedRealtime() - feedLoadedAt >= PHOTO_URLS_OLD_MILLIS) refreshFeedIfStale()
    }

    private fun queueFeedLoad() {
        feedRequestedAt = SystemClock.elapsedRealtime()
        feedLoadPending = true
    }

    fun loadMyPosts() {
        myPostsLoadPending = true
        if (!requestMutex.isLocked) launchRequest {}
    }

    /**
     * Posts the form's listing under [draftId], the listing's ID. The form keeps
     * its ID, so tapping Post again after a failure can never post it twice.
     * The result stays here ([postResult]) until shown, even if Post is left.
     * Returns false when ignored because another write has not finished yet.
     */
    fun createPost(draftId: String, draft: FoodPostDraft, photoUris: List<Uri>): Boolean = launchWrite {
        _postingDraftId.value = draftId
        _postResult.value = try {
            // Shrinks and re-encodes every photo as JPEG off the main thread.
            val photos = photoUris.map { uri ->
                encodeListingPhoto(getApplication(), uri) ?: throw UnreadablePhotoException()
            }
            val post = repository.createFoodPost(draftId, draft, photos)
            // Reload My Listings, then the feed, as separate loads at the end of this
            // request, so a failed feed reload cannot stop My Listings from refreshing.
            myPostsLoadPending = true
            queueFeedLoad()
            _publishedDraftIds.value = _publishedDraftIds.value + draftId
            PostResult.Published(draftId, post)
        } catch (error: UnreadablePhotoException) {
            PostResult.Failed(draftId, "One of the photos could not be read. Please choose another photo.")
        } catch (error: PublishUnconfirmedException) {
            PostResult.Failed(draftId, PUBLISH_UNCONFIRMED)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            PostResult.Failed(
                draftId,
                if (error is IllegalArgumentException && error !is SerializationException) {
                    // The repository's own checks, for example "Choose a food image before posting."
                    error.message ?: PUBLISH_FAILED
                } else {
                    shortErrorText(error, PUBLISH_FAILED)
                }
            )
        } finally {
            _postingDraftId.value = null
        }
    }

    /** Returns false when ignored because another write has not finished yet. */
    fun updatePost(postId: String, edit: FoodPostEdit): Boolean = launchWrite {
        _editResult.value = try {
            repository.updateMyFoodPost(postId, edit)
            myPostsLoadPending = true
            queueFeedLoad()
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

    /**
     * Deletes a never-reserved listing, or removes (hides) one with reservations.
     * Returns false when ignored because another write has not finished yet.
     */
    fun deletePost(post: FoodPost): Boolean = launchWrite {
        _deleteResult.value = try {
            val result = repository.removeMyFoodPost(post)
            _myPosts.value = _myPosts.value.filterNot { it.id == post.id }
            _availablePosts.value = _availablePosts.value.filterNot { it.id == post.id }
            myPostsLoadPending = true
            queueFeedLoad()
            if (result.deleted) DeleteResult.Deleted else DeleteResult.Removed(result.cancelledReservations)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            DeleteResult.Failed(
                shortErrorText(error, "Your listing could not be deleted. Check your internet connection and try again.")
            )
        }
    }

    fun openListing(post: FoodPost) {
        _openedListing.value = OpenedListing(post, listed = _availablePosts.value.any { it.id == post.id })
    }

    fun closeListing() { _openedListing.value = null }

    /** Signed URLs for a listing's photos 2–4, for the details gallery; a photo that can't be signed is null. */
    suspend fun extraPhotoUrls(post: FoodPost): List<String?> = repository.signedExtraPhotoUrls(post)

    fun clearSession() {
        myPostsLoadPending = false
        feedLoadPending = false
        feedRequestedAt = 0L
        feedLoadedAt = 0L
        _availablePosts.value = emptyList()
        _myPosts.value = emptyList()
        _feedLoaded.value = false
        _feedError.value = null
        _myPostsLoaded.value = false
        _myPostsError.value = null
        _postResult.value = null
        _publishedDraftIds.value = emptySet()
        _editResult.value = null
        _deleteResult.value = null
        _openedListing.value = null
    }

    fun clearPostResult() { _postResult.value = null }
    fun clearEditResult() { _editResult.value = null }
    fun clearDeleteResult() { _deleteResult.value = null }

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
                // Writes report their own results.
                runCatching { block() }
                // Each queued load succeeds or fails on its own.
                while (myPostsLoadPending || feedLoadPending) {
                    if (myPostsLoadPending) {
                        myPostsLoadPending = false
                        runCatching { repository.getUserFoodPosts() }
                            .onSuccess {
                                _myPosts.value = it
                                _myPostsLoaded.value = true
                                _myPostsError.value = null
                            }
                            .onFailure { _myPostsError.value = errorText(it) }
                    } else {
                        feedLoadPending = false
                        runCatching { repository.getAvailableFoodPosts() }
                            .onSuccess {
                                _availablePosts.value = it
                                feedLoadedAt = SystemClock.elapsedRealtime()
                                _feedLoaded.value = true
                                _feedError.value = null
                            }
                            .onFailure { _feedError.value = errorText(it) }
                    }
                }
                _loading.value = false
            }
        }
    }

    // Only the short server text: the full exception message can include the URL and headers.
    private fun errorText(throwable: Throwable): String =
        shortErrorText(throwable, "Could not complete this request. Check your internet connection and try again.")

    private class UnreadablePhotoException : Exception()

    private companion object {
        const val PUBLISH_FAILED =
            "Your listing could not be published. Check your internet connection and try again."
        const val PUBLISH_UNCONFIRMED =
            "We couldn't confirm whether your listing was published. Check your internet connection, " +
                "then tap Post listing again: it will not be posted twice."
        // Automatic feed reloads (Home opened, the app back) at most every 30 seconds.
        const val FEED_REFRESH_INTERVAL_MILLIS = 30_000L
        // Signed photo URLs last 1 hour; after 50 minutes a failing photo may be an expired URL.
        const val PHOTO_URLS_OLD_MILLIS = 50 * 60_000L
    }
}

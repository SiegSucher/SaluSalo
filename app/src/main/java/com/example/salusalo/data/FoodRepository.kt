package com.example.salusalo.data

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import com.example.salusalo.model.FoodPostEdit
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.ListingDonorProfile
import com.example.salusalo.model.NewFoodPost
import com.example.salusalo.model.RemoveFoodPostResult
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours

/** A listing has a cover photo plus at most this many more. */
const val MAX_LISTING_PHOTOS = 4

class FoodRepository(
    private val userRepository: UserRepository = UserRepository(),
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private val supabase = SupabaseClientProvider.client
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val bucket = supabase.storage.from("food-images")

    suspend fun getAvailableFoodPosts(): List<FoodPost> {
        val now = java.time.Instant.now().toString()
        val posts = supabase.from("food_posts").select {
            filter {
                filter("status", FilterOperator.EQ, FoodPostStatus.AVAILABLE.name)
                filter("expiration_date", FilterOperator.GT, now)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<FoodPost>()
        return withDonorProfiles(posts).map { post -> post.withSignedImageUrl(bucket) }
    }

    suspend fun getUserFoodPosts(): List<FoodPost> {
        val profile = userRepository.getCurrentProfile()
        val posts = supabase.from("food_posts").select {
            filter {
                filter("donor_id", FilterOperator.EQ, profile.id)
                // Removed listings stay in the database for their reservations only.
                filter("status", FilterOperator.NEQ, FoodPostStatus.REMOVED.name)
            }
        }.decodeList<FoodPost>()
        return posts.map { post -> post.withSignedImageUrl(bucket) }
    }

    /**
     * Adds each donor's name, "Email verified" and rating from
     * get_listing_donor_profiles(), in one call for the whole list. It returns
     * names only, for listings the caller may see. If the lookup fails the
     * listings still load, without names or ratings.
     */
    suspend fun withDonorProfiles(posts: List<FoodPost>): List<FoodPost> {
        if (posts.isEmpty()) return posts
        val profiles = try {
            supabase.postgrest.rpc("get_listing_donor_profiles", buildJsonObject {
                putJsonArray("p_food_post_ids") { posts.forEach { add(it.id) } }
            }).decodeList<ListingDonorProfile>().associateBy { it.foodPostId }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return posts
        }
        return posts.map { post ->
            val donor = profiles[post.id] ?: return@map post
            post.copy(
                donorName = donor.donorName,
                donorRatingAverage = donor.ratingAverage,
                donorReviewCount = donor.ratingCount,
                donorEmailVerified = donor.emailVerified
            )
        }
    }

    /**
     * Uploads the photos (the first is the cover), then inserts the listing.
     * Every photo is already a shrunk JPEG. If an upload or the insert fails,
     * the photos uploaded so far are deleted, best effort.
     */
    suspend fun createFoodPost(draft: FoodPostDraft, photos: List<ByteArray>): FoodPost {
        require(photos.isNotEmpty() && photos.all { it.isNotEmpty() }) { "Choose a food image before posting." }
        require(photos.size <= MAX_LISTING_PHOTOS) { "Choose at most $MAX_LISTING_PHOTOS photos." }
        val pin = draft.pickupPin.rounded()
        require(pin.isValid) { "Place the pickup pin on the map." }
        val profile = userRepository.getCurrentProfile()
        val firebaseUid = firebaseAuth.currentUser?.uid
            ?: throw ProfileException.NotSignedIn
        val id = UUID.randomUUID().toString()
        val paths = photos.indices.map { index ->
            storageRepository.listingPhotoPath(firebaseUid = firebaseUid, postId = id, number = index + 1)
        }

        val uploaded = mutableListOf<String>()
        try {
            photos.forEachIndexed { index, bytes ->
                bucket.upload(paths[index], bytes) {
                    upsert = false
                }
                uploaded += paths[index]
            }
        } catch (error: Exception) {
            if (uploaded.isNotEmpty()) runCatching { bucket.delete(uploaded) }
            throw error
        }

        val newPost = NewFoodPost(
            id = id,
            donorId = profile.id,
            name = draft.name.trim(),
            description = draft.description.trim().ifBlank { null },
            category = draft.category,
            quantity = draft.quantity,
            unit = draft.unit,
            imageUrl = paths.first(),
            pickupLocation = draft.pickupLocation.trim(),
            expirationDate = draft.expirationDate,
            price = draft.price,
            pickupStart = draft.pickupStart,
            pickupEnd = draft.pickupEnd,
            handover = draft.handover?.code,
            allergens = draft.allergens.trim().ifBlank { null },
            storageNotes = draft.storageNotes.trim().ifBlank { null },
            extraImagePaths = paths.drop(1),
            pickupLatitude = pin.latitude,
            pickupLongitude = pin.longitude
        )

        // Remove the uploaded photos only when the insert itself fails. Once the
        // row exists it references them, so a later decode or signing problem
        // must not delete them.
        val inserted = try {
            supabase.from("food_posts").insert(newPost) { select() }
        } catch (error: Exception) {
            runCatching { bucket.delete(paths) }
            throw error
        }
        return inserted.decodeSingle<FoodPost>().withSignedImageUrl(bucket)
    }

    /**
     * Edits the caller's own listing, including its pickup pin, through
     * update_my_food_post_with_pin(). The server finds the caller from the token
     * and refuses anyone else's listing. Every parameter is sent, including
     * nulls; a null pin keeps the stored one.
     */
    suspend fun updateMyFoodPost(postId: String, edit: FoodPostEdit): FoodPost {
        val pin = edit.pickupPin?.rounded()
        require(pin == null || pin.isValid) { "Place the pickup pin on the map." }
        return supabase.postgrest.rpc("update_my_food_post_with_pin", buildJsonObject {
            put("p_food_post_id", postId)
            put("p_name", edit.name.trim())
            put("p_description", edit.description.trim().ifBlank { null })
            put("p_category", edit.category)
            put("p_price", edit.price)
            put("p_pickup_location", edit.pickupLocation.trim())
            put("p_expiration_date", edit.expirationDate)
            put("p_pickup_start", edit.pickupStart)
            put("p_pickup_end", edit.pickupEnd)
            put("p_handover", edit.handover?.code)
            put("p_allergens", edit.allergens.trim().ifBlank { null })
            put("p_storage_notes", edit.storageNotes.trim().ifBlank { null })
            put("p_pickup_latitude", pin?.latitude)
            put("p_pickup_longitude", pin?.longitude)
        }).decodeAs<FoodPost>().withSignedImageUrl(bucket)
    }

    /**
     * 1-hour signed URLs for photos 2–4 (the cover is already signed). Each
     * photo is signed on its own, so one that fails is null and the others
     * still show.
     */
    suspend fun signedExtraPhotoUrls(post: FoodPost): List<String?> =
        post.extraImagePaths.map { path -> bucket.signedUrlOrNull(path) }

    /**
     * Deletes or removes the caller's own listing through remove_my_food_post().
     * The server finds the caller from the token. A listing nobody ever reserved
     * is deleted, and then its photos too (best effort: a failure only leaves
     * photos behind in Storage). A listing with reservations is only hidden, so
     * its photos stay for those reservations.
     */
    suspend fun removeMyFoodPost(post: FoodPost): RemoveFoodPostResult {
        val result = supabase.postgrest.rpc("remove_my_food_post", buildJsonObject {
            put("p_food_post_id", post.id)
        }).decodeList<RemoveFoodPostResult>().single()
        if (result.deleted) {
            val paths = listOfNotNull(post.imageUrl?.takeIf { it.isNotBlank() }) + post.extraImagePaths
            if (paths.isNotEmpty()) runCatching { bucket.delete(paths) }
        }
        return result
    }
}

/**
 * Adds a 1-hour signed URL for the post's private food-images path. If signing
 * fails (for example the object is missing or the request times out), only this
 * post is left without an image; the list it belongs to still loads.
 */
internal suspend fun FoodPost.withSignedImageUrl(bucket: BucketApi): FoodPost =
    bucket.signedUrlOrNull(imageUrl)?.let { copy(displayImageUrl = it) } ?: this

/** A 1-hour signed URL for a private object path, or null if there is none or signing fails. */
internal suspend fun BucketApi.signedUrlOrNull(path: String?): String? {
    val objectPath = path?.takeIf { it.isNotBlank() } ?: return null
    return try {
        createSignedUrl(objectPath, 1.hours)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }
}

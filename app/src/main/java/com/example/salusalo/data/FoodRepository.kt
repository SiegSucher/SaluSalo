package com.example.salusalo.data

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.NewFoodPost
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours

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
        }.decodeList<FoodPost>()
        return posts.map { post -> post.withSignedImageUrl(bucket) }
    }

    suspend fun getUserFoodPosts(): List<FoodPost> {
        val profile = userRepository.getCurrentProfile()
        val posts = supabase.from("food_posts").select {
            filter { filter("donor_id", FilterOperator.EQ, profile.id) }
        }.decodeList<FoodPost>()
        return posts.map { post -> post.withSignedImageUrl(bucket) }
    }

    suspend fun createFoodPost(
        draft: FoodPostDraft,
        imageBytes: ByteArray,
        fileExtension: String
    ): FoodPost {
        require(imageBytes.isNotEmpty()) { "Choose a food image before posting." }
        val profile = userRepository.getCurrentProfile()
        val firebaseUid = firebaseAuth.currentUser?.uid
            ?: throw ProfileException.NotSignedIn
        val id = UUID.randomUUID().toString()
        val imagePath = storageRepository.foodImagePath(
            firebaseUid = firebaseUid,
            postId = id,
            fileName = "image.${fileExtension.ifBlank { "jpg" }}"
        )

        bucket.upload(imagePath, imageBytes) {
            upsert = false
        }

        val newPost = NewFoodPost(
            id = id,
            donorId = profile.id,
            name = draft.name.trim(),
            description = draft.description.trim().ifBlank { null },
            category = draft.category,
            quantity = draft.quantity,
            unit = draft.unit,
            imageUrl = imagePath,
            pickupLocation = draft.pickupLocation.trim(),
            expirationDate = draft.expirationDate
        )

        // Remove the uploaded image only when the insert itself fails. Once the
        // row exists it references this image, so a later decode or signing
        // problem must not delete it.
        val inserted = try {
            supabase.from("food_posts").insert(newPost) { select() }
        } catch (error: Exception) {
            runCatching { bucket.delete(imagePath) }
            throw error
        }
        return inserted.decodeSingle<FoodPost>().withSignedImageUrl(bucket)
    }

    suspend fun updateFoodPost(post: FoodPost): FoodPost {
        val profile = userRepository.getCurrentProfile()
        require(post.donorId == profile.id) { "You can edit only your own listing." }
        return supabase.from("food_posts").update(post) {
            select()
            filter { filter("id", FilterOperator.EQ, post.id) }
        }.decodeSingle<FoodPost>().withSignedImageUrl(bucket)
    }

    suspend fun deleteFoodPost(post: FoodPost) {
        val profile = userRepository.getCurrentProfile()
        require(post.donorId == profile.id) { "You can delete only your own listing." }
        supabase.from("food_posts").delete {
            filter { filter("id", FilterOperator.EQ, post.id) }
        }
        post.imageUrl?.let { path -> runCatching { bucket.delete(path) } }
    }
}

/**
 * Adds a 1-hour signed URL for the post's private food-images path. If signing
 * fails (for example the object is missing or the request times out), only this
 * post is left without an image; the list it belongs to still loads.
 */
internal suspend fun FoodPost.withSignedImageUrl(bucket: BucketApi): FoodPost {
    val path = imageUrl?.takeIf { it.isNotBlank() } ?: return this
    return try {
        copy(displayImageUrl = bucket.createSignedUrl(path, 1.hours))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        this
    }
}
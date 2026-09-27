package com.example.salusalo.data

import com.example.salusalo.model.DONOR_REVIEWS_LIMIT
import com.example.salusalo.model.ListingDonorProfile
import com.example.salusalo.model.MY_REVIEWS_LIMIT
import com.example.salusalo.model.MyReview
import com.example.salusalo.model.RatingSummary
import com.example.salusalo.model.ReviewItem
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Ratings and reviews. Clients can't read or write the reviews table; every
 * call is a SECURITY DEFINER function that takes the caller from the token and
 * returns names only.
 */
class ReviewRepository {
    private val postgrest = SupabaseClientProvider.client.postgrest

    /**
     * Rates the donor of a claimed reservation the caller received (1–5 stars,
     * optional comment). The server finds the person rated from the listing;
     * sending again changes the review.
     */
    suspend fun submitReview(reservationId: String, rating: Int, comment: String): MyReview =
        postgrest.rpc("submit_review", buildJsonObject {
            put("p_reservation_id", reservationId)
            put("p_rating", rating)
            put("p_comment", comment.trim().ifBlank { null })
        }).decodeList<MyReview>().single()

    /** The caller's own reviews, one per reservation. */
    suspend fun getMyGivenReviews(): List<MyReview> =
        postgrest.rpc("get_my_given_reviews").decodeList()

    /** The donor of a listing the caller may see: name, "Email verified" and rating; null if hidden. */
    suspend fun getListingDonorProfile(foodPostId: String): ListingDonorProfile? =
        postgrest.rpc("get_listing_donor_profiles", buildJsonObject {
            putJsonArray("p_food_post_ids") { add(foodPostId) }
        }).decodeList<ListingDonorProfile>().firstOrNull()

    /** The latest reviews about the donor of a listing the caller may see. */
    suspend fun getListingDonorReviews(foodPostId: String, limit: Int = DONOR_REVIEWS_LIMIT): List<ReviewItem> =
        postgrest.rpc("get_listing_donor_reviews", buildJsonObject {
            put("p_food_post_id", foodPostId)
            put("p_limit", limit)
        }).decodeList()

    /** The caller's own rating (average and count). */
    suspend fun getMyRatingSummary(): RatingSummary =
        postgrest.rpc("get_my_rating_summary").decodeList<RatingSummary>().first()

    /** The latest reviews about the caller. */
    suspend fun getMyReceivedReviews(limit: Int = MY_REVIEWS_LIMIT): List<ReviewItem> =
        postgrest.rpc("get_my_received_reviews", buildJsonObject {
            put("p_limit", limit)
        }).decodeList()
}

package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A saved_listings row with its listing embedded. [foodPost] is null when the
 * caller can no longer see the listing (food_posts RLS applies to the embed).
 */
@Serializable
data class SavedListingRow(
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("food_posts") val foodPost: FoodPost? = null,
    /** From get_my_saved_listing_names(), for a listing the caller can no longer see. */
    @Transient val fallbackName: String? = null,
    /** Signed cover URL for the same case. */
    @Transient val fallbackImageUrl: String? = null
)

@Serializable
data class NewSavedListing(
    @SerialName("user_id") val userId: String,
    @SerialName("food_post_id") val foodPostId: String
)

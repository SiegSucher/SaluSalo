package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class FoodPost(
    val id: String,
    @SerialName("donor_id") val donorId: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val quantity: Int,
    val unit: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("pickup_location") val pickupLocation: String,
    @SerialName("expiration_date") val expirationDate: String,
    val status: FoodPostStatus = FoodPostStatus.AVAILABLE,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @Transient val displayImageUrl: String? = null
)

@Serializable
enum class FoodPostStatus { AVAILABLE, RESERVED, CLAIMED, EXPIRED }

@Serializable
data class NewFoodPost(
    val id: String,
    @SerialName("donor_id") val donorId: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val quantity: Int,
    val unit: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("pickup_location") val pickupLocation: String,
    @SerialName("expiration_date") val expirationDate: String,
    val status: FoodPostStatus = FoodPostStatus.AVAILABLE
)

data class FoodPostDraft(
    val name: String,
    val description: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val pickupLocation: String,
    val expirationDate: String
)
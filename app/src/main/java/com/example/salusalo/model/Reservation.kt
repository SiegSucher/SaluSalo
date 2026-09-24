package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reservation(
    val id: String,
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("recipient_id") val recipientId: String,
    val quantity: Int,
    val status: ReservationStatus,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
enum class ReservationStatus { RESERVED, CLAIMED, CANCELLED }

data class ReservationDisplay(
    val reservation: Reservation,
    val foodPost: FoodPost,
    val recipientName: String? = null
)

@Serializable
internal data class DonorReservationRow(
    val id: String,
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("recipient_id") val recipientId: String,
    val quantity: Int,
    val status: ReservationStatus,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("recipient_name") val recipientName: String
) {
    fun toReservation() = Reservation(id, foodPostId, recipientId, quantity, status, createdAt, updatedAt)
}


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
    @SerialName("updated_at") val updatedAt: String? = null,
    /** The listing's price per unit when this reservation was made; set by the database. */
    @SerialName("unit_price") val unitPrice: Double = 0.0,
    /**
     * LISTING_REMOVED when cancelled because the donor removed the listing,
     * DONOR_RELEASED when the donor released it, PICKUP_EXPIRED when not picked
     * up by its deadline; null otherwise.
     */
    @SerialName("cancel_reason") val cancelReason: String? = null,
    /** When it expires if not picked up; null for reservations made without a hold (never expire). */
    @SerialName("pickup_deadline") val pickupDeadline: String? = null
) {
    val cancelledByListingRemoval: Boolean get() = cancelReason == CANCEL_REASON_LISTING_REMOVED
    val releasedByDonor: Boolean get() = cancelReason == CANCEL_REASON_DONOR_RELEASED
    val expiredUnpicked: Boolean get() = cancelReason == CANCEL_REASON_PICKUP_EXPIRED
}

const val CANCEL_REASON_LISTING_REMOVED = "LISTING_REMOVED"
const val CANCEL_REASON_DONOR_RELEASED = "DONOR_RELEASED"
const val CANCEL_REASON_PICKUP_EXPIRED = "PICKUP_EXPIRED"

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
    @SerialName("recipient_name") val recipientName: String,
    @SerialName("unit_price") val unitPrice: Double = 0.0,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("pickup_deadline") val pickupDeadline: String? = null
) {
    fun toReservation() =
        Reservation(id, foodPostId, recipientId, quantity, status, createdAt, updatedAt, unitPrice, cancelReason, pickupDeadline)
}


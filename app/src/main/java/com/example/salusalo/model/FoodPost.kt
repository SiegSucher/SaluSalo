package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.math.BigDecimal
import java.math.RoundingMode

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
    /** Price per unit in pesos; 0 means a free donation. */
    val price: Double = 0.0,
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
    // No default, so the price is always sent, including 0 for a donation.
    val price: Double,
    val status: FoodPostStatus = FoodPostStatus.AVAILABLE
)

data class FoodPostDraft(
    val name: String,
    val description: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val pickupLocation: String,
    val expirationDate: String,
    val price: Double
)

/** Formats pesos with two decimals, e.g. 45.1 -> "₱45.10". */
fun formatPeso(amount: BigDecimal): String =
    "₱" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

fun formatPeso(amount: Double): String = formatPeso(BigDecimal.valueOf(amount))

/** Price for [quantity] units at [unitPrice], without binary rounding errors. */
fun reservationTotal(unitPrice: Double, quantity: Int): BigDecimal =
    BigDecimal.valueOf(unitPrice).multiply(BigDecimal(quantity))
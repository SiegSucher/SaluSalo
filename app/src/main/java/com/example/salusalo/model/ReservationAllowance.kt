package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ReservationAllowance(
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("available_quantity") val availableQuantity: Int,
    @SerialName("max_per_receiver") val maxPerReceiver: Int? = null,
    @SerialName("used_quantity") val usedQuantity: Long,
    @SerialName("remaining_allowance") val remainingAllowance: Int? = null,
    @SerialName("can_reserve") val canReserve: Int,
    @SerialName("pickup_start") val pickupStart: String? = null,
    @SerialName("pickup_end") val pickupEnd: String? = null,
    @SerialName("expiration_date") val expirationDate: String,
    @SerialName("server_now") val serverNow: String
)

/** Display-only eligibility; the reservation RPC validates again atomically. */
fun ReservationAllowance.allowedPickupMinutes(now: Instant): List<Int> {
    if (canReserve <= 0) return emptyList()
    val expiry = parseTimestamp(expirationDate) ?: return emptyList()
    val start = parseTimestamp(pickupStart)
    val end = parseTimestamp(pickupEnd)
    if (pickupStart != null && start == null) return emptyList()
    if (pickupEnd != null && end == null) return emptyList()
    return listOf(15, 30, 60, 120).filter { minutes ->
        // Leave a small margin for the confirmation request's travel time.
        val deadline = now.plusSeconds(minutes * 60L)
        val guardedDeadline = deadline.plusSeconds(2)
        !guardedDeadline.isAfter(expiry) && (end == null || !guardedDeadline.isAfter(end)) &&
                (start == null || deadline.isAfter(start))
    }
}

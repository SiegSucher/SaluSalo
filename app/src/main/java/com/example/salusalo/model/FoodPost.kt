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
    // Optional details; listings made before 20260929_phase2_listing_details.sql have none.
    @SerialName("pickup_start") val pickupStart: String? = null,
    @SerialName("pickup_end") val pickupEnd: String? = null,
    /** A [ListingHandover] code, or null when not set. */
    val handover: String? = null,
    val allergens: String? = null,
    @SerialName("storage_notes") val storageNotes: String? = null,
    /** Photos 2–4 as private food-images paths; [imageUrl] stays the cover. */
    @SerialName("extra_image_paths") val extraImagePaths: List<String> = emptyList(),
    /** The pickup pin; both null for listings without one (see [pickupPin] in Geo.kt). */
    @SerialName("pickup_latitude") val pickupLatitude: Double? = null,
    @SerialName("pickup_longitude") val pickupLongitude: Double? = null,
    /** The most one person may reserve (open plus picked up); null = no limit. Set on Post only. */
    @SerialName("max_per_recipient") val maxPerRecipient: Int? = null,
    @Transient val displayImageUrl: String? = null,
    /** The donor's name from get_listing_donor_profiles(); null if it could not be loaded. */
    @Transient val donorName: String? = null,
    /** The donor's rating (one decimal) and number of reviews; null and 0 without reviews. */
    @Transient val donorRatingAverage: Double? = null,
    @Transient val donorReviewCount: Int = 0,
    /** True when the donor's email address was confirmed (the "Email verified" badge). */
    @Transient val donorEmailVerified: Boolean = false
)

/** "★ 4.7 (12)" for the donor, or null when they have no reviews. */
val FoodPost.donorRatingLabel: String?
    get() = ratingLabel(donorRatingAverage, donorReviewCount)

/** True when a cover photo is stored; [FoodPost.displayImageUrl] is null when it could not be signed. */
val FoodPost.hasPhoto: Boolean
    get() = !imageUrl.isNullOrBlank()

@Serializable
enum class FoodPostStatus {
    AVAILABLE, RESERVED, CLAIMED, EXPIRED,
    /** Deleted by its donor after it was reserved; kept for its reservations and chats. */
    REMOVED
}

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
    // No defaults either: supabase-kt leaves out values equal to their default,
    // so these are always sent, as null when empty.
    @SerialName("pickup_start") val pickupStart: String?,
    @SerialName("pickup_end") val pickupEnd: String?,
    val handover: String?,
    val allergens: String?,
    @SerialName("storage_notes") val storageNotes: String?,
    @SerialName("extra_image_paths") val extraImagePaths: List<String>,
    // The pickup pin: required for a new listing, so no defaults and never null.
    @SerialName("pickup_latitude") val pickupLatitude: Double,
    @SerialName("pickup_longitude") val pickupLongitude: Double,
    // No default either, so it is always sent (null for no limit).
    @SerialName("max_per_recipient") val maxPerRecipient: Int?,
    val status: FoodPostStatus = FoodPostStatus.AVAILABLE
)

/** How the food changes hands. [code] is the value stored in food_posts.handover. */
enum class ListingHandover(val code: String, val label: String) {
    MEET_AT_PICKUP_LOCATION("MEET_AT_PICKUP_LOCATION", "Meet at pickup location"),
    COLLECT_FROM_PROVIDER("COLLECT_FROM_PROVIDER", "Collect from provider");

    companion object {
        fun fromCode(code: String?): ListingHandover? = entries.firstOrNull { it.code == code }
        fun fromLabel(label: String): ListingHandover? = entries.firstOrNull { it.label == label }
    }
}

/** Row returned by get_my_saved_listing_names(). */
@Serializable
data class SavedListingName(
    @SerialName("food_post_id") val foodPostId: String,
    val name: String,
    @SerialName("image_path") val imagePath: String? = null
)

/** What remove_my_food_post() did. */
@Serializable
data class RemoveFoodPostResult(
    /** DELETED (never reserved; the row is gone) or REMOVED (hidden, kept for its reservations). */
    val outcome: String,
    @SerialName("cancelled_reservations") val cancelledReservations: Int = 0
) {
    val deleted: Boolean get() = outcome == "DELETED"
}

data class FoodPostDraft(
    val name: String,
    val description: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val pickupLocation: String,
    val expirationDate: String,
    val price: Double,
    val pickupStart: String?,
    val pickupEnd: String?,
    val handover: ListingHandover?,
    val allergens: String,
    val storageNotes: String,
    val pickupPin: GeoPin,
    val maxPerRecipient: Int? = null
)

/**
 * The fields update_my_food_post_with_pin() may change. Quantity, unit and
 * photos never change. [pickupPin] null keeps the stored pin (or its absence):
 * a pin can be added or moved, never removed.
 */
data class FoodPostEdit(
    val name: String,
    val description: String,
    val category: String,
    val price: Double,
    val pickupLocation: String,
    val expirationDate: String,
    val pickupStart: String?,
    val pickupEnd: String?,
    val handover: ListingHandover?,
    val allergens: String,
    val storageNotes: String,
    val pickupPin: GeoPin?
)

/** How far in the past a new listing's pickup start may be, so a "Start now" time still passes a few minutes later. */
const val PICKUP_START_SLACK_MILLIS = 10 * 60_000L

/**
 * True when a new listing's pickup start is at most [PICKUP_START_SLACK_MILLIS]
 * in the past. Post's check and "Check your listing" both use it, so they agree.
 * The server doesn't check the start; Edit accepts a start in the past.
 */
fun isPickupStartAllowed(startMillis: Long, nowMillis: Long): Boolean =
    startMillis >= nowMillis - PICKUP_START_SLACK_MILLIS

/** True when the listing's expiry has passed, whatever its stored status. */
fun FoodPost.isExpired(now: java.time.Instant = java.time.Instant.now()): Boolean =
    parseTimestamp(expirationDate)?.let { !it.isAfter(now) } ?: false

/**
 * Why create_reservation would refuse this listing now, in its own words, or
 * null when it can be reserved. The details page shows it instead of Reserve;
 * the server still decides.
 */
fun FoodPost.reserveBlocker(now: java.time.Instant = java.time.Instant.now()): String? = when {
    status == FoodPostStatus.REMOVED -> "The donor removed this listing."
    isExpired(now) -> "This listing has expired."
    parseTimestamp(pickupEnd)?.let { !it.isAfter(now) } == true -> "The pickup time for this listing has ended."
    status != FoodPostStatus.AVAILABLE || quantity <= 0 -> "Fully reserved: nothing is left to reserve."
    else -> null
}

/** True when Home would offer Reserve on it now: nothing in [reserveBlocker] stops it. */
fun FoodPost.canBeReserved(now: java.time.Instant = java.time.Instant.now()): Boolean = reserveBlocker(now) == null

/**
 * Activity's Active filter for the donor's own listing: it can still be
 * reserved, or it still has food to hand over. Until the reservations are
 * known, a fully reserved listing counts as active.
 */
fun FoodPost.isActiveListing(hasOpenReservations: Boolean, reservationsKnown: Boolean, now: java.time.Instant): Boolean =
    hasOpenReservations || (!reservationsKnown && status == FoodPostStatus.RESERVED) || canBeReserved(now)

/**
 * The next moment [reserveBlocker] can change by itself (the expiry or the end
 * of pickup, whichever comes first after [now]), or null when both have passed.
 */
fun FoodPost.nextReserveBlockerChange(now: java.time.Instant): java.time.Instant? =
    listOfNotNull(parseTimestamp(expirationDate), parseTimestamp(pickupEnd))
        .filter { it.isAfter(now) }
        .minOrNull()

/** Formats pesos with two decimals, e.g. 45.1 -> "₱45.10". */
fun formatPeso(amount: BigDecimal): String =
    "₱" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

fun formatPeso(amount: Double): String = formatPeso(BigDecimal.valueOf(amount))

/** Price for [quantity] units at [unitPrice], without binary rounding errors. */
fun reservationTotal(unitPrice: Double, quantity: Int): BigDecimal =
    BigDecimal.valueOf(unitPrice).multiply(BigDecimal(quantity))

/** How long a recipient may ask for a reservation to be held (create_reservation_with_hold). */
val HOLD_MINUTES = listOf(15, 30, 60, 120)
const val DEFAULT_HOLD_MINUTES = 30

/**
 * The deadline create_reservation_with_hold() will set: the hold counted from
 * now or from the pickup start, whichever is later, but never after the end of
 * pickup or the expiry. The server's own value is what counts.
 */
fun pickupDeadlineEstimate(
    now: java.time.Instant,
    pickupStart: java.time.Instant?,
    pickupEnd: java.time.Instant?,
    expiration: java.time.Instant,
    holdMinutes: Int
): java.time.Instant {
    val start = if (pickupStart != null && pickupStart.isAfter(now)) pickupStart else now
    return listOfNotNull(start.plusSeconds(holdMinutes * 60L), pickupEnd, expiration).min()
}

/** How many one person may reserve now: what's left, within the listing's per-person limit. */
fun reservableNow(limit: Int?, available: Int, held: Int): Int =
    (if (limit == null) available else minOf(available, limit - held)).coerceAtLeast(0)
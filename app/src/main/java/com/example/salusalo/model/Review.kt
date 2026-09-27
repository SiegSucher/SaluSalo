package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Longest review comment and report details, as the server enforces them. */
const val REVIEW_COMMENT_MAX = 500
const val REPORT_DETAILS_MAX = 1000

/** How many reviews the Donor/Seller page and the Profile reviews page load. */
const val DONOR_REVIEWS_LIMIT = 20
const val MY_REVIEWS_LIMIT = 50

/**
 * Row of get_listing_donor_profiles(): the donor of a listing the caller may
 * see. Names only; no other profile data.
 */
@Serializable
data class ListingDonorProfile(
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("donor_name") val donorName: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    /** Rounded to one decimal by the server; null without reviews. */
    @SerialName("rating_average") val ratingAverage: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0
)

/** get_my_rating_summary(): the caller's own rating; [average] is null without reviews. */
@Serializable
data class RatingSummary(
    @SerialName("rating_average") val average: Double? = null,
    @SerialName("rating_count") val count: Int = 0
)

/** A review as the Donor page and Profile show it: the reviewer's current name, stars, comment and dates. */
@Serializable
data class ReviewItem(
    @SerialName("reviewer_name") val reviewerName: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** One of the caller's own reviews, from get_my_given_reviews() or submit_review(). */
@Serializable
data class MyReview(
    @SerialName("reservation_id") val reservationId: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** submit_review() only: true when the review was new, false when it was changed. */
    val created: Boolean? = null
)

/** What a report is about; [code] is submit_report()'s p_target. */
enum class ReportTarget(val code: String) {
    /** A listing (p_target_id = the listing). */
    LISTING("LISTING"),
    /** A listing's donor or seller (p_target_id = the listing). */
    DONOR("DONOR"),
    /** The other person in a chat (p_target_id = the conversation). */
    CHAT("CHAT")
}

/** The reasons submit_report() accepts; listings and people have their own lists. */
enum class ReportReason(val code: String, val label: String, val forListing: Boolean) {
    UNSAFE_FOOD("UNSAFE_FOOD", "Unsafe or spoiled food", true),
    MISLEADING("MISLEADING", "Wrong or misleading details", true),
    SPAM_FAKE("SPAM_FAKE", "Spam or fake listing", true),
    LISTING_OTHER("OTHER", "Other", true),
    ABUSIVE("ABUSIVE", "Rude or abusive", false),
    NO_SHOW("NO_SHOW", "Didn't show up", false),
    SCAM("SCAM", "Scam or fraud", false),
    SPAM("SPAM", "Spam", false),
    PERSON_OTHER("OTHER", "Other", false);

    /** "Other" needs details. */
    val needsDetails: Boolean get() = code == "OTHER"

    companion object {
        fun forTarget(target: ReportTarget): List<ReportReason> =
            entries.filter { it.forListing == (target == ReportTarget.LISTING) }
    }
}

/** A one-decimal average as text, e.g. 4.7 -> "4.7", 4.0 -> "4.0". */
fun formatRatingAverage(average: Double): String =
    BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).toPlainString()

/** "★ 4.7 (12)", or null when there are no reviews. */
fun ratingLabel(average: Double?, count: Int): String? =
    if (average == null || count <= 0) null else "★ ${formatRatingAverage(average)} ($count)"

/** "★★★★☆" for a whole number of stars from 0 to 5. */
fun starsText(stars: Int): String {
    val filled = stars.coerceIn(0, 5)
    return "★".repeat(filled) + "☆".repeat(5 - filled)
}

/** Stars for an average, rounded to whole stars (4.5 -> 5). */
fun starsText(average: Double): String =
    starsText(BigDecimal.valueOf(average).setScale(0, RoundingMode.HALF_UP).toInt())

/** "1 review" or "12 reviews". */
fun reviewCountText(count: Int): String = if (count == 1) "1 review" else "$count reviews"

private val reviewDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/** When the review was last written or changed, e.g. "Sep 27, 2026"; null if unknown. */
fun ReviewItem.dateText(): String? =
    parseTimestamp(updatedAt ?: createdAt)?.let { reviewDateFormat.format(it.atZone(ZoneId.systemDefault())) }

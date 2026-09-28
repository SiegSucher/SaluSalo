package com.example.salusalo.model

import java.time.Instant
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

// "Recommended for you" on Home: content-based filtering, worked out on the phone.
//
// Each listing becomes a vector of its words (TF-IDF: a word in the name counts
// twice and one in the description once; words that few listings use weigh
// more), its category, whether it is free or for sale, and its donor. The
// account's profile is the weighted sum of the vectors of the listings it chose:
// reserved (open or claimed), claimed and rated 4–5, or saved. A listing's score
// is its cosine similarity to that profile, plus a boost when it expires soon and
// one when its pin is near the phone. Only data the app has already loaded is
// used; the profile lives in memory and is never stored or sent.

internal const val WORDS_WEIGHT = 1.0
internal const val CATEGORY_WEIGHT = 1.0
internal const val PRICE_TYPE_WEIGHT = 0.5
internal const val DONOR_WEIGHT = 0.5

/** An open or claimed reservation. */
internal const val RESERVED_WEIGHT = 3.0
/** A claimed reservation this account rated 4 or 5 stars. */
internal const val LIKED_WEIGHT = 4.0
/** A saved listing that is still in the feed. */
internal const val SAVED_WEIGHT = 2.0

internal const val SIMILARITY_WEIGHT = 0.7
/** Added when the listing expires soon ([expiresSoon]). */
internal const val EXPIRY_BOOST = 0.15
/** Added in full at the phone's position, falling to 0 at [NEARBY_RADIUS_METERS]. */
internal const val DISTANCE_BOOST = 0.15

/** Home's "Recommended for you" row shows at most this many listings. */
const val RECOMMENDATION_LIMIT = 5

/** Longest listing name a reason shows in full; longer names are shortened with "…". */
private const val REASON_NAME_LENGTH = 20

/** Scores are compared at this precision, so the order is a total order and ties are real ties. */
private const val SCORE_PRECISION = 1e9

private val STOP_WORDS = setOf(
    // English
    "the", "and", "for", "with", "from", "this", "that", "these", "those", "are", "was", "were",
    "been", "has", "have", "had", "not", "but", "you", "your", "our", "its", "can", "will", "all",
    "any", "some", "per", "each", "very", "just", "also", "only", "more", "into", "out", "than",
    "then", "them", "they", "their", "there", "here", "what", "when", "where", "which", "who",
    "how", "may", "please",
    // Filipino
    "ang", "mga", "para", "nang", "lang", "din", "rin", "ito", "iyan", "yan", "yung", "iyong",
    "kay", "kung", "pero", "dahil", "hindi", "wala", "meron", "mayroon", "ako", "ikaw", "siya",
    "kami", "tayo", "sila", "namin", "natin", "nila", "niya", "amin", "atin", "lamang", "pati",
    "naman", "opo", "kasi", "kaya", "sana", "lahat"
)

/** A listing for the "Recommended for you" row. */
data class Recommendation(
    val post: FoodPost,
    /** One true reason for the card, or null when nothing stands out. */
    val reason: String?,
    /** Similarity and boosts together; used for the order only, never shown. */
    val score: Double
)

/**
 * Up to [RECOMMENDATION_LIMIT] listings from [feed] for this account, best first,
 * or null when the account has chosen nothing yet (no open or claimed
 * reservation and no saved listing in the feed), so Home shows its usual row.
 *
 * Never recommended: the account's own listings, listings it reserved (in any
 * status) or saved, and every listing of a donor whose latest review from this
 * account has 1–2 stars. [include] then applies the account's food preferences.
 * [phonePin] is the phone's position for the distance boost, or null.
 */
fun recommendListings(
    feed: List<FoodPost>,
    myProfileId: String?,
    myReservations: List<ReservationDisplay>,
    myReviews: Map<String, MyReview>,
    savedIds: Set<String>,
    now: Instant,
    phonePin: GeoPin?,
    include: (FoodPost) -> Boolean = { true }
): List<Recommendation>? {
    val feedById = feed.associateBy { it.id }
    val choices = chosenListings(feed, feedById, myReservations, myReviews, savedIds)
    if (choices.isEmpty()) return null

    val corpus = LinkedHashMap<String, FoodPost>()
    feed.forEach { corpus.putIfAbsent(it.id, it) }
    choices.forEach { corpus.putIfAbsent(it.post.id, it.post) }
    val vectors = ListingVectors(corpus.values)

    val profile = HashMap<String, Double>()
    choices.forEach { choice ->
        vectors.of(choice.post).forEach { (key, value) ->
            profile[key] = (profile[key] ?: 0.0) + choice.weight * value
        }
    }
    val profileLength = vectorLength(profile)

    val reservedIds = myReservations.map { it.reservation.foodPostId }.toSet()
    val lowRatedDonors = lowRatedDonors(myReservations, myReviews)
    val candidates = feed.filter { post ->
        post.donorId != myProfileId && post.id !in reservedIds && post.id !in savedIds &&
            post.donorId !in lowRatedDonors && include(post)
    }

    val scored = candidates.map { post ->
        val vector = vectors.of(post)
        // The listing's vector has length 1 (or is empty), so this is the cosine.
        val similarity = if (profileLength > 0.0) dot(profile, vector) / profileLength else 0.0
        val distance = phonePin?.let { here -> post.pickupPin?.let { distanceMeters(here, it) } }
        ScoredListing(
            post = post,
            similarityPart = SIMILARITY_WEIGHT * similarity,
            expiryPart = if (post.expiresSoon(now)) EXPIRY_BOOST else 0.0,
            distance = distance,
            distancePart = if (distance != null && distance <= NEARBY_RADIUS_METERS) {
                DISTANCE_BOOST * (1 - distance / NEARBY_RADIUS_METERS)
            } else {
                0.0
            }
        )
    }

    // Highest score first, then the newest listing, then the feed's order (the sort is stable).
    return scored
        .sortedWith(
            compareByDescending<ScoredListing> { rounded(it.score) }
                .thenBy(nullsLast(reverseOrder())) { parseTimestamp(it.post.createdAt) }
        )
        .take(RECOMMENDATION_LIMIT)
        .map { Recommendation(it.post, reasonFor(it, choices, vectors), it.score) }
}

/** Lowercase words of 3 or more letters or digits, without common English and Filipino words. */
internal fun recommendationWords(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    val words = ArrayList<String>()
    val current = StringBuilder()
    fun endWord() {
        if (current.length >= 3) {
            val word = current.toString()
            if (word !in STOP_WORDS) words += word
        }
        current.setLength(0)
    }
    text.lowercase(Locale.ROOT).forEach { char ->
        if (char.isLetterOrDigit()) current.append(char) else endWord()
    }
    endWord()
    return words
}

/** The listing name for a reason: up to 20 characters, else cut at a word and ended with "…". */
internal fun shortReasonName(name: String): String {
    val text = name.trim()
    if (text.length <= REASON_NAME_LENGTH) return text
    var head = text.substring(0, REASON_NAME_LENGTH - 1)
    val space = head.lastIndexOf(' ')
    if (space > 0) head = head.substring(0, space)
    if (head.last().isHighSurrogate()) head = head.dropLast(1)
    return head.trimEnd() + "…"
}

/** The item vectors of the listings in [corpus], with IDF over that corpus. Each has length 1, or is empty. */
internal class ListingVectors(corpus: Collection<FoodPost>) {
    private val listingCount = corpus.size
    private val documentFrequency = HashMap<String, Int>()
    private val cache = HashMap<String, Map<String, Double>>()

    init {
        corpus.forEach { post ->
            termFrequencies(post).keys.forEach { word ->
                documentFrequency[word] = (documentFrequency[word] ?: 0) + 1
            }
        }
    }

    /** The listing's usable words (name and description). */
    fun wordsOf(post: FoodPost): Set<String> = termFrequencies(post).keys

    fun of(post: FoodPost): Map<String, Double> = cache.getOrPut(post.id) {
        val vector = HashMap<String, Double>()
        val tfIdf = termFrequencies(post).mapValues { (word, count) -> count * idf(word) }
        val wordsLength = vectorLength(tfIdf)
        // A listing without usable words keeps the word part empty (never 0 / 0).
        if (wordsLength > 0.0) {
            tfIdf.forEach { (word, value) -> vector["w:$word"] = WORDS_WEIGHT * value / wordsLength }
        }
        categoryKey(post)?.let { vector["c:$it"] = CATEGORY_WEIGHT }
        vector[if (post.price > 0) "p:sale" else "p:free"] = PRICE_TYPE_WEIGHT
        vector["d:${post.donorId}"] = DONOR_WEIGHT
        val length = vectorLength(vector)
        if (length > 0.0) vector.mapValues { it.value / length } else emptyMap()
    }

    private fun idf(word: String): Double {
        val listings = documentFrequency[word] ?: 0
        return ln((1.0 + listingCount) / (1.0 + listings)) + 1.0
    }
}

/** How often each word appears: 2 for each time in the name, 1 for each time in the description. */
private fun termFrequencies(post: FoodPost): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    recommendationWords(post.name).forEach { counts[it] = (counts[it] ?: 0) + 2 }
    recommendationWords(post.description).forEach { counts[it] = (counts[it] ?: 0) + 1 }
    return counts
}

private fun categoryKey(post: FoodPost): String? =
    post.category.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }

private class Choice(
    val post: FoodPost,
    val weight: Double,
    /** From an open or claimed reservation (even when also saved), else saved. */
    val reserved: Boolean,
    /** When the newest reservation of it was made; null for a saved listing (the save time isn't loaded). */
    val chosenAt: Instant?
)

private class ScoredListing(
    val post: FoodPost,
    val similarityPart: Double,
    val expiryPart: Double,
    val distance: Double?,
    val distancePart: Double
) {
    val score: Double get() = similarityPart + expiryPart + distancePart
}

/** The listings this account chose, each once at its highest weight. Cancelled reservations don't count. */
private fun chosenListings(
    feed: List<FoodPost>,
    feedById: Map<String, FoodPost>,
    myReservations: List<ReservationDisplay>,
    myReviews: Map<String, MyReview>,
    savedIds: Set<String>
): List<Choice> {
    val choices = LinkedHashMap<String, Choice>()
    myReservations.forEach { display ->
        val reservation = display.reservation
        val weight = when (reservation.status) {
            ReservationStatus.CANCELLED -> return@forEach
            ReservationStatus.RESERVED -> RESERVED_WEIGHT
            ReservationStatus.CLAIMED ->
                if ((myReviews[reservation.id]?.rating ?: 0) in 4..5) LIKED_WEIGHT else RESERVED_WEIGHT
        }
        // The feed's copy when there is one, so every vector comes from the same data.
        val post = feedById[display.foodPost.id] ?: display.foodPost
        val at = parseTimestamp(reservation.createdAt)
        val current = choices[post.id]
        choices[post.id] = if (current == null) {
            Choice(post, weight, reserved = true, chosenAt = at)
        } else {
            Choice(post, maxOf(current.weight, weight), reserved = true, chosenAt = newer(current.chosenAt, at))
        }
    }
    feed.forEach { post ->
        if (post.id in savedIds && post.id !in choices) {
            choices[post.id] = Choice(post, SAVED_WEIGHT, reserved = false, chosenAt = null)
        }
    }
    return choices.values.toList()
}

/** Donors whose latest review from this account (by its update, else creation time) has 1–2 stars. */
private fun lowRatedDonors(
    myReservations: List<ReservationDisplay>,
    myReviews: Map<String, MyReview>
): Set<String> {
    val donorByReservation = myReservations.associate { it.reservation.id to it.foodPost.donorId }
    val latestFirst = compareBy<MyReview, Instant?>(nullsFirst()) {
        parseTimestamp(it.updatedAt) ?: parseTimestamp(it.createdAt)
    }
    return myReviews.values
        .mapNotNull { review -> donorByReservation[review.reservationId]?.let { donor -> donor to review } }
        .groupBy({ it.first }, { it.second })
        .filterValues { reviews -> (reviews.maxWithOrNull(latestFirst)?.rating ?: 0) in 1..2 }
        .keys
}

/**
 * The card's reason, from its biggest part (on a tie: similarity, then expiry,
 * then distance). Similarity names the chosen listing most like the card, and
 * shows only when the card shares its category or a word with it; otherwise the
 * larger of the other parts gives the reason. Nothing above 0: no reason.
 */
private fun reasonFor(listing: ScoredListing, choices: List<Choice>, vectors: ListingVectors): String? {
    val similarity = rounded(listing.similarityPart)
    val expiry = rounded(listing.expiryPart)
    val nearby = rounded(listing.distancePart)
    if (similarity > 0 && similarity >= expiry && similarity >= nearby) {
        similarityReason(listing.post, choices, vectors)?.let { return it }
    }
    return when {
        expiry > 0 && expiry >= nearby -> "Expires soon"
        nearby > 0 && listing.distance != null -> distanceLabel(listing.distance) + " away"
        else -> null
    }
}

private fun similarityReason(post: FoodPost, choices: List<Choice>, vectors: ListingVectors): String? {
    val card = vectors.of(post)
    // The most similar choice; on a tie the higher weight, then the newer choice, then the first.
    val closest = choices.maxWithOrNull(
        compareBy<Choice> { rounded(dot(card, vectors.of(it.post))) }
            .thenBy { it.weight }
            .thenBy(nullsFirst()) { it.chosenAt }
    ) ?: return null
    val sameCategory = categoryKey(post)?.let { it == categoryKey(closest.post) } ?: false
    val sharedWord = vectors.wordsOf(post).any { it in vectors.wordsOf(closest.post) }
    if (!sameCategory && !sharedWord) return null
    val verb = if (closest.reserved) "reserved" else "saved"
    return "Because you $verb ${shortReasonName(closest.post.name)}"
}

private fun newer(first: Instant?, second: Instant?): Instant? = when {
    first == null -> second
    second == null -> first
    else -> maxOf(first, second)
}

private fun rounded(value: Double): Long = Math.round(value * SCORE_PRECISION)

private fun dot(first: Map<String, Double>, second: Map<String, Double>): Double {
    val (small, large) = if (first.size <= second.size) first to second else second to first
    return small.entries.sumOf { (key, value) -> value * (large[key] ?: 0.0) }
}

private fun vectorLength(vector: Map<String, Double>): Double = sqrt(vector.values.sumOf { it * it })

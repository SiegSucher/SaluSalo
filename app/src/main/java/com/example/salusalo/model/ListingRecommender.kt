package com.example.salusalo.model

import java.time.Instant
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Why a listing is in "Recommended for you": the learned feature of the
 * listing that adds the most to its score, preferring its category, donor or
 * a word of its name over free or for sale when one of them adds enough.
 * [fromReservation] is true when that feature is in a listing the account
 * reserved, false when it is only in listings it saved. [category] is the
 * listing's own category, for [Kind.CATEGORY].
 */
data class RecommendationReason(
    val kind: Kind,
    val fromReservation: Boolean,
    val category: String? = null
) {
    enum class Kind { CATEGORY, FREE, FOR_SALE, DONOR, SIMILAR }
}

/** A listing [ListingRecommender] picked, the model's score for it (0 to 1) and why. */
data class Recommendation(
    val post: FoodPost,
    val score: Double,
    val reason: RecommendationReason
)

/**
 * "Recommended for you" on Home: a small machine-learning model, a logistic
 * regression trained on the phone from the account's own reservations and
 * saved listings.
 *
 * - Examples: every listing the account reserved (whatever happened to the
 *   reservation later) is a positive with weight 1, every listing it only
 *   saved a positive with weight 0.5. The other listings in the feed, apart
 *   from the account's own, are the negatives: listings it hasn't chosen so
 *   far, the usual reading of implicit feedback. They share the positives'
 *   total weight, so both classes count the same however long the feed is.
 *   Only the newest 50 chosen listings and the newest 100 others are used.
 * - Features of a listing ([listingFeatures]): its category, free or for sale,
 *   its donor, and the words of its name. Each group adds up to one unit, so a
 *   long name doesn't outweigh the category. The model learns a weight only for
 *   features that occur in the account's reserved or saved listings.
 * - Training: full-batch gradient descent on the weighted log loss with L2
 *   regularisation, from zero weights and for a fixed number of steps. The
 *   examples are sorted first, so the same history and feed always give the
 *   same model, whatever order the lists arrive in.
 *
 * Nothing is stored or sent anywhere: the model lives in memory and is trained
 * again whenever the feed, the reservations or the saved listings change.
 */
class ListingRecommender private constructor(
    private val weights: Map<String, Double>,
    private val bias: Double,
    // Listings never recommended: those the account already reserved or saved.
    private val chosenIds: Set<String>,
    private val ownProfileId: String?,
    // Features found in a listing the account reserved, for the reason's wording.
    private val reservedFeatures: Set<String>,
    private val facts: ListingFacts
) {
    /** The model's score for [post], from 0 to 1: higher means more like what the account chooses. */
    fun score(post: FoodPost): Double = sigmoid(logit(facts.features(post)))

    /**
     * Up to [limit] of [candidates], best first. Never the account's own
     * listings or those it already reserved or saved, and only listings that
     * share something the model learned to look for (a feature adding at least
     * [MIN_REASON_WEIGHT]), so each one has a real reason. Equal scores go to
     * the newest listing.
     */
    fun recommend(candidates: List<FoodPost>, limit: Int): List<Recommendation> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        return candidates
            .filter { it.id !in chosenIds && !isOwn(it, ownProfileId) && seen.add(it.id) }
            .mapNotNull(::recommendation)
            .map { it to facts.time(it.post) }
            .sortedWith(
                compareByDescending<Pair<Recommendation, Instant>> { it.first.score }
                    .thenByDescending { it.second }
                    .thenBy { it.first.post.id }
            )
            .take(limit)
            .map { it.first }
    }

    private fun recommendation(post: FoodPost): Recommendation? {
        val features = facts.features(post)
        // The strongest feature overall decides whether the listing is shown; the
        // strongest specific one (not free or for sale) explains it when it adds
        // enough, as "From a donor you reserved from" says more than "Free food".
        // Features come in name order, so of two equal contributions the first wins.
        var strongest: String? = null
        var strongestWeight = 0.0
        var specific: String? = null
        var specificWeight = 0.0
        for ((name, value) in features) {
            val contribution = (weights[name] ?: 0.0) * value
            if (contribution > strongestWeight) {
                strongest = name
                strongestWeight = contribution
            }
            if (!name.startsWith(PRICE) && contribution > specificWeight) {
                specific = name
                specificWeight = contribution
            }
        }
        if (strongest == null || strongestWeight < MIN_REASON_WEIGHT) return null
        val reasonFeature = specific?.takeIf { specificWeight >= MIN_REASON_WEIGHT } ?: strongest
        return Recommendation(post, sigmoid(logit(features)), reasonFor(reasonFeature, post))
    }

    // Only features of listings the account chose have a weight, so the reason
    // always points at something it reserved or saved.
    private fun reasonFor(feature: String, post: FoodPost): RecommendationReason {
        val fromReservation = feature in reservedFeatures
        return when {
            feature.startsWith(CATEGORY) ->
                RecommendationReason(RecommendationReason.Kind.CATEGORY, fromReservation, post.category.trim())
            feature == FREE -> RecommendationReason(RecommendationReason.Kind.FREE, fromReservation)
            feature == FOR_SALE -> RecommendationReason(RecommendationReason.Kind.FOR_SALE, fromReservation)
            feature.startsWith(DONOR) -> RecommendationReason(RecommendationReason.Kind.DONOR, fromReservation)
            else -> RecommendationReason(RecommendationReason.Kind.SIMILAR, fromReservation)
        }
    }

    private fun logit(features: Map<String, Double>): Double {
        var z = bias
        for ((name, value) in features) z += (weights[name] ?: 0.0) * value
        return z
    }

    private class Positive(val post: FoodPost, val weight: Double)

    private class Labelled(val post: FoodPost, val label: Double, val weight: Double)

    private class Example(val columns: IntArray, val values: DoubleArray, val label: Double, val weight: Double)

    companion object {
        /** Weight of a listing the account reserved, and of one it only saved. */
        const val RESERVED_WEIGHT = 1.0
        const val SAVED_WEIGHT = 0.5

        /** The smallest contribution that counts as a reason to recommend a listing. */
        const val MIN_REASON_WEIGHT = 0.1

        // L2 strength, gradient-descent step size and number of steps. A listing's
        // features have a squared length of at most 5 with the bias, so the loss's
        // curvature is at most 5/4 + L2 and a step of 0.5 lowers the loss every time.
        private const val L2 = 0.1
        private const val LEARNING_RATE = 0.5
        private const val TRAINING_STEPS = 200

        // It learns from the newest 50 listings the account chose and the newest
        // 100 others, so training stays at a few milliseconds on the phone (Home
        // trains it on the main thread, like its other lists).
        private const val MAX_POSITIVES = 50
        private const val MAX_NEGATIVES = 100

        /**
         * Trains a model on the account's history: [reserved] are the listings of
         * its own reservations, [savedIds] the listings it saved, whose details
         * come from [saved] or else from [feed] (a save not loaded yet). [feed] is
         * the listings available now. Returns null when there is nothing to learn
         * from yet (no reserved or saved listing with details) or nothing left to
         * recommend (every other listing is the account's own, reserved or saved).
         */
        fun train(
            feed: List<FoodPost>,
            reserved: List<FoodPost>,
            savedIds: Set<String>,
            saved: List<FoodPost>,
            ownProfileId: String?
        ): ListingRecommender? {
            val positives = HashMap<String, Positive>()
            for (post in reserved) {
                if (!isOwn(post, ownProfileId)) positives[post.id] = Positive(post, RESERVED_WEIGHT)
            }
            val details = HashMap<String, FoodPost>()
            feed.forEach { details[it.id] = it }
            saved.forEach { details[it.id] = it }
            for (id in savedIds) {
                if (id in positives) continue
                val post = details[id] ?: continue
                if (!isOwn(post, ownProfileId)) positives[id] = Positive(post, SAVED_WEIGHT)
            }
            val chosenIds = reserved.mapTo(HashSet()) { it.id } + savedIds
            val facts = ListingFacts()
            val positiveExamples = newestFirst(positives.values, facts) { it.post }.take(MAX_POSITIVES)
            val negativeExamples = newestFirst(
                feed.filter { it.id !in chosenIds && !isOwn(it, ownProfileId) }.distinctBy { it.id }, facts
            ) { it }.take(MAX_NEGATIVES)
            if (positiveExamples.isEmpty() || negativeExamples.isEmpty()) return null

            // Both classes weigh the same in total.
            val positiveTotal = positiveExamples.sumOf { it.weight }
            val negativeWeight = positiveTotal / negativeExamples.size
            val labelled = (positiveExamples.map { Labelled(it.post, 1.0, it.weight) } +
                    negativeExamples.map { Labelled(it, 0.0, negativeWeight) })
                .sortedBy { it.post.id }
            val featureMaps = labelled.map { facts.learn(it.post) }
            // The vocabulary is what the account's own choices contain. Anything else
            // weighs nothing, so no listing is marked down for what makes it different
            // (the listings scored later are the negatives here).
            val names = labelled.indices
                .filter { labelled[it].label == 1.0 }
                .flatMapTo(sortedSetOf()) { featureMaps[it].keys }
                .toList()
            val column = HashMap<String, Int>(names.size * 2)
            names.forEachIndexed { index, name -> column[name] = index }
            // Examples that look the same to the model (same label and known
            // features, as feed listings sharing only "free" with the history) are
            // merged and their weights added: the same loss, less work per step.
            val merged = LinkedHashMap<String, Example>()
            labelled.forEachIndexed { i, example ->
                val known = featureMaps[i].filterKeys { it in column }
                val columns = known.keys.map { column.getValue(it) }.toIntArray()
                val values = known.values.toDoubleArray()
                val key = "${example.label}|${columns.contentToString()}|${values.contentToString()}"
                val weight = example.weight + (merged[key]?.weight ?: 0.0)
                merged[key] = Example(columns, values, example.label, weight)
            }
            val examples = merged.values.toList()

            val w = DoubleArray(names.size)
            var b = 0.0
            val total = positiveTotal * 2
            val gradient = DoubleArray(names.size)
            repeat(TRAINING_STEPS) {
                gradient.fill(0.0)
                var biasGradient = 0.0
                for (example in examples) {
                    var z = b
                    for (k in example.columns.indices) z += w[example.columns[k]] * example.values[k]
                    val error = example.weight * (sigmoid(z) - example.label) / total
                    biasGradient += error
                    for (k in example.columns.indices) gradient[example.columns[k]] += error * example.values[k]
                }
                for (j in w.indices) w[j] -= LEARNING_RATE * (gradient[j] + L2 * w[j])
                b -= LEARNING_RATE * biasGradient
            }

            val reservedFeatures = positiveExamples
                .filter { it.weight == RESERVED_WEIGHT }
                .flatMapTo(HashSet()) { facts.learn(it.post).keys }
            return ListingRecommender(
                weights = names.indices.associate { names[it] to w[it] },
                bias = b,
                chosenIds = chosenIds,
                ownProfileId = ownProfileId,
                reservedFeatures = reservedFeatures,
                facts = facts
            )
        }
    }
}

private const val CATEGORY = "category:"
private const val PRICE = "price:"
private const val FREE = PRICE + "free"
private const val FOR_SALE = PRICE + "sale"
private const val DONOR = "donor:"
private const val WORD = "word:"

private fun isOwn(post: FoodPost, ownProfileId: String?): Boolean =
    ownProfileId != null && post.donorId == ownProfileId

/**
 * Each listing's features and time, worked out once while training and reused
 * when the model then scores the same listings (the feed). Keyed by the object
 * itself, so another copy of a listing is always worked out afresh. Once the
 * model is trained it only reads them, so it never changes.
 */
private class ListingFacts {
    private val features = IdentityHashMap<FoodPost, Map<String, Double>>()
    private val times = IdentityHashMap<FoodPost, Instant>()

    // While training: work out and keep.
    fun learn(post: FoodPost): Map<String, Double> = features.getOrPut(post) { listingFeatures(post) }
    fun learnTime(post: FoodPost): Instant = times.getOrPut(post) { parseTimestamp(post.createdAt) ?: Instant.EPOCH }

    // Once trained: read only.
    fun features(post: FoodPost): Map<String, Double> = features[post] ?: listingFeatures(post)
    fun time(post: FoodPost): Instant = times[post] ?: parseTimestamp(post.createdAt) ?: Instant.EPOCH
}

/** [items] newest listing first (each listing's time read once, not per comparison), then by listing ID. */
private fun <T> newestFirst(items: Collection<T>, facts: ListingFacts, post: (T) -> FoodPost): List<T> =
    items.map { it to facts.learnTime(post(it)) }
        .sortedWith(compareByDescending<Pair<T, Instant>> { it.second }.thenBy { post(it.first).id })
        .map { it.first }

private fun sigmoid(z: Double): Double =
    if (z >= 0) 1.0 / (1.0 + exp(-z)) else exp(z).let { it / (1.0 + it) }

/**
 * The model's view of a listing, in name order: its category, free or for
 * sale (as Home's "Free" label: a price of 0), its donor, and the words of its
 * name ([nameWords]). Each present group adds up to one unit (the words share
 * theirs), so every listing's vector has about the same length.
 */
internal fun listingFeatures(post: FoodPost): Map<String, Double> {
    val features = sortedMapOf<String, Double>()
    val category = post.category.trim().lowercase(Locale.ROOT)
    if (category.isNotEmpty()) features[CATEGORY + category] = 1.0
    features[if (post.price > 0.0) FOR_SALE else FREE] = 1.0
    if (post.donorId.isNotBlank()) features[DONOR + post.donorId] = 1.0
    val words = nameWords(post.name)
    if (words.isNotEmpty()) {
        val share = 1.0 / sqrt(words.size.toDouble())
        for (word in words) features[WORD + word] = share
    }
    return features
}

/**
 * The distinct words of a listing's name that say what the food is: lower
 * case, plurals folded ([foldPlural]), without numbers, words under 3 letters
 * and filler such as "fresh", "free", "pack" or "mga".
 */
internal fun nameWords(name: String): Set<String> =
    name.lowercase(Locale.ROOT)
        .split(NON_WORD)
        .map(::foldPlural)
        .filterTo(sortedSetOf()) { it.length >= 3 && it.none(Char::isDigit) && it !in FILLER_WORDS }

/**
 * Folds an English plural and its singular to one form, so "Tomatoes" matches
 * "tomato" and "Cookies" matches "cookie". The form is only compared, never
 * shown, so "pastry" and "pastries" both become "pastri".
 */
internal fun foldPlural(word: String): String {
    val singular = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(2)
        word.length > 4 && PLURAL_ES.any { word.endsWith(it) } -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && KEEP_S.none { word.endsWith(it) } -> word.dropLast(1)
        else -> word
    }
    return when {
        singular.length > 3 && singular.endsWith("ie") -> singular.dropLast(1)
        singular.length > 3 && singular.endsWith("y") && singular[singular.length - 2] !in VOWELS ->
            singular.dropLast(1) + "i"
        else -> singular
    }
}

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
private val PLURAL_ES = listOf("oes", "ches", "shes", "xes", "sses", "zes")
private val KEEP_S = listOf("ss", "us", "is")
private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')

// Folded like the names they are compared with ("pieces" and "piece" both match).
private val FILLER_WORDS = listOf(
    "and", "with", "the", "for", "from", "fresh", "free", "homemade", "home", "made",
    "leftover", "extra", "some", "pack", "pcs", "piece", "box", "kilo", "per", "each",
    "assorted", "mixed", "food", "meal", "good", "new", "today", "only",
    "mga", "ang", "may", "lang", "pang"
).mapTo(HashSet(), ::foldPlural)

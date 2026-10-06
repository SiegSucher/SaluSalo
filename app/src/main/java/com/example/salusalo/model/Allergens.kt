package com.example.salusalo.model

/**
 * The allergens a donor picks on Post and Edit, and a person picks in My
 * allergies. [label] is what the listing's `allergens` text stores, so it
 * must never change; [examples] is shown next to it in My allergies.
 */
enum class Allergen(val label: String, val examples: String? = null) {
    MILK("Milk"),
    EGGS("Eggs"),
    FISH("Fish", "patis, fish bagoong, tuyo"),
    SHELLFISH("Shellfish", "shrimp, crab, squid, clams, alamang"),
    PEANUTS("Peanuts"),
    TREE_NUTS("Tree nuts", "cashews, almonds, pili nuts"),
    WHEAT("Wheat/gluten"),
    SOY("Soy"),
    SESAME("Sesame")
}

/** A listing's allergens as the chips wrote them: none picked and no [other] means "None". */
data class ListingAllergens(val allergens: Set<Allergen>, val other: String?)

const val NO_ALLERGENS = "None"
private const val OTHER_PREFIX = "Other:"

/**
 * What the chips store in `food_posts.allergens`: "None", or the picked labels
 * in list order, optionally ending with "Other: <text>" (always last, so the
 * text may contain commas).
 */
fun allergenText(picked: Set<Allergen>, other: String?): String {
    val parts = Allergen.entries.filter { it in picked }.map { it.label } +
            listOfNotNull(other?.trim()?.takeIf { it.isNotEmpty() }?.let { "$OTHER_PREFIX $it" })
    return parts.joinToString(", ").ifEmpty { NO_ALLERGENS }
}

/**
 * The chips back from a listing's text; null when the text isn't in that form:
 * blank, or typed before the chips existed ("Contains gatas"). Such text is
 * never matched against anyone's allergies, so it is never called safe.
 */
fun parseAllergens(text: String?): ListingAllergens? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.equals(NO_ALLERGENS, ignoreCase = true)) return ListingAllergens(emptySet(), null)
    val otherAt = trimmed.indexOf(OTHER_PREFIX, ignoreCase = true)
    val listed = if (otherAt >= 0) trimmed.substring(0, otherAt).trimEnd() else trimmed
    // "Other:" is first or follows ", ".
    if (otherAt > 0 && !listed.endsWith(",")) return null
    val other = if (otherAt >= 0) trimmed.substring(otherAt + OTHER_PREFIX.length).trim().ifEmpty { null } else null
    val names = listed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (names.isEmpty() && other == null) return null
    val picked = names.map { name ->
        Allergen.entries.firstOrNull { it.label.equals(name, ignoreCase = true) } ?: return null
    }
    return ListingAllergens(picked.toSet(), other)
}

/** The [allergies] a listing's text says it contains, in list order; none for text that can't be checked. */
fun allergiesIn(text: String?, allergies: Set<Allergen>): List<Allergen> {
    if (allergies.isEmpty()) return emptyList()
    val listed = parseAllergens(text)?.allergens ?: return emptyList()
    return Allergen.entries.filter { it in listed && it in allergies }
}

/** "Milk", "Milk and Eggs", "Milk, Eggs and Fish". */
fun allergenNames(allergens: List<Allergen>): String {
    val labels = allergens.map { it.label }
    return if (labels.size <= 1) labels.joinToString() else labels.dropLast(1).joinToString(", ") + " and " + labels.last()
}

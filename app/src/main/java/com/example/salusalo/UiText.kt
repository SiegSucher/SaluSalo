package com.example.salusalo

import com.example.salusalo.model.FoodPost
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Short, plain-English labels for times and reasons shown on cards. They are
// worked out from the listing's own timestamps and the user's own saved
// preferences only; nothing here is invented.

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * "Expires in 45 min", "Expires in 5 h", "Expires tomorrow" or "Expires Sep 30";
 * "Expired" once it has passed; null when the time is unknown.
 */
internal fun expiryLabel(expiration: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? {
    expiration ?: return null
    if (!expiration.isAfter(now)) return "Expired"
    val minutes = Duration.between(now, expiration).toMinutes()
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), expiration.atZone(zone).toLocalDate())
    return when {
        minutes < 60 -> "Expires in ${minutes.coerceAtLeast(1)} min"
        minutes < 24 * 60 -> "Expires in ${minutes / 60} h"
        days == 1L -> "Expires tomorrow"
        else -> "Expires " + shortDate.format(expiration.atZone(zone))
    }
}

/** "Just posted", "Posted 12 min ago", "Posted 3 h ago", "Posted yesterday" or "Posted Sep 24"; null when unknown. */
internal fun postedLabel(created: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? {
    created ?: return null
    val minutes = Duration.between(created, now).toMinutes()
    val days = java.time.temporal.ChronoUnit.DAYS.between(created.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    return when {
        minutes < 2 -> "Just posted"
        minutes < 60 -> "Posted $minutes min ago"
        days == 0L -> "Posted ${minutes / 60} h ago"
        days == 1L -> "Posted yesterday"
        else -> "Posted " + shortDate.format(created.atZone(zone))
    }
}

/**
 * Why a listing is in "For your preferences", from the choices saved in Food
 * preferences and Location & distance; null when no preference is set.
 */
internal fun DiscoveryPreferences.reasonFor(post: FoodPost): String? {
    if (!isActive) return null
    val category = categories.firstOrNull { it.equals(post.category, ignoreCase = true) }
    return when {
        category != null && !category.equals("Other", ignoreCase = true) ->
            "Because you like ${category.lowercase(Locale.ENGLISH)}"
        category != null -> "Matches your food preferences"
        area.isNotBlank() -> "Pickup in ${area.trim()}, your area"
        listingType == "Donations" -> "Free food, as you prefer"
        listingType == "For sale" -> "For sale, as you prefer"
        else -> "Matches your preferences"
    }
}

/** Initials for an avatar or review row: "Ana Cruz" -> "AC". */
internal fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }

/** The unit one price is for: "packs" -> "pack", "pcs" -> "pc"; other units unchanged. */
internal fun unitSingular(unit: String): String = when (unit.trim().lowercase(Locale.ENGLISH)) {
    "pcs" -> "pc"
    "packs" -> "pack"
    "meals" -> "meal"
    "boxes" -> "box"
    else -> unit.trim()
}

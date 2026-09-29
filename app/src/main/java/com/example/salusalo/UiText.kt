package com.example.salusalo

import com.example.salusalo.model.FoodPost
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

private val clockTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dateWithYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val relativeDays = listOf("Today", "Tomorrow", "Yesterday")

/** "Today", "Tomorrow", "Yesterday", "Sep 24", or "Sep 24, 2025" in another year. */
internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> (if (date.year == today.year) shortDate else dateWithYear).format(date)
}

/**
 * A listing's pickup window: "Today, 4:00 PM – 6:00 PM", "Oct 1, 9:00 AM – 11:00 AM",
 * or with both days when it spans two ("Today, 8:00 PM – Tomorrow, 9:00 AM");
 * null when the window is not set.
 */
internal fun pickupWindowLabel(start: Instant?, end: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (start == null || end == null) return null
    val today = now.atZone(zone).toLocalDate()
    val from = start.atZone(zone)
    val to = end.atZone(zone)
    val opening = dayLabel(from.toLocalDate(), today) + ", " + clockTime.format(from)
    return if (from.toLocalDate() == to.toLocalDate()) {
        opening + " – " + clockTime.format(to)
    } else {
        opening + " – " + dayLabel(to.toLocalDate(), today) + ", " + clockTime.format(to)
    }
}

/** "Pickup today, 4:00 PM – 6:00 PM" or "Pickup Oct 1, …" for a card; null when the window is not set. */
internal fun pickupLine(start: Instant?, end: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? {
    val window = pickupWindowLabel(start, end, now, zone) ?: return null
    val relative = relativeDays.any { window.startsWith(it) }
    return "Pickup " + if (relative) window.replaceFirstChar { it.lowercase() } else window
}

/** The day of a past event for a history row ("Today", "Sep 24"); null when unknown. */
internal fun historyDayLabel(instant: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? =
    instant?.let { dayLabel(it.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate()) }

/** A moment as "Today, 3:12 PM" or "Sep 24, 9:05 AM"; null when unknown. */
internal fun momentLabel(instant: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? =
    instant?.let {
        val at = it.atZone(zone)
        dayLabel(at.toLocalDate(), now.atZone(zone).toLocalDate()) + ", " + clockTime.format(at)
    }

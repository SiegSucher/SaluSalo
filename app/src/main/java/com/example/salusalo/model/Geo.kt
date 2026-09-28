package com.example.salusalo.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A map position in degrees (WGS 84): a listing's pickup pin, or the phone's position. */
data class GeoPin(val latitude: Double, val longitude: Double) {
    /** A real map position; the database refuses anything else. */
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

    /** Rounded to 6 decimals (about 0.1 m), as food_posts stores it. */
    fun rounded(): GeoPin = GeoPin(roundTo6(latitude), roundTo6(longitude))

    /** "9.647812,123.854012": always with a dot and 6 decimals, for map links. */
    fun toQueryText(): String = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
}

private fun roundTo6(value: Double): Double =
    BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).toDouble()

/** The listing's pickup pin, or null for listings without one (older listings). */
val FoodPost.pickupPin: GeoPin?
    get() {
        val latitude = pickupLatitude ?: return null
        val longitude = pickupLongitude ?: return null
        return GeoPin(latitude, longitude).takeIf { it.isValid }
    }

/** The Nearby chip shows pinned listings at most this far away, in a straight line. */
const val NEARBY_RADIUS_METERS = 5_000.0

/** Where a map starts without a pin or a known phone position: Tagbilaran City, Bohol. */
val DEFAULT_MAP_CENTER = GeoPin(9.6500, 123.8540)
const val DEFAULT_MAP_ZOOM = 12.0

/** Zoom for a pin or the phone's position: streets and buildings are readable. */
const val PIN_MAP_ZOOM = 16.0

private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Straight-line (great-circle) distance in meters, by the haversine formula. */
fun distanceMeters(from: GeoPin, to: GeoPin): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
}

/**
 * A distance for people: "350 m" under 1 km (in steps of 10 m, at least 10 m),
 * "1.2 km" under 10 km, and "18 km" above. Always with a dot.
 */
fun distanceLabel(meters: Double): String {
    val tens = (meters / 10).roundToInt().coerceAtLeast(1) * 10
    if (tens < 1_000) return "$tens m"
    val tenths = (meters / 100).roundToInt()
    if (tenths < 100) return "${tenths / 10}.${tenths % 10} km"
    return "${(meters / 1_000).roundToInt()} km"
}

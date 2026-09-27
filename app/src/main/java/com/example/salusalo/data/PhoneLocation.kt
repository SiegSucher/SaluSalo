package com.example.salusalo.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.salusalo.model.GeoPin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** What a phone location lookup found. */
sealed interface PhoneLocationResult {
    /**
     * The phone's position. [approximate] when the phone shared only its
     * approximate location, or the fix is less precise than
     * [PhoneLocation.PRECISE_ENOUGH_METERS].
     */
    data class Found(val position: GeoPin, val approximate: Boolean) : PhoneLocationResult

    /** Location access is not allowed for the app. */
    data object NoPermission : PhoneLocationResult

    /** Location is turned off for the whole phone. */
    data object LocationOff : PhoneLocationResult

    /** No position within the time limit. */
    data object NotFound : PhoneLocationResult
}

/**
 * The phone's own location, foreground only, through the platform LocationManager
 * (via androidx.core; no location library). The result is only returned to the
 * caller: it is never logged, stored or sent anywhere. Callers keep it in memory
 * to compute distances; the only way a position leaves the phone is when a donor
 * taps "Use my location" and then saves the pin with a listing.
 */
object PhoneLocation {
    /** Asked for together: Android 12+ then lets the user choose Precise or Approximate. */
    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** A fix less precise than this counts as approximate (no pin is placed from it). */
    const val PRECISE_ENOUGH_METERS = 100f

    /** For distances: a position up to 10 minutes old is good enough. */
    const val DISTANCE_MAX_AGE_MILLIS = 10 * 60 * 1000L

    /** For the pickup pin: a fresh fix, or one at most 2 minutes old if none comes. */
    const val PIN_MAX_AGE_MILLIS = 2 * 60 * 1000L

    private const val TIMEOUT_MILLIS = 20_000L

    fun hasPermission(context: Context): Boolean = hasFine(context) || hasCoarse(context)

    private fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasCoarse(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * The newest position the phone already has, at most [maxAgeMillis] old,
     * without asking the system for a new one. Null without permission.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context, maxAgeMillis: Long): PhoneLocationResult.Found? {
        if (!hasPermission(context)) return null
        val manager = manager(context) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val newest = providers(context, manager, includePassive = true)
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .filter { ageMillis(it) <= maxAgeMillis }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?: return null
        return newest.toFound(context)
    }

    /**
     * The phone's position: the last known one if it is at most [maxAgeMillis]
     * old, otherwise a fresh fix (waiting up to 20 seconds). With
     * [preferFresh], a fresh fix is tried first and the last known one is only
     * the fallback. Never asks for permission; see [PERMISSIONS].
     */
    @SuppressLint("MissingPermission")
    suspend fun find(context: Context, maxAgeMillis: Long, preferFresh: Boolean = false): PhoneLocationResult {
        if (!hasPermission(context)) return PhoneLocationResult.NoPermission
        val manager = manager(context) ?: return PhoneLocationResult.NotFound
        if (!LocationManagerCompat.isLocationEnabled(manager)) return PhoneLocationResult.LocationOff

        if (!preferFresh) lastKnown(context, maxAgeMillis)?.let { return it }
        val provider = providers(context, manager, includePassive = false).firstOrNull()
            ?: return lastKnown(context, maxAgeMillis) ?: PhoneLocationResult.NotFound
        val fresh = try {
            withTimeoutOrNull(TIMEOUT_MILLIS) { currentLocation(context, manager, provider) }
        } catch (_: SecurityException) {
            return PhoneLocationResult.NoPermission
        }
        return fresh?.toFound(context)
            ?: lastKnown(context, maxAgeMillis)
            ?: PhoneLocationResult.NotFound
    }

    /** One fix from [provider]; the request is cancelled if the caller stops waiting. */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context, manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val mainThread: Executor = ContextCompat.getMainExecutor(context)
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, mainThread) { location: Location? ->
                if (continuation.isActive) continuation.resume(location)
            }
        }

    /** Enabled providers the app may use, best first: fused (Android 12+), network, GPS. */
    private fun providers(context: Context, manager: LocationManager, includePassive: Boolean): List<String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            // GPS needs precise access.
            if (hasFine(context)) add(LocationManager.GPS_PROVIDER)
            if (includePassive) add(LocationManager.PASSIVE_PROVIDER)
        }
        return candidates.filter { provider ->
            try {
                LocationManagerCompat.hasProvider(manager, provider) &&
                        (provider == LocationManager.PASSIVE_PROVIDER || manager.isProviderEnabled(provider))
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun Location.toFound(context: Context): PhoneLocationResult.Found {
        val precise = hasFine(context) && hasAccuracy() && accuracy <= PRECISE_ENOUGH_METERS
        return PhoneLocationResult.Found(GeoPin(latitude, longitude), approximate = !precise)
    }
}

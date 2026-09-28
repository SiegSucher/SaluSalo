package com.example.salusalo

import com.example.salusalo.model.PICKUP_START_SLACK_MILLIS
import com.example.salusalo.model.isPickupStartAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule Post's check and "Check your listing" share for a pickup start in the past. */
class PickupStartTest {
    private val now = 1_790_000_000_000L
    private val minute = 60_000L

    @Test
    fun theSlackIsTenMinutes() {
        assertEquals(10 * minute, PICKUP_START_SLACK_MILLIS)
    }

    @Test
    fun aFutureStartIsAllowed() {
        assertTrue(isPickupStartAllowed(now + minute, now))
    }

    @Test
    fun startNowIsAllowed() {
        // "Start now" rounds down to the minute, so it can be up to a minute early.
        assertTrue(isPickupStartAllowed(now, now))
        assertTrue(isPickupStartAllowed(now - 59_000L, now))
    }

    @Test
    fun startNowStillPassesTenMinutesLater() {
        assertTrue(isPickupStartAllowed(now - 10 * minute, now))
    }

    @Test
    fun aStartMoreThanTenMinutesAgoIsRefused() {
        assertFalse(isPickupStartAllowed(now - 10 * minute - 1, now))
        assertFalse(isPickupStartAllowed(now - 60 * minute, now))
    }

    @Test
    fun anUnsetStartIsRefused() {
        assertFalse(isPickupStartAllowed(0L, now))
    }
}

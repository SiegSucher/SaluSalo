package com.example.salusalo

import com.example.salusalo.model.pickupDeadlineEstimate
import com.example.salusalo.model.reservableNow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The phone's deadline estimate and "how many can you still reserve" follow the server's rules. */
class ReservationLimitsTest {
    private val now = Instant.parse("2026-10-03T04:00:00Z")
    private val expiry = Instant.parse("2026-10-04T04:00:00Z")
    private fun at(minutesFromNow: Long) = now.plusSeconds(minutesFromNow * 60)

    @Test
    fun theHoldStartsNowWhenPickupHasStarted() {
        assertEquals(at(30), pickupDeadlineEstimate(now, at(-60), at(180), expiry, 30))
    }

    @Test
    fun theHoldStartsAtPickupStartWhenReservingAhead() {
        assertEquals(at(120), pickupDeadlineEstimate(now, at(60), at(180), expiry, 60))
    }

    @Test
    fun theDeadlineNeverPassesTheEndOfPickup() {
        assertEquals(at(10), pickupDeadlineEstimate(now, at(-60), at(10), expiry, 120))
    }

    @Test
    fun withoutAPickupWindowTheExpiryCapsIt() {
        assertEquals(at(20), pickupDeadlineEstimate(now, null, null, at(20), 60))
        assertEquals(at(15), pickupDeadlineEstimate(now, null, null, expiry, 15))
    }

    @Test
    fun withoutALimitEverythingLeftCanBeReserved() {
        assertEquals(7, reservableNow(limit = null, available = 7, held = 5))
    }

    @Test
    fun theLimitCountsWhatThePersonAlreadyHolds() {
        assertEquals(1, reservableNow(limit = 3, available = 7, held = 2))
        assertEquals(3, reservableNow(limit = 3, available = 7, held = 0))
        assertEquals(2, reservableNow(limit = 3, available = 2, held = 0))
    }

    @Test
    fun atOrOverTheLimitNothingIsLeft() {
        assertEquals(0, reservableNow(limit = 3, available = 7, held = 3))
        assertEquals(0, reservableNow(limit = 3, available = 7, held = 4))
        assertEquals(0, reservableNow(limit = null, available = 0, held = 0))
    }

    private val manila = ZoneId.of("Asia/Manila")   // now = 12:00 PM there

    @Test
    fun pickUpByShowsTheTimeTodayAndTheDayOtherwise() {
        assertEquals("12:30 PM", pickUpByTime(at(30), now, manila))
        assertEquals("Tomorrow, 9:00 AM", pickUpByTime(Instant.parse("2026-10-04T01:00:00Z"), now, manila))
    }

    @Test
    fun theEstimateSaysWhyTheDeadlineIsWhatItIs() {
        assertEquals("Pick up by 12:30 PM.", holdEstimateText(at(30), at(-60), at(180), expiry, now, manila))
        assertEquals(
            "Pickup starts at 1:00 PM, so pick up by 2:00 PM.",
            holdEstimateText(at(120), at(60), at(180), expiry, now, manila)
        )
        assertEquals("Pick up by 12:10 PM, when pickup ends.", holdEstimateText(at(10), at(-60), at(10), expiry, now, manila))
        assertEquals("Pick up by 12:20 PM, when the food expires.", holdEstimateText(at(20), null, null, at(20), now, manila))
    }

    @Test
    fun theActivityLineCountsDownAndThenSaysTimesUp() {
        assertEquals("Pick up by 12:30 PM · 30 min left", deadlineLine(at(30), at(-60), now, manila))
        assertEquals("Pick up by 1:30 PM · 1 h 30 min left", deadlineLine(at(90), at(-60), now, manila))
        assertEquals("Pick up by 12:00 PM · less than a minute left", deadlineLine(now.plusSeconds(20), null, now, manila))
        assertEquals("Pick up by 2:00 PM · pickup starts 1:00 PM", deadlineLine(at(120), at(60), now, manila))
        assertEquals("Pick up by 11:59 AM · Time's up", deadlineLine(at(-1), null, now, manila))
    }

    @Test
    fun underTenMinutesIsUrgent() {
        assertEquals(true, deadlineUrgent(at(9), now))
        assertEquals(false, deadlineUrgent(at(11), now))
        assertEquals(true, deadlineUrgent(at(-1), now))
    }
}

package com.example.salusalo

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.canBeReserved
import com.example.salusalo.model.isActiveListing
import com.example.salusalo.model.nextReserveBlockerChange
import com.example.salusalo.model.reserveBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The details page offers Reserve only when create_reservation would accept it. */
class ReserveBlockerTest {
    private val now = Instant.parse("2026-09-29T12:00:00Z")

    private fun post(
        status: FoodPostStatus = FoodPostStatus.AVAILABLE,
        quantity: Int = 2,
        expires: String = "2026-09-30T12:00:00Z",
        pickupEnd: String? = "2026-09-29T15:00:00Z"
    ) = FoodPost(
        id = "p", donorId = "d", name = "Rice", category = "Meals", quantity = quantity, unit = "packs",
        pickupLocation = "Plaza", expirationDate = expires, status = status, pickupEnd = pickupEnd
    )

    @Test
    fun anAvailableListingCanBeReserved() {
        assertNull(post().reserveBlocker(now))
        assertNull(post(pickupEnd = null).reserveBlocker(now))
    }

    @Test
    fun eachRefusalHasItsReason() {
        assertEquals("The donor removed this listing.", post(status = FoodPostStatus.REMOVED).reserveBlocker(now))
        assertEquals("This listing has expired.", post(expires = "2026-09-29T12:00:00Z").reserveBlocker(now))
        assertEquals(
            "The pickup time for this listing has ended.",
            post(pickupEnd = "2026-09-29T11:59:00Z").reserveBlocker(now)
        )
        assertEquals(
            "Fully reserved: nothing is left to reserve.",
            post(status = FoodPostStatus.RESERVED, quantity = 0).reserveBlocker(now)
        )
    }

    /** An open details page re-checks when pickup ends or the listing expires, not only when reloaded. */
    @Test
    fun theNextCheckIsTheFirstBoundaryStillAhead() {
        assertEquals(Instant.parse("2026-09-29T15:00:00Z"), post().nextReserveBlockerChange(now))
        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), post(pickupEnd = null).nextReserveBlockerChange(now))
        assertEquals(
            Instant.parse("2026-09-30T12:00:00Z"),
            post(pickupEnd = "2026-09-29T11:00:00Z").nextReserveBlockerChange(now)
        )
        // Once both have passed, nothing is left to wait for.
        assertNull(post(expires = "2026-09-29T11:00:00Z", pickupEnd = null).nextReserveBlockerChange(now))
        // At the boundary itself the listing already counts as ended (reserveBlocker uses !isAfter).
        val atEnd = post(pickupEnd = "2026-09-29T12:00:00Z")
        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), atEnd.nextReserveBlockerChange(now))
        assertEquals("The pickup time for this listing has ended.", atEnd.reserveBlocker(now))
    }

    /** Saved Listings and the records offer "View listing" only for what Home would offer Reserve on (#41). */
    @Test
    fun aListingWhosePickupEndedCannotBeReserved() {
        assertTrue(post().canBeReserved(now))
        assertFalse(post(pickupEnd = "2026-09-29T11:59:00Z").canBeReserved(now))
        assertFalse(post(expires = "2026-09-29T11:00:00Z", pickupEnd = null).canBeReserved(now))
    }

    /** Activity's Active filter: can still be reserved, or still has food to hand over (#41). */
    @Test
    fun aListingIsActiveWhileReservableOrWithFoodToHandOver() {
        val ended = post(pickupEnd = "2026-09-29T11:59:00Z")
        assertTrue(post().isActiveListing(hasOpenReservations = false, reservationsKnown = true, now = now))
        // Pickup over and nothing left to hand over: Closed, as on Home.
        assertFalse(ended.isActiveListing(hasOpenReservations = false, reservationsKnown = true, now = now))
        assertTrue(ended.isActiveListing(hasOpenReservations = true, reservationsKnown = true, now = now))
        // Fully reserved: active until the reservations show nothing is open.
        val full = post(status = FoodPostStatus.RESERVED, quantity = 0)
        assertTrue(full.isActiveListing(hasOpenReservations = false, reservationsKnown = false, now = now))
        assertFalse(full.isActiveListing(hasOpenReservations = false, reservationsKnown = true, now = now))
    }
}

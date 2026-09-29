package com.example.salusalo

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.reserveBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

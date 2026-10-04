package com.example.salusalo

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.RecommendationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The card labels in UiText.kt: times, recommendation reasons, units and initials. */
class UiTextTest {
    private val zone = ZoneId.of("Asia/Manila")

    // 12:00 PM on Sep 28, 2026 in Manila.
    private val now = Instant.parse("2026-09-28T04:00:00Z")

    private fun post(category: String = "Vegetables", price: Double = 0.0, location: String = "Poblacion, Balilihan") =
        FoodPost(
            id = "p1",
            donorId = "d1",
            name = "Pechay",
            category = category,
            quantity = 3,
            unit = "packs",
            pickupLocation = location,
            expirationDate = "2026-09-29T04:00:00Z",
            price = price
        )

    @Test
    fun expiryLabelUsesMinutesHoursTomorrowOrDate() {
        assertEquals("Expires in 1 min", expiryLabel(now.plusSeconds(20), now, zone))
        assertEquals("Expires in 45 min", expiryLabel(now.plusSeconds(45 * 60), now, zone))
        assertEquals("Expires in 5 h", expiryLabel(now.plusSeconds(5 * 3600 + 59 * 60), now, zone))
        assertEquals("Expires tomorrow", expiryLabel(now.plusSeconds(25 * 3600), now, zone))
        assertEquals("Expires Oct 1", expiryLabel(Instant.parse("2026-10-01T02:00:00Z"), now, zone))
    }

    @Test
    fun expiryLabelSaysExpiredOrNothing() {
        assertEquals("Expired", expiryLabel(now, now, zone))
        assertEquals("Expired", expiryLabel(now.minusSeconds(60), now, zone))
        assertNull(expiryLabel(null, now, zone))
    }

    @Test
    fun postedLabelCountsBackFromNow() {
        assertEquals("Just posted", postedLabel(now.minusSeconds(30), now, zone))
        assertEquals("Posted 12 min ago", postedLabel(now.minusSeconds(12 * 60), now, zone))
        assertEquals("Posted 3 h ago", postedLabel(now.minusSeconds(3 * 3600), now, zone))
        assertEquals("Posted yesterday", postedLabel(Instant.parse("2026-09-27T09:00:00Z"), now, zone))
        assertEquals("Posted Sep 24", postedLabel(Instant.parse("2026-09-24T09:00:00Z"), now, zone))
        assertNull(postedLabel(null, now, zone))
    }

    @Test
    fun reasonComesOnlyFromSavedPreferences() {
        assertNull(DiscoveryPreferences().reasonFor(post()))
        assertEquals(
            "Because you like vegetables",
            DiscoveryPreferences(categories = setOf("Vegetables", "Fruit")).reasonFor(post())
        )
        assertEquals(
            "Matches your food preferences",
            DiscoveryPreferences(categories = setOf("Other")).reasonFor(post(category = "Other"))
        )
        assertEquals(
            "Pickup in Balilihan, your area",
            DiscoveryPreferences(area = " Balilihan ").reasonFor(post())
        )
        assertEquals(
            "Free food, as you prefer",
            DiscoveryPreferences(listingType = "Donations").reasonFor(post())
        )
        assertEquals(
            "For sale, as you prefer",
            DiscoveryPreferences(listingType = "For sale").reasonFor(post(price = 25.0))
        )
    }

    @Test
    fun recommendationReasonSaysWhatTheUserReservedOrSaved() {
        fun label(kind: RecommendationReason.Kind, reserved: Boolean, category: String? = null) =
            recommendationLabel(RecommendationReason(kind, reserved, category))
        assertEquals("Like the cooked meals you reserved", label(RecommendationReason.Kind.CATEGORY, true, "Cooked meals"))
        assertEquals("Like the bread & pastries you saved", label(RecommendationReason.Kind.CATEGORY, false, " Bread & pastries "))
        assertEquals("Similar to food you saved", label(RecommendationReason.Kind.CATEGORY, false, "Other"))
        assertEquals("Free food, like what you reserved", label(RecommendationReason.Kind.FREE, true))
        assertEquals("For sale, like what you saved", label(RecommendationReason.Kind.FOR_SALE, false))
        assertEquals("From a donor you reserved from", label(RecommendationReason.Kind.DONOR, true))
        assertEquals("From a donor whose food you saved", label(RecommendationReason.Kind.DONOR, false))
        assertEquals("Similar to food you reserved", label(RecommendationReason.Kind.SIMILAR, true))
    }

    @Test
    fun unitAndInitials() {
        assertEquals("pack", unitSingular("packs"))
        assertEquals("pc", unitSingular("pcs"))
        assertEquals("box", unitSingular("Boxes"))
        assertEquals("kg", unitSingular("kg"))
        assertEquals("AC", initialsOf("  Ana   Cruz "))
        assertEquals("JD", initialsOf("juan dela cruz"))
        assertEquals("", initialsOf("   "))
    }

    @Test
    fun pickupWindowNamesTheDayAndBothTimes() {
        // 4:00 PM – 6:00 PM today in Manila.
        assertEquals(
            "Today, 4:00 PM – 6:00 PM",
            pickupWindowLabel(Instant.parse("2026-09-28T08:00:00Z"), Instant.parse("2026-09-28T10:00:00Z"), now, zone)
        )
        assertEquals(
            "Tomorrow, 9:00 AM – 11:00 AM",
            pickupWindowLabel(Instant.parse("2026-09-29T01:00:00Z"), Instant.parse("2026-09-29T03:00:00Z"), now, zone)
        )
        assertEquals(
            "Oct 1, 9:00 AM – 11:00 AM",
            pickupWindowLabel(Instant.parse("2026-10-01T01:00:00Z"), Instant.parse("2026-10-01T03:00:00Z"), now, zone)
        )
        // Across midnight: both days.
        assertEquals(
            "Today, 8:00 PM – Tomorrow, 9:00 AM",
            pickupWindowLabel(Instant.parse("2026-09-28T12:00:00Z"), Instant.parse("2026-09-29T01:00:00Z"), now, zone)
        )
        assertNull(pickupWindowLabel(null, Instant.parse("2026-09-28T10:00:00Z"), now, zone))
        assertNull(pickupWindowLabel(Instant.parse("2026-09-28T08:00:00Z"), null, now, zone))
    }

    @Test
    fun pickupLineReadsAsASentence() {
        assertEquals(
            "Pickup today, 4:00 PM – 6:00 PM",
            pickupLine(Instant.parse("2026-09-28T08:00:00Z"), Instant.parse("2026-09-28T10:00:00Z"), now, zone)
        )
        assertEquals(
            "Pickup Oct 1, 9:00 AM – 11:00 AM",
            pickupLine(Instant.parse("2026-10-01T01:00:00Z"), Instant.parse("2026-10-01T03:00:00Z"), now, zone)
        )
        assertNull(pickupLine(null, null, now, zone))
    }

    @Test
    fun historyDayIsRelativeThenDated() {
        assertEquals("Today", historyDayLabel(Instant.parse("2026-09-28T01:00:00Z"), now, zone))
        assertEquals("Yesterday", historyDayLabel(Instant.parse("2026-09-27T09:00:00Z"), now, zone))
        assertEquals("Sep 24", historyDayLabel(Instant.parse("2026-09-24T09:00:00Z"), now, zone))
        assertEquals("Dec 31, 2025", historyDayLabel(Instant.parse("2025-12-31T09:00:00Z"), now, zone))
        assertNull(historyDayLabel(null, now, zone))
    }

    @Test
    fun momentHasTheDayAndTheTime() {
        assertEquals("Today, 3:12 PM", momentLabel(Instant.parse("2026-09-28T07:12:00Z"), now, zone))
        assertEquals("Tomorrow, 9:00 AM", momentLabel(Instant.parse("2026-09-29T01:00:00Z"), now, zone))
        assertEquals("Sep 24, 9:05 AM", momentLabel(Instant.parse("2026-09-24T01:05:00Z"), now, zone))
        assertNull(momentLabel(null, now, zone))
    }
}

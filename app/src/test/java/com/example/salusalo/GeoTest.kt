package com.example.salusalo

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.GeoPin
import com.example.salusalo.model.NEARBY_RADIUS_METERS
import com.example.salusalo.model.NewFoodPost
import com.example.salusalo.model.distanceLabel
import com.example.salusalo.model.distanceMeters
import com.example.salusalo.model.pickupPin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Distances, distance texts and the pickup pin fields (Phase 5 part 2). */
class GeoTest {
    private val tagbilaran = GeoPin(9.6500, 123.8540)

    // Like supabase-kt's default: unknown keys ignored, values equal to their default left out.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun distanceIsZeroForTheSamePoint() {
        assertEquals(0.0, distanceMeters(tagbilaran, tagbilaran), 1e-9)
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        val north = GeoPin(10.6500, 123.8540)
        assertEquals(111_194.9, distanceMeters(tagbilaran, north), 1.0)
        assertEquals(distanceMeters(tagbilaran, north), distanceMeters(north, tagbilaran), 1e-6)
    }

    @Test
    fun oneDegreeOfLongitudeAtTheEquatorIsAbout111Km() {
        assertEquals(111_194.9, distanceMeters(GeoPin(0.0, 123.0), GeoPin(0.0, 124.0)), 1.0)
    }

    @Test
    fun nearbyRadiusSeparatesCloseAndFarListings() {
        // 0.04 degrees of latitude is about 4.4 km; 0.05 about 5.6 km.
        assertTrue(distanceMeters(tagbilaran, GeoPin(9.6900, 123.8540)) <= NEARBY_RADIUS_METERS)
        assertFalse(distanceMeters(tagbilaran, GeoPin(9.7000, 123.8540)) <= NEARBY_RADIUS_METERS)
    }

    @Test
    fun distanceLabels() {
        assertEquals("10 m", distanceLabel(0.0))
        assertEquals("10 m", distanceLabel(4.0))
        assertEquals("350 m", distanceLabel(346.0))
        assertEquals("990 m", distanceLabel(994.0))
        assertEquals("1.0 km", distanceLabel(995.0))
        assertEquals("1.2 km", distanceLabel(1_234.0))
        assertEquals("9.9 km", distanceLabel(9_949.0))
        assertEquals("10 km", distanceLabel(9_950.0))
        assertEquals("18 km", distanceLabel(18_400.0))
    }

    @Test
    fun labelsAndLinksUseADotInEveryLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.2 km", distanceLabel(1_234.0))
            assertEquals("9.647812,123.854012", GeoPin(9.647812, 123.854012).toQueryText())
            assertEquals("-9.500000,-123.000000", GeoPin(-9.5, -123.0).toQueryText())
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun pinsAreRoundedToSixDecimals() {
        assertEquals(GeoPin(9.647813, 123.854013), GeoPin(9.6478125, 123.8540126).rounded())
    }

    @Test
    fun onlyRealMapPositionsAreValid() {
        assertTrue(GeoPin(-90.0, -180.0).isValid)
        assertTrue(GeoPin(90.0, 180.0).isValid)
        assertFalse(GeoPin(90.5, 123.0).isValid)
        assertFalse(GeoPin(9.6, 180.5).isValid)
        assertFalse(GeoPin(Double.NaN, 123.0).isValid)
    }

    @Test
    fun aListingHasAPinOnlyWithBothCoordinates() {
        val post = FoodPost(
            id = "p", donorId = "d", name = "Bread", category = "Bakery", quantity = 1, unit = "pcs",
            pickupLocation = "Plaza", expirationDate = "2026-10-01T00:00:00Z"
        )
        assertNull(post.pickupPin)
        assertNull(post.copy(pickupLatitude = 9.65).pickupPin)
        assertEquals(GeoPin(9.65, 123.854), post.copy(pickupLatitude = 9.65, pickupLongitude = 123.854).pickupPin)
    }

    @Test
    fun aListingDecodesWithAndWithoutAPin() {
        val row = """{"id":"p","donor_id":"d","name":"Bread","category":"Bakery","quantity":1,"unit":"pcs",""" +
                """"pickup_location":"Plaza","expiration_date":"2026-10-01T00:00:00+00:00","""
        val pinned = json.decodeFromString(FoodPost.serializer(), row + """"pickup_latitude":9.647812,"pickup_longitude":123.854012}""")
        assertEquals(GeoPin(9.647812, 123.854012), pinned.pickupPin)
        val older = json.decodeFromString(FoodPost.serializer(), row + """"pickup_latitude":null,"pickup_longitude":null}""")
        assertNull(older.pickupPin)
        // Rows from before the migration have no pin columns at all.
        val beforeMigration = json.decodeFromString(FoodPost.serializer(), row.dropLast(1) + "}")
        assertNull(beforeMigration.pickupPin)
    }

    @Test
    fun aNewListingAlwaysSendsItsPin() {
        val post = NewFoodPost(
            id = "p", donorId = "d", name = "Bread", category = "Bakery", quantity = 1, unit = "pcs",
            pickupLocation = "Plaza", expirationDate = "2026-10-01T00:00:00Z", price = 0.0,
            pickupStart = null, pickupEnd = null, handover = null, allergens = null, storageNotes = null,
            extraImagePaths = emptyList(), pickupLatitude = 9.647812, pickupLongitude = 123.854012
        )
        val sent = json.encodeToJsonElement(NewFoodPost.serializer(), post).jsonObject
        assertEquals(9.647812, sent.getValue("pickup_latitude").jsonPrimitive.double, 0.0)
        assertEquals(123.854012, sent.getValue("pickup_longitude").jsonPrimitive.double, 0.0)
    }
}

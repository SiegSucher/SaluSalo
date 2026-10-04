package com.example.salusalo

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.ListingRecommender
import com.example.salusalo.model.RecommendationReason
import com.example.salusalo.model.foldPlural
import com.example.salusalo.model.listingFeatures
import com.example.salusalo.model.nameWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Recommended for you": what the on-device model learns from, what it never shows, and why it shows the rest. */
class ListingRecommenderTest {
    private fun post(
        id: String,
        name: String,
        category: String,
        price: Double = 0.0,
        donor: String = "donor-$id",
        created: String = "2026-10-01T00:00:00Z"
    ) = FoodPost(
        id = id, donorId = donor, name = name, category = category, quantity = 3, unit = "packs",
        pickupLocation = "Plaza", expirationDate = "2026-10-09T00:00:00Z", price = price, createdAt = created
    )

    // Today's listings: one of each kind, each from its own donor.
    private val adobo = post("adobo", "Pork adobo", "Cooked meals")
    private val pandesal = post("pandesal", "Pandesal", "Bread & pastries")
    private val mango = post("mango", "Mango", "Fruit", price = 30.0)
    private val noodles = post("noodles", "Instant noodles", "Packaged food", price = 15.0)
    private val pechay = post("pechay", "Pechay", "Vegetables", price = 20.0)
    private val feed = listOf(adobo, pandesal, mango, noodles, pechay)

    // A listing reserved last week, no longer in the feed.
    private val chickenAdobo = post("old-adobo", "Chicken adobo", "Cooked meals", created = "2026-09-24T00:00:00Z")

    private fun train(
        reserved: List<FoodPost> = emptyList(),
        savedIds: Set<String> = emptySet(),
        saved: List<FoodPost> = emptyList(),
        feed: List<FoodPost> = this.feed,
        own: String? = "me"
    ) = ListingRecommender.train(feed, reserved, savedIds, saved, own)

    @Test
    fun withoutReservationsOrSavesThereIsNoModel() {
        assertNull(train())
        // A saved listing whose details can't be loaded gives nothing to learn from.
        assertNull(train(savedIds = setOf("gone")))
    }

    @Test
    fun withNothingElseToShowThereIsNoModel() {
        val mine = post("mine", "Chicken adobo", "Cooked meals", donor = "me")
        assertNull(train(reserved = listOf(adobo), feed = listOf(adobo, mine)))
    }

    @Test
    fun aReservationRanksSimilarFoodFirst() {
        val model = requireNotNull(train(reserved = listOf(chickenAdobo)))
        val picks = model.recommend(feed, limit = 5)
        assertEquals("adobo", picks.first().post.id)
        assertEquals(RecommendationReason(RecommendationReason.Kind.CATEGORY, true, "Cooked meals"), picks.first().reason)
        // Food with nothing in common with the reservation (another category, for sale) is never "recommended".
        assertTrue(picks.none { it.post.id in setOf("mango", "noodles", "pechay") })
        assertTrue(model.score(adobo) > model.score(noodles))
    }

    @Test
    fun ownReservedAndSavedListingsAreNeverRecommended() {
        val mine = post("mine", "Chicken adobo", "Cooked meals", donor = "me")
        val curry = post("curry", "Chicken curry", "Cooked meals")
        val model = requireNotNull(
            train(reserved = listOf(chickenAdobo), savedIds = setOf("adobo"), feed = feed + mine + curry + chickenAdobo)
        )
        val picks = model.recommend(feed + mine + curry + chickenAdobo, limit = 10).map { it.post.id }
        assertEquals("curry", picks.first())
        assertFalse("adobo" in picks)
        assertFalse("mine" in picks)
        assertFalse("old-adobo" in picks)
    }

    @Test
    fun aReservationCountsMoreThanASave() {
        val banana = post("banana", "Banana", "Fruit", price = 25.0, created = "2026-09-20T00:00:00Z")
        val kangkong = post("kangkong", "Kangkong", "Vegetables", price = 30.0)
        val today = listOf(mango, kangkong, noodles, adobo, pechay)
        val model = requireNotNull(train(reserved = listOf(banana), savedIds = setOf("pechay"), feed = today))
        // Fruit was reserved, vegetables only saved; otherwise the two are alike.
        assertTrue(model.score(mango) > model.score(kangkong))
        assertEquals(listOf("mango", "kangkong"), model.recommend(today, limit = 2).map { it.post.id })
    }

    @Test
    fun theReasonSaysWhetherItCameFromASave() {
        val ensaymada = post("ensaymada", "Ensaymada", "Bread & pastries")
        val model = requireNotNull(train(savedIds = setOf("pandesal"), feed = feed + ensaymada))
        val pick = model.recommend(feed + ensaymada, limit = 1).single()
        assertEquals("ensaymada", pick.post.id)
        assertEquals(RecommendationReason(RecommendationReason.Kind.CATEGORY, false, "Bread & pastries"), pick.reason)
    }

    @Test
    fun aDonorReservedFromTwiceIsAReason() {
        val lolaFruit = post("lola-1", "Calamansi", "Fruit", price = 20.0, donor = "lola", created = "2026-09-20T00:00:00Z")
        val lolaGreens = post("lola-2", "Malunggay", "Vegetables", price = 10.0, donor = "lola", created = "2026-09-21T00:00:00Z")
        val lolaBiscuits = post("lola-3", "Biscuits", "Packaged food", donor = "lola")
        val model = requireNotNull(train(reserved = listOf(lolaFruit, lolaGreens), feed = feed + lolaBiscuits))
        // Nothing but the donor links these biscuits to the reservations (other category, free).
        val pick = model.recommend(feed + lolaBiscuits, limit = 10).single { it.post.id == "lola-3" }
        assertEquals(RecommendationReason(RecommendationReason.Kind.DONOR, true), pick.reason)
    }

    @Test
    fun aSaveKnownOnlyFromTheFeedCounts() {
        val curry = post("curry", "Chicken curry", "Cooked meals")
        val model = requireNotNull(train(savedIds = setOf("adobo"), feed = feed + curry))
        val picks = model.recommend(feed + curry, limit = 5)
        assertEquals("curry", picks.first().post.id)
        assertEquals(RecommendationReason(RecommendationReason.Kind.CATEGORY, false, "Cooked meals"), picks.first().reason)
        assertTrue(picks.none { it.post.id == "adobo" })
        assertTrue(picks.all { it.score > 0.0 && it.score < 1.0 })
    }

    @Test
    fun theSameHistoryAlwaysGivesTheSameAnswer() {
        val curry = post("curry", "Chicken curry", "Cooked meals")
        val listings = feed + curry
        val first = requireNotNull(train(reserved = listOf(chickenAdobo, pechay), savedIds = setOf("pandesal", "mango"), feed = listings))
            .recommend(listings, limit = 10)
        val second = requireNotNull(
            train(reserved = listOf(pechay, chickenAdobo), savedIds = setOf("mango", "pandesal"), feed = listings.reversed())
        ).recommend(listings.reversed(), limit = 10)
        assertEquals(first, second)
    }

    @Test
    fun equalScoresGoToTheNewestAndTheLimitHolds() {
        val older = post("rice-1", "Fried rice", "Cooked meals", created = "2026-10-01T08:00:00Z")
        val newer = post("rice-2", "Fried rice", "Cooked meals", created = "2026-10-02T08:00:00Z")
        val listings = listOf(older, newer, noodles, mango)
        val model = requireNotNull(train(reserved = listOf(chickenAdobo), feed = listings))
        assertEquals(model.score(older), model.score(newer), 0.0)
        assertEquals(listOf("rice-2"), model.recommend(listings, limit = 1).map { it.post.id })
        assertEquals(emptyList<Any>(), model.recommend(listings, limit = 0))
    }

    @Test
    fun nameWordsFoldPluralsAndSkipFiller() {
        assertEquals(setOf("tomato", "cooki"), nameWords("Fresh Tomatoes and 12 pcs Cookies"))
        assertEquals(setOf("kare"), nameWords("Kare-kare"))
        assertEquals(setOf("ulam"), nameWords("Mga ulam only"))
        assertEquals(foldPlural("pastry"), foldPlural("pastries"))
        assertEquals(foldPlural("cookie"), foldPlural("cookies"))
        assertEquals(foldPlural("mango"), foldPlural("mangoes"))
        assertEquals("glass", foldPlural("glasses"))
        assertEquals("hummus", foldPlural("hummus"))
        assertEquals("honey", foldPlural("honey"))
    }

    @Test
    fun eachFeatureGroupCountsOnce() {
        val features = listingFeatures(post("x", "Chicken adobo rice", " Cooked Meals ", price = 50.0))
        assertEquals(
            listOf("category:cooked meals", "donor:donor-x", "price:sale", "word:adobo", "word:chicken", "word:rice"),
            features.keys.toList()
        )
        assertEquals(1.0, features.filterKeys { it.startsWith("word:") }.values.sumOf { it * it }, 1e-9)
        assertEquals(1.0, listingFeatures(post("y", "Pandesal", "Bread & pastries"))["price:free"]!!, 0.0)
    }
}

package com.example.salusalo

import com.example.salusalo.model.EXPIRES_SOON_SECONDS
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.GeoPin
import com.example.salusalo.model.ListingVectors
import com.example.salusalo.model.MyReview
import com.example.salusalo.model.RECOMMENDATION_LIMIT
import com.example.salusalo.model.Recommendation
import com.example.salusalo.model.Reservation
import com.example.salusalo.model.ReservationDisplay
import com.example.salusalo.model.ReservationStatus
import com.example.salusalo.model.distanceLabel
import com.example.salusalo.model.distanceMeters
import com.example.salusalo.model.expiresSoon
import com.example.salusalo.model.pickupPin
import com.example.salusalo.model.recommendListings
import com.example.salusalo.model.recommendationWords
import com.example.salusalo.model.shortReasonName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.sqrt

/** Home's "Recommended for you" row: content-based filtering on the phone. */
class RecommendationsTest {
    private val now = Instant.parse("2026-09-28T08:00:00Z")
    private val me = "profile-me"
    private val phone = GeoPin(9.6500, 123.8540)

    private fun post(
        id: String,
        name: String,
        category: String = COOKED,
        donor: String = "donor-a",
        description: String? = null,
        price: Double = 0.0,
        expiresInSeconds: Long = 24 * 60 * 60L,
        createdAt: String? = "2026-09-28T07:00:00Z",
        pin: GeoPin? = null
    ) = FoodPost(
        id = id,
        donorId = donor,
        name = name,
        description = description,
        category = category,
        quantity = 5,
        unit = "packs",
        pickupLocation = "Tagbilaran City",
        expirationDate = now.plusSeconds(expiresInSeconds).toString(),
        price = price,
        createdAt = createdAt,
        pickupLatitude = pin?.latitude,
        pickupLongitude = pin?.longitude
    )

    private fun reserved(
        id: String,
        post: FoodPost,
        status: ReservationStatus = ReservationStatus.RESERVED
    ) = ReservationDisplay(Reservation(id, post.id, me, 1, status, "2026-09-28T07:30:00Z"), post)

    private fun review(reservationId: String, rating: Int, at: String) =
        MyReview(reservationId, rating, createdAt = at, updatedAt = at)

    private fun recommend(
        feed: List<FoodPost>,
        reservations: List<ReservationDisplay> = emptyList(),
        reviews: List<MyReview> = emptyList(),
        saved: Set<String> = emptySet(),
        phonePin: GeoPin? = null,
        include: (FoodPost) -> Boolean = { true }
    ): List<Recommendation>? = recommendListings(
        feed, me, reservations, reviews.associateBy { it.reservationId }, saved, now, phonePin, include
    )

    private fun List<Recommendation>?.ids() = orEmpty().map { it.post.id }
    private fun List<Recommendation>?.reasonOf(id: String) = orEmpty().first { it.post.id == id }.reason

    // The listings of the manual test (§19.4), posted in this order; the feed is newest first.
    private val chickenAdobo = post("chicken-adobo", "Chicken adobo", createdAt = "2026-09-28T07:00:00Z")
    private val porkAdobo = post("pork-adobo", "Pork adobo", createdAt = "2026-09-28T07:01:00Z")
    private val pandesal = post("pandesal", "Pandesal", BREAD, createdAt = "2026-09-28T07:02:00Z")
    private val pechay = post("pechay", "Pechay", VEGETABLES, createdAt = "2026-09-28T07:03:00Z")
    private val kangkong = post("kangkong", "Kangkong", VEGETABLES, createdAt = "2026-09-28T07:04:00Z")
    private val feed = listOf(kangkong, pechay, pandesal, porkAdobo, chickenAdobo)

    @Test
    fun afterReservingChickenAdoboPorkAdoboComesFirst() {
        val result = recommend(feed, listOf(reserved("r1", chickenAdobo)))
        assertEquals(listOf("pork-adobo", "kangkong", "pechay", "pandesal"), result.ids())
        assertEquals("Because you reserved Chicken adobo", result.reasonOf("pork-adobo"))
    }

    @Test
    fun afterSavingPechayKangkongNamesIt() {
        val result = recommend(feed, listOf(reserved("r1", chickenAdobo)), saved = setOf("pechay"))
        assertFalse("pechay" in result.ids())
        assertEquals("Because you saved Pechay", result.reasonOf("kangkong"))
        assertEquals("Because you reserved Chicken adobo", result.reasonOf("pork-adobo"))
    }

    @Test
    fun theSameCategoryRanksAboveAnother() {
        val beefStew = post("beef-stew", "Beef stew")
        val fishFillet = post("fish-fillet", "Fish fillet")
        // Newer and first in the feed, but bread.
        val pandesal = post("pandesal", "Pandesal", BREAD, createdAt = "2026-09-28T07:30:00Z")
        val result = recommend(listOf(pandesal, fishFillet, beefStew), listOf(reserved("r1", beefStew)))
        assertEquals(listOf("fish-fillet", "pandesal"), result.ids())
    }

    @Test
    fun aSharedWordRanksAboveTheSameCategoryAlone() {
        // Newer and first in the feed, but without "adobo".
        val pancit = post("pancit", "Pancit bihon", createdAt = "2026-09-28T07:30:00Z")
        val result = recommend(listOf(pancit, porkAdobo, chickenAdobo), listOf(reserved("r1", chickenAdobo)))
        assertEquals(listOf("pork-adobo", "pancit"), result.ids())
    }

    @Test
    fun ownSavedReservedAndLowRatedListingsAreNeverRecommended() {
        val mine = post("mine", "Chicken curry", donor = me)
        val saved = post("saved", "Chicken soup")
        val open = post("open", "Chicken adobo")
        val claimed = post("claimed", "Chicken inasal", donor = "donor-b")
        val cancelled = post("cancelled", "Chicken tinola")
        val lowRated = post("low-rated", "Chicken wings", donor = "donor-b")
        val fine = post("fine", "Chicken sisig")
        val feed = listOf(mine, saved, open, claimed, cancelled, lowRated, fine)
        val history = listOf(
            reserved("r1", open),
            reserved("r2", claimed, ReservationStatus.CLAIMED),
            reserved("r3", cancelled, ReservationStatus.CANCELLED)
        )
        val result = recommend(feed, history, listOf(review("r2", 2, EARLIER)), saved = setOf("saved"))
        assertEquals(listOf("fine"), result.ids())

        // A later 4-star review of the same donor (its listing has left the feed) lifts the donor.
        val again = reserved("r4", post("claimed-2", "Chicken barbecue", donor = "donor-b"), ReservationStatus.CLAIMED)
        val lifted = recommend(
            feed, history + again, listOf(review("r2", 2, EARLIER), review("r4", 4, LATER)), saved = setOf("saved")
        )
        assertEquals(setOf("fine", "low-rated"), lifted.ids().toSet())

        // The other way round, the latest review (1 star) keeps the donor out.
        val lowered = recommend(
            feed, history + again, listOf(review("r2", 5, EARLIER), review("r4", 1, LATER)), saved = setOf("saved")
        )
        assertEquals(listOf("fine"), lowered.ids())
    }

    @Test
    fun anExpiringListingBeatsAnOtherwiseEqualOne() {
        val later = post("later", "Beef adobo", createdAt = "2026-09-28T07:30:00Z")
        val soon = post("soon", "Fish adobo", expiresInSeconds = 2 * 60 * 60L)
        val result = recommend(listOf(later, soon, chickenAdobo), listOf(reserved("r1", chickenAdobo)))!!
        assertEquals(listOf("soon", "later"), result.ids())
        assertEquals(0.15, result[0].score - result[1].score, 1e-9)
    }

    @Test
    fun aNearbyListingBeatsAnOtherwiseEqualOneOnlyWithAPhonePosition() {
        val near = GeoPin(9.6600, 123.8540) // about 1.1 km from the phone
        val far = GeoPin(9.7500, 123.8540) // about 11 km, outside the 5 km
        val farPost = post("far", "Beef adobo", createdAt = "2026-09-28T07:30:00Z", pin = far)
        val nearPost = post("near", "Fish adobo", pin = near)
        val feed = listOf(farPost, nearPost, chickenAdobo)
        val history = listOf(reserved("r1", chickenAdobo))
        // Without a position the newer listing comes first.
        assertEquals(listOf("far", "near"), recommend(feed, history).ids())
        val withPhone = recommend(feed, history, phonePin = phone)!!
        assertEquals(listOf("near", "far"), withPhone.ids())
        val boost = 0.15 * (1 - distanceMeters(phone, near) / 5_000.0)
        assertEquals(boost, withPhone[0].score - withPhone[1].score, 1e-9)
    }

    @Test
    fun theReasonSaysReservedBeforeSaved() {
        // Reserved and saved: it counts as reserved.
        val both = recommend(feed, listOf(reserved("r1", chickenAdobo)), saved = setOf("chicken-adobo"))
        assertEquals("Because you reserved Chicken adobo", both.reasonOf("pork-adobo"))
        val savedOnly = recommend(feed, saved = setOf("chicken-adobo"))
        assertEquals("Because you saved Chicken adobo", savedOnly.reasonOf("pork-adobo"))
    }

    @Test
    fun longNamesAreShortenedAtAWord() {
        assertEquals("Chicken adobo", shortReasonName("  Chicken adobo "))
        assertEquals("Twenty characters ok", shortReasonName("Twenty characters ok"))
        assertEquals("Homemade chicken…", shortReasonName("Homemade chicken adobo with rice"))
        assertEquals("Supercalifragilisti…", shortReasonName("Supercalifragilisticexpialidocious"))
        // Never half an emoji.
        assertEquals("abcdefghijklmnopqr…", shortReasonName("abcdefghijklmnopqr🍗 chicken"))

        val long = post("long", "Homemade chicken adobo with rice")
        val result = recommend(listOf(porkAdobo, long), listOf(reserved("r1", long)))
        assertEquals("Because you reserved Homemade chicken…", result.reasonOf("pork-adobo"))
    }

    @Test
    fun theSimilarityReasonNeedsTheSameCategoryOrAWord() {
        val history = listOf(reserved("r1", chickenAdobo))
        // Pandesal shares only the donor and "free" with Chicken adobo: no reason at all.
        assertNull(recommend(listOf(chickenAdobo, pandesal), history).reasonOf("pandesal"))
        // A shared word is enough, across categories.
        val sandwich = post("sandwich", "Chicken sandwich", BREAD)
        assertEquals(
            "Because you reserved Chicken adobo",
            recommend(listOf(chickenAdobo, sandwich), history).reasonOf("sandwich")
        )
        // Otherwise the other parts give the reason, even when similarity is the biggest.
        val near = pandesal.copy(pickupLatitude = 9.6600, pickupLongitude = 123.8540)
        assertEquals(
            distanceLabel(distanceMeters(phone, near.pickupPin!!)) + " away",
            recommend(listOf(chickenAdobo, near), history, phonePin = phone).reasonOf("pandesal")
        )
    }

    @Test
    fun theBiggestPartGivesTheReason() {
        val history = listOf(reserved("r1", chickenAdobo))
        // Another donor, category and price: no similarity at all.
        val other = post("other", "Kalabasa", VEGETABLES, donor = "donor-b", price = 20.0)
        val soon = now.plusSeconds(60 * 60).toString()
        fun reason(post: FoodPost) = recommend(listOf(chickenAdobo, post), history, phonePin = phone).reasonOf(post.id)

        assertNull(reason(other))
        assertEquals("Expires soon", reason(other.copy(expirationDate = soon)))
        val atPhone = other.copy(pickupLatitude = phone.latitude, pickupLongitude = phone.longitude)
        assertEquals(distanceLabel(0.0) + " away", reason(atPhone))
        // A pin at the phone and expiring soon both add 0.15: expiry wins the tie.
        assertEquals("Expires soon", reason(atPhone.copy(expirationDate = soon)))
        // Similarity (about 0.5) beats both.
        val porkSoonAtPhone = porkAdobo.copy(
            expirationDate = soon, pickupLatitude = phone.latitude, pickupLongitude = phone.longitude
        )
        assertEquals("Because you reserved Chicken adobo", reason(porkSoonAtPhone))
    }

    @Test
    fun aLikedListingOutweighsAnOpenReservation() {
        val beefStew = post("beef-stew", "Beef stew")
        val ubeBread = post("ube-bread", "Ube bread", BREAD)
        val beefTapa = post("beef-tapa", "Beef tapa", createdAt = "2026-09-28T07:30:00Z")
        val ubeCake = post("ube-cake", "Ube cake", BREAD)
        val feed = listOf(beefTapa, ubeCake, beefStew, ubeBread)
        val history = listOf(reserved("r1", beefStew), reserved("r2", ubeBread, ReservationStatus.CLAIMED))
        // Rated 3: both weigh 3, so the tie goes to the newer Beef tapa.
        assertEquals(listOf("beef-tapa", "ube-cake"), recommend(feed, history, listOf(review("r2", 3, LATER))).ids())
        // Rated 5: the liked Ube bread weighs 4, so Ube cake comes first.
        val liked = recommend(feed, history, listOf(review("r2", 5, LATER)))
        assertEquals(listOf("ube-cake", "beef-tapa"), liked.ids())
        assertEquals("Because you reserved Ube bread", liked.reasonOf("ube-cake"))
    }

    @Test
    fun withoutChoicesThereIsNoPersonalRow() {
        assertNull(recommend(feed))
        assertNull(recommend(feed, listOf(reserved("r1", chickenAdobo, ReservationStatus.CANCELLED))))
        // A saved listing that has left the feed doesn't count.
        assertNull(recommend(feed, saved = setOf("gone")))
        // A reservation counts even when its listing has left the feed.
        assertNotNull(recommend(feed, listOf(reserved("r1", post("gone", "Beef adobo")))))
    }

    @Test
    fun tiesGoToTheNewestListingThenTheFeedOrder() {
        val a = post("a", "Beef adobo", createdAt = "2026-09-28T07:00:00Z")
        val b = post("b", "Fish adobo", createdAt = "2026-09-28T07:30:00Z")
        val c = post("c", "Goat adobo", createdAt = "2026-09-28T07:00:00Z")
        val d = post("d", "Duck adobo", createdAt = null)
        val e = post("e", "Crab adobo", createdAt = "2026-09-28T07:00:00Z")
        val result = recommend(listOf(d, a, b, c, e, chickenAdobo), listOf(reserved("r1", chickenAdobo)))!!
        assertEquals(listOf("b", "a", "c", "e", "d"), result.ids())
        result.forEach { assertEquals(result[0].score, it.score, 1e-9) }
    }

    @Test
    fun theRowHasAtMostFiveListings() {
        assertEquals(5, RECOMMENDATION_LIMIT)
        val many = (1..40).map { post("p$it", "Adobo number $it") }
        val result = recommend(many + chickenAdobo, listOf(reserved("r1", chickenAdobo)))
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), result.ids())
    }

    @Test
    fun theFoodPreferencesFilterTheRow() {
        val history = listOf(reserved("r1", chickenAdobo))
        assertEquals(listOf("pandesal"), recommend(feed, history, include = { it.category == BREAD }).ids())
        // Everything filtered out: still the personal row, just empty.
        assertEquals(emptyList<Recommendation>(), recommend(feed, history, include = { false }))
    }

    @Test
    fun wordsAreLowercaseLongEnoughAndNotCommon() {
        assertEquals(listOf("chicken", "adobo", "rice", "eggs"), recommendationWords("Chicken ADOBO, with rice & 2 eggs!"))
        assertEquals(listOf("pancit"), recommendationWords("Pancit para sa lahat"))
        assertEquals(listOf("turon", "turon"), recommendationWords("Turon-turon"))
        assertEquals(listOf("7up", "100g", "ñoño", "café"), recommendationWords("7up/100g Ñoño café"))
        assertEquals(emptyList<String>(), recommendationWords(null))
        assertEquals(emptyList<String>(), recommendationWords("  "))
    }

    @Test
    fun aWordInTheNameCountsTwice() {
        val post = post("p", "Adobo", description = "Rice")
        val vector = ListingVectors(listOf(post)).of(post)
        assertEquals(2.0, vector.getValue("w:adobo") / vector.getValue("w:rice"), 1e-9)
    }

    @Test
    fun expiresSoonMeansLessThanThreeHoursLeft() {
        assertEquals(3 * 60 * 60L, EXPIRES_SOON_SECONDS)
        assertFalse(post("a", "A", expiresInSeconds = EXPIRES_SOON_SECONDS).expiresSoon(now))
        assertTrue(post("a", "A", expiresInSeconds = EXPIRES_SOON_SECONDS - 1).expiresSoon(now))
        assertTrue(post("a", "A", expiresInSeconds = -60).expiresSoon(now))
        assertFalse(post("a", "A").copy(expirationDate = "not a date").expiresSoon(now))
    }

    @Test
    fun aListingWithoutUsableWordsHasNoWordPartAndNoNaN() {
        // Only short and common words.
        val wordless = post("wordless", "Ok na ang", description = "at sa mga")
        val vector = ListingVectors(listOf(wordless, porkAdobo)).of(wordless)
        assertTrue(vector.keys.none { it.startsWith("w:") })
        assertTrue(vector.values.all { it.isFinite() })
        assertEquals(1.0, sqrt(vector.values.sumOf { it * it }), 1e-9)

        // As the chosen listing and as a card.
        val asChoice = recommend(listOf(wordless, porkAdobo, pandesal), listOf(reserved("r1", wordless)))!!
        assertTrue(asChoice.isNotEmpty() && asChoice.all { it.score.isFinite() })
        val asCard = recommend(listOf(wordless, chickenAdobo), listOf(reserved("r1", chickenAdobo)))!!
        assertEquals(listOf("wordless"), asCard.ids())
        assertTrue(asCard.single().score.isFinite())
    }

    private companion object {
        const val COOKED = "Cooked meals"
        const val BREAD = "Bread & pastries"
        const val VEGETABLES = "Vegetables"
        const val EARLIER = "2026-09-27T10:00:00Z"
        const val LATER = "2026-09-28T06:00:00Z"
    }
}

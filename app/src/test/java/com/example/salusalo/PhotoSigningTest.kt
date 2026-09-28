package com.example.salusalo

import com.example.salusalo.data.signPhotoPaths
import com.example.salusalo.data.withImageUrls
import com.example.salusalo.model.FoodPost
import io.github.jan.supabase.storage.SignedUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** Signing a list's photos in one request, and the per-photo fallback. */
class PhotoSigningTest {
    private val batches = mutableListOf<List<String>>()
    private val singles = mutableListOf<String>()

    private fun url(path: String) = "https://storage.test/object/sign/food-images/$path?token=t"

    private fun sign(
        paths: List<String?>,
        batch: (List<String>) -> List<SignedUrl> = { asked -> asked.map { SignedUrl(null, url(it), it) } },
        single: (String) -> String? = { url(it) }
    ): Map<String, String> = runBlocking {
        signPhotoPaths(
            paths,
            batch = { asked -> batches += asked; batch(asked) },
            single = { path -> singles += path; single(path) }
        )
    }

    @Test
    fun oneRequestSignsTheWholeList() {
        val urls = sign(listOf("u/a/image.jpg", "u/b/image.jpg", null, " ", "u/a/image.jpg"))
        assertEquals(listOf(listOf("u/a/image.jpg", "u/b/image.jpg")), batches)
        assertTrue(singles.isEmpty())
        assertEquals(mapOf("u/a/image.jpg" to url("u/a/image.jpg"), "u/b/image.jpg" to url("u/b/image.jpg")), urls)
    }

    @Test
    fun resultsAreMatchedByPathNotByOrder() {
        val urls = sign(listOf("a", "b"), batch = {
            listOf(SignedUrl(null, url("b"), "b"), SignedUrl(null, url("x"), "x"), SignedUrl(null, url("a"), "a"))
        })
        assertEquals(mapOf("a" to url("a"), "b" to url("b")), urls)
    }

    @Test
    fun aPathTheBatchRefusesLosesOnlyItsOwnPicture() {
        val urls = sign(listOf("a", "b", "c"), batch = {
            listOf(
                SignedUrl(null, url("a"), "a"),
                SignedUrl("Either the object does not exist or you do not have access to it", "", "b"),
                SignedUrl(null, url("c"), "c")
            )
        })
        assertEquals(mapOf("a" to url("a"), "c" to url("c")), urls)
        assertTrue(singles.isEmpty())
    }

    @Test
    fun whenTheBatchFailsEachPathIsSignedOnItsOwn() {
        val urls = sign(
            listOf("a", "b", "c"),
            batch = { throw SerializationException("unreadable answer") },
            single = { if (it == "b") null else url(it) }
        )
        assertEquals(1, batches.size)
        assertEquals(listOf("a", "b", "c"), singles)
        assertEquals(mapOf("a" to url("a"), "c" to url("c")), urls)
    }

    @Test
    fun cancellationIsNotTurnedIntoTheFallback() {
        assertThrows(CancellationException::class.java) {
            sign(listOf("a"), batch = { throw CancellationException("left the screen") })
        }
        assertTrue(singles.isEmpty())
    }

    @Test
    fun nothingToSignMakesNoRequest() {
        assertEquals(emptyMap<String, String>(), sign(listOf(null, "", "  ")))
        assertTrue(batches.isEmpty())
        assertTrue(singles.isEmpty())
    }

    /**
     * Why the fallback exists: supabase-kt 3.5.0's SignedUrl.signedURL is not
     * nullable, so a batch answer with one unsignable path can't be read at all.
     * If a later supabase-kt reads it, the fallback is no longer needed.
     */
    @Test
    fun supabaseKtCannotReadABatchAnswerWithAnUnsignablePath() {
        // Like supabase-kt's own Json for Storage answers.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val serializer = ListSerializer(SignedUrl.serializer())
        val good = """{"error":null,"path":"a","signedURL":"/object/sign/food-images/a?token=t"}"""
        val missing = """{"error":"Either the object does not exist or you do not have access to it","path":"b","signedURL":null}"""
        assertEquals("a", json.decodeFromString(serializer, "[$good]").single().path)
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(serializer, "[$good,$missing]")
        }
    }

    @Test
    fun eachListingGetsItsOwnCoverUrl() {
        fun post(id: String, path: String?) = FoodPost(
            id = id, donorId = "d", name = id, category = "Other", quantity = 1, unit = "pcs",
            imageUrl = path, pickupLocation = "Plaza", expirationDate = "2026-10-01T00:00:00Z"
        )
        val posts = listOf(post("1", "u/1/image.jpg"), post("2", "u/2/image.jpg"), post("3", null))
        val signed = posts.withImageUrls(mapOf("u/1/image.jpg" to url("u/1/image.jpg")))
        assertEquals(url("u/1/image.jpg"), signed[0].displayImageUrl)
        // Could not be signed: no URL, so its tile says "Image unavailable".
        assertNull(signed[1].displayImageUrl)
        assertEquals(posts[1], signed[1])
        // No photo stored: a plain tile.
        assertNull(signed[2].displayImageUrl)
    }
}

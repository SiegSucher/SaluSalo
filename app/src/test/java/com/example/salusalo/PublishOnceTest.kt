package com.example.salusalo

import com.example.salusalo.data.PublishUnconfirmedException
import com.example.salusalo.data.publishOnce
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** Posting a listing once: retries never post twice, and photos are deleted only when nothing was saved. */
class PublishOnceTest {
    private val steps = mutableListOf<String>()
    private val refusal = IllegalStateException("refused by the database")

    private fun publish(
        stored: MutableList<String?>,
        insert: () -> String = { "new" },
        findFails: Int = Int.MAX_VALUE
    ): String = runBlocking {
        var finds = 0
        publishOnce(
            find = {
                steps += "find"
                if (++finds >= findFails) throw IOException("offline")
                stored.removeFirstOrNull()
            },
            upload = { steps += "upload" },
            insert = { steps += "insert"; insert() },
            deletePhotos = { steps += "delete" },
            refused = { it === refusal }
        )
    }

    @Test
    fun firstTryUploadsAndInserts() {
        assertEquals("new", publish(mutableListOf(null)))
        assertEquals(listOf("find", "upload", "insert"), steps)
    }

    @Test
    fun aRetryOfASavedListingReturnsItWithoutUploading() {
        assertEquals("saved", publish(mutableListOf("saved")))
        assertEquals(listOf("find"), steps)
    }

    @Test
    fun aLostAnswerAfterTheSaveIsASuccessAndKeepsThePhotos() {
        val result = publish(mutableListOf(null, "saved"), insert = { throw IOException("connection reset") })
        assertEquals("saved", result)
        assertEquals(listOf("find", "upload", "insert", "find"), steps)
    }

    @Test
    fun aLostAnswerWithNothingSavedDeletesThePhotos() {
        val error = IOException("connection reset")
        val thrown = assertThrows(IOException::class.java) {
            publish(mutableListOf(null, null), insert = { throw error })
        }
        assertSame(error, thrown)
        assertEquals(listOf("find", "upload", "insert", "find", "delete"), steps)
    }

    @Test
    fun whenTheCheckFailsTooThePhotosStay() {
        assertThrows(PublishUnconfirmedException::class.java) {
            publish(mutableListOf(null), insert = { throw IOException("timeout") }, findFails = 2)
        }
        assertEquals(listOf("find", "upload", "insert", "find"), steps)
    }

    @Test
    fun aRefusalDeletesThePhotosWithoutChecking() {
        val thrown = assertThrows(IllegalStateException::class.java) {
            publish(mutableListOf(null), insert = { throw refusal })
        }
        assertSame(refusal, thrown)
        assertEquals(listOf("find", "upload", "insert", "delete"), steps)
    }
}

package com.example.salusalo

import com.example.salusalo.data.ProfileException
import com.example.salusalo.data.ensureRole
import com.example.salusalo.data.hasSupabaseRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** When the app calls the set-firebase-role Edge Function (UserRepository.ensureSupabaseRole). */
class SupabaseRoleTest {
    private val withRole = mapOf<String, Any?>("role" to "authenticated", "sub" to "uid-1")
    private val withoutRole = mapOf<String, Any?>("sub" to "uid-1")

    /** Fake token reads: [cached] for a cached read, then [refreshed] in order. */
    private class Tokens(val cached: Map<String, Any?>, vararg refreshed: Map<String, Any?>) {
        private val next = refreshed.toMutableList()
        val reads = mutableListOf<Boolean>()
        var functionCalls = 0

        suspend fun read(refresh: Boolean): Map<String, Any?> {
            reads += refresh
            return if (refresh) next.removeAt(0) else cached
        }

        suspend fun setRole() {
            functionCalls++
        }
    }

    @Test
    fun aCachedTokenWithTheRoleNeedsNothingElse() = runBlocking {
        val tokens = Tokens(withRole)
        ensureRole(tokens::read, tokens::setRole)
        assertEquals(listOf(false), tokens.reads)
        assertEquals(0, tokens.functionCalls)
    }

    @Test
    fun aRefreshedTokenWithTheRoleNeedsNoFunctionCall() = runBlocking {
        // For example, the backup script set the claim after the token was issued.
        val tokens = Tokens(withoutRole, withRole)
        ensureRole(tokens::read, tokens::setRole)
        assertEquals(listOf(false, true), tokens.reads)
        assertEquals(0, tokens.functionCalls)
    }

    @Test
    fun aNewAccountCallsTheFunctionOnceThenRefreshes() = runBlocking {
        val tokens = Tokens(withoutRole, withoutRole, withRole)
        ensureRole(tokens::read, tokens::setRole)
        assertEquals(listOf(false, true, true), tokens.reads)
        assertEquals(1, tokens.functionCalls)
    }

    @Test
    fun aRoleStillMissingAfterTheFunctionFails() = runBlocking {
        val tokens = Tokens(withoutRole, withoutRole, withoutRole)
        assertFailsWithRoleSetup { ensureRole(tokens::read, tokens::setRole) }
        assertEquals(1, tokens.functionCalls)
    }

    @Test
    fun aFunctionErrorFailsWithoutItsText() = runBlocking {
        val tokens = Tokens(withoutRole, withoutRole)
        val error = assertFailsWithRoleSetup {
            ensureRole(tokens::read) { throw IllegalStateException("https://example.test secret details") }
        }
        assertFalse(error.message.orEmpty().contains("example.test"))
        assertEquals(listOf(false, true), tokens.reads)
    }

    @Test
    fun aTokenErrorFails() = runBlocking {
        assertFailsWithRoleSetup {
            ensureRole({ throw RuntimeException("offline") }, { fail("the function must not be called") })
        }
        Unit
    }

    @Test
    fun cancellationIsNotTurnedIntoAnError() = runBlocking {
        val tokens = Tokens(withoutRole, withoutRole)
        try {
            ensureRole(tokens::read) { throw CancellationException("left the screen") }
            fail("expected a cancellation")
        } catch (error: CancellationException) {
            assertEquals("left the screen", error.message)
        }
    }

    @Test
    fun onlyTheExactRoleCounts() {
        assertTrue(hasSupabaseRole(mapOf("role" to "authenticated")))
        assertFalse(hasSupabaseRole(mapOf("role" to "Authenticated")))
        assertFalse(hasSupabaseRole(mapOf("role" to "admin")))
        assertFalse(hasSupabaseRole(mapOf("role" to null)))
        assertFalse(hasSupabaseRole(mapOf("role" to listOf("authenticated"))))
        assertFalse(hasSupabaseRole(emptyMap()))
    }

    private suspend fun assertFailsWithRoleSetup(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: ProfileException) {
            assertSame(ProfileException.RoleSetupFailed, error)
            return error
        }
        fail("expected ProfileException.RoleSetupFailed")
        throw AssertionError()
    }
}

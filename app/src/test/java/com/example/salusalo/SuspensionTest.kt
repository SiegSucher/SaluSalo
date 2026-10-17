package com.example.salusalo

import com.example.salusalo.model.AccountStatus
import com.example.salusalo.model.StatusChange
import com.example.salusalo.model.statusChange
import com.example.salusalo.model.suspensionReasonLabel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §36: the suspended screen's wording and when the app moves to or from it. */
class SuspensionTest {
    @Test
    fun eachCodeHasItsWording() {
        assertEquals("Repeated no-shows at pickup", suspensionReasonLabel("NO_SHOWS"))
        assertEquals("Unsafe or misleading food", suspensionReasonLabel("UNSAFE_FOOD"))
        assertEquals("Harassment or abuse", suspensionReasonLabel("HARASSMENT"))
        assertEquals("Spam or fake account", suspensionReasonLabel("SPAM"))
        assertEquals("Breaking the community rules", suspensionReasonLabel("OTHER"))
    }

    @Test
    fun unknownOrMissingCodeIsTheGeneralRule() {
        assertEquals("Breaking the community rules", suspensionReasonLabel("SOMETHING_NEW"))
        assertEquals("Breaking the community rules", suspensionReasonLabel(null))
    }

    @Test
    fun statusDecodesBothAnswers() {
        val suspended = Json.decodeFromString(AccountStatus.serializer(), """{"suspended":true,"reason":"SPAM"}""")
        assertTrue(suspended.suspended)
        assertEquals("SPAM", suspended.reason)
        val active = Json.decodeFromString(AccountStatus.serializer(), """{"suspended":false,"reason":null}""")
        assertFalse(active.suspended)
        assertNull(active.reason)
    }

    @Test
    fun noAnswerChangesNothing() {
        assertEquals(StatusChange.NONE, statusChange(onSuspendedScreen = false, status = null))
        assertEquals(StatusChange.NONE, statusChange(onSuspendedScreen = true, status = null))
    }

    @Test
    fun suspendedWhileOnHomeShowsTheScreen() {
        assertEquals(StatusChange.SHOW_SUSPENDED, statusChange(false, AccountStatus(true, "NO_SHOWS")))
    }

    @Test
    fun restoredWhileOnTheScreenResumesSetup() {
        assertEquals(StatusChange.RESUME_SETUP, statusChange(true, AccountStatus(false, null)))
    }

    @Test
    fun sameStateChangesNothing() {
        assertEquals(StatusChange.NONE, statusChange(true, AccountStatus(true, "SPAM")))
        assertEquals(StatusChange.NONE, statusChange(false, AccountStatus(false, null)))
    }
}

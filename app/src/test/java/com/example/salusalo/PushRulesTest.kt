package com.example.salusalo

import android.content.Intent
import com.example.salusalo.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which pushed alerts become phone notifications, and what a tap carries (§35). */
class PushRulesTest {
    private val message = mapOf(
        "alert_id" to "a1", "type" to "MESSAGE", "title" to "New message from Ana",
        "body" to "Pandesal: Is it still there?", "conversation_id" to "c1", "uid" to "u1"
    )
    private val tap = PushTap("a1", NotificationType.MESSAGE, "c1", null, "u1")

    @Test
    fun readsTheTapFromTheAlertData() {
        assertEquals(tap, pushTapFrom(message))
    }

    @Test
    fun noAlertIdMeansNoTap() {
        assertNull(pushTapFrom(message - "alert_id"))
        assertNull(pushTapFrom(message + ("alert_id" to "")))
    }

    @Test
    fun aTypeThisBuildDoesNotKnowIsUnknown() {
        assertEquals(NotificationType.UNKNOWN, pushTapFrom(message + ("type" to "SOMETHING_NEW"))?.type)
        assertEquals(NotificationType.UNKNOWN, pushTapFrom(message - "type")?.type)
        assertEquals(NotificationType.ANNOUNCEMENT, pushTapFrom(message + ("type" to "ANNOUNCEMENT"))?.type)
    }

    @Test
    fun emptyIdsAreMissing() {
        val read = pushTapFrom(message + ("conversation_id" to "") + ("reservation_id" to ""))!!
        assertNull(read.conversationId)
        assertNull(read.reservationId)
    }

    @Test
    fun showsForTheAccountSignedInOnThisPhone() {
        assertTrue(shouldShowPush(tap, "u1", appResumed = false, openChatId = null))
        assertTrue(shouldShowPush(tap, "u1", appResumed = true, openChatId = null))
    }

    @Test
    fun dropsWhenSignedOutOrForAnotherAccount() {
        assertFalse(shouldShowPush(tap, null, appResumed = false, openChatId = null))
        assertFalse(shouldShowPush(tap, "u2", appResumed = false, openChatId = null))
        assertFalse(shouldShowPush(tap.copy(uid = null), "u1", appResumed = false, openChatId = null))
    }

    @Test
    fun dropsAMessageOfTheChatOpenOnScreen() {
        assertFalse(shouldShowPush(tap, "u1", appResumed = true, openChatId = "c1"))
    }

    @Test
    fun showsThatChatsMessageWhileTheAppIsInTheBackground() {
        assertTrue(shouldShowPush(tap, "u1", appResumed = false, openChatId = "c1"))
    }

    @Test
    fun showsOtherChatsAndOtherAlertsWhileAChatIsOpen() {
        assertTrue(shouldShowPush(tap.copy(conversationId = "c2"), "u1", appResumed = true, openChatId = "c1"))
        assertTrue(
            shouldShowPush(
                tap.copy(type = NotificationType.RESERVATION_CREATED),
                "u1", appResumed = true, openChatId = "c1"
            )
        )
    }

    @Test
    fun readsATapOnlyOnAFreshStartNeverFromRecents() {
        assertTrue(isFreshTap(hasSavedState = false, intentFlags = 0))
        assertTrue(isFreshTap(hasSavedState = false, intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK))
        // Android restoring the app, or reopening it from Recents, hands back the old intent.
        assertFalse(isFreshTap(hasSavedState = true, intentFlags = 0))
        assertFalse(isFreshTap(hasSavedState = false, intentFlags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }

    @Test
    fun pushesCountAsArrivingFor14DaysAfterTheLastOne() {
        val day = 24 * 60 * 60 * 1000L
        assertFalse(pushesArriving(lastPushAtMillis = 0, nowMillis = 100 * day))
        assertTrue(pushesArriving(lastPushAtMillis = 100 * day, nowMillis = 100 * day + 1))
        assertTrue(pushesArriving(lastPushAtMillis = 100 * day, nowMillis = 114 * day - 1))
        assertFalse(pushesArriving(lastPushAtMillis = 100 * day, nowMillis = 114 * day))
        // The phone's clock moved back: the push still arrived recently.
        assertTrue(pushesArriving(lastPushAtMillis = 100 * day, nowMillis = 99 * day))
    }

    @Test
    fun triesToSaveThePushTokenSoonThenLessOften() {
        assertEquals(15_000L, pushRetryDelayMillis(0))
        assertEquals(30_000L, pushRetryDelayMillis(1))
        assertEquals(60_000L, pushRetryDelayMillis(2))
        assertEquals(120_000L, pushRetryDelayMillis(3))
        // At most every 10 minutes, however long it keeps failing.
        assertEquals(600_000L, pushRetryDelayMillis(6))
        assertEquals(600_000L, pushRetryDelayMillis(1_000))
    }
}

package com.example.salusalo

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
}

package com.example.salusalo

import com.example.salusalo.model.AppNotification
import com.example.salusalo.model.NotificationType
import com.example.salusalo.model.isKnown
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Alerts decode leniently: a type added on the server later never breaks the Alerts list (§27). */
class NotificationTypeTest {
    private fun alert(id: String, type: String) =
        """{"id":"$id","user_id":"u1","type":"$type","title":"T","body":"B","created_at":"2026-10-04T08:00:00Z"}"""

    @Test
    fun announcementDecodes() {
        val decoded = Json.decodeFromString(AppNotification.serializer(), alert("a1", "ANNOUNCEMENT"))
        assertEquals(NotificationType.ANNOUNCEMENT, decoded.type)
    }

    @Test
    fun typeThisBuildDoesNotKnowDecodesAsUnknown() {
        val decoded = Json.decodeFromString(AppNotification.serializer(), alert("a1", "SOMETHING_NEW"))
        assertEquals(NotificationType.UNKNOWN, decoded.type)
    }

    @Test
    fun listWithAnUnknownTypeStillLoadsAndDropsOnlyThatAlert() {
        val json = listOf(alert("a1", "MESSAGE"), alert("a2", "SOMETHING_NEW"), alert("a3", "ANNOUNCEMENT"))
            .joinToString(",", "[", "]")
        val list = Json.decodeFromString(ListSerializer(AppNotification.serializer()), json)
        assertEquals(listOf("a1", "a3"), list.filter { it.isKnown }.map { it.id })
    }
}

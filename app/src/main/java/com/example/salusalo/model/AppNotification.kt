package com.example.salusalo.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    @SerialName("food_post_id") val foodPostId: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("reservation_id") val reservationId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable(with = NotificationTypeSerializer::class)
enum class NotificationType {
    MESSAGE, RESERVATION_CREATED, RESERVATION_CANCELLED, RESERVATION_CLAIMED, RESERVATION_REMOVED,
    /** An admin's announcement from the web dashboard (§27). */
    ANNOUNCEMENT,
    /** A type added on the server after this build; never shown (NotificationRepository drops it). */
    UNKNOWN
}

/** False for an alert of a type this build doesn't know. */
val AppNotification.isKnown: Boolean get() = type != NotificationType.UNKNOWN

/**
 * Decodes a type name this build doesn't know as UNKNOWN instead of failing, so one
 * new alert type can't break the whole Alerts list or the live alerts (§27).
 */
object NotificationTypeSerializer : KSerializer<NotificationType> {
    override val descriptor = PrimitiveSerialDescriptor("com.example.salusalo.model.NotificationType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: NotificationType) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): NotificationType {
        val name = decoder.decodeString()
        return NotificationType.entries.firstOrNull { it.name == name && it != NotificationType.UNKNOWN }
            ?: NotificationType.UNKNOWN
    }
}

/**
 * A missing row on the server means every alert type is on, matching these defaults.
 * The switches are always encoded: supabase-kt's Json leaves out values equal to their
 * default, so a switch turned back on would otherwise never reach the upsert.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationPreferences(
    @SerialName("user_id") val userId: String,
    @EncodeDefault @SerialName("message_alerts") val messageAlerts: Boolean = true,
    @EncodeDefault @SerialName("reservation_alerts") val reservationAlerts: Boolean = true,
    // No switch or alert uses these two any more; they are kept so a save
    // writes back the stored values unchanged (older builds still show them).
    @EncodeDefault val recommendations: Boolean = true,
    @EncodeDefault @SerialName("pickup_reminders") val pickupReminders: Boolean = true
)

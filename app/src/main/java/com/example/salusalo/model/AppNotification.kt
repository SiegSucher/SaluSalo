package com.example.salusalo.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
enum class NotificationType { MESSAGE, RESERVATION_CREATED, RESERVATION_CANCELLED, RESERVATION_CLAIMED }

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
    @EncodeDefault val recommendations: Boolean = true,
    @EncodeDefault @SerialName("pickup_reminders") val pickupReminders: Boolean = true
)

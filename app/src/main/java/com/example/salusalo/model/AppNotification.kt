package com.example.salusalo.model

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

/** A missing row on the server means every alert type is on, matching these defaults. */
@Serializable
data class NotificationPreferences(
    @SerialName("user_id") val userId: String,
    @SerialName("message_alerts") val messageAlerts: Boolean = true,
    @SerialName("reservation_alerts") val reservationAlerts: Boolean = true,
    val recommendations: Boolean = true,
    @SerialName("pickup_reminders") val pickupReminders: Boolean = true
)

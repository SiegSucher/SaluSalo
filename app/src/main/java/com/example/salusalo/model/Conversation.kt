package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Conversation(
    val id: String,
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("donor_id") val donorId: String,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** A row of get_my_conversations(): the caller's thread with display details. */
@Serializable
data class ConversationSummary(
    val id: String,
    @SerialName("food_post_id") val foodPostId: String,
    @SerialName("food_name") val foodName: String,
    @SerialName("food_image_path") val foodImagePath: String? = null,
    @SerialName("is_donor") val isDonor: Boolean,
    @SerialName("other_name") val otherName: String,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_mine") val lastMessageMine: Boolean? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @Transient val displayImageUrl: String? = null
)

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

/**
 * Parses a Postgres timestamp as PostgREST or Realtime sends it
 * ("2026-09-25T08:10:36.123456+00:00", possibly with a space instead of "T").
 */
fun parseTimestamp(value: String?): java.time.Instant? {
    val text = value?.trim()?.replace(' ', 'T') ?: return null
    return runCatching { java.time.OffsetDateTime.parse(text).toInstant() }
        .recoverCatching { java.time.Instant.parse(text) }
        .getOrNull()
}

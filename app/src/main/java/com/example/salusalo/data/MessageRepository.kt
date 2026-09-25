package com.example.salusalo.data

import com.example.salusalo.model.ChatMessage
import com.example.salusalo.model.Conversation
import com.example.salusalo.model.ConversationSummary
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Listing conversations. Threads and messages are read-only tables for the
 * client; every write goes through an RPC that derives the caller from the JWT.
 */
class MessageRepository {
    private val supabase = SupabaseClientProvider.client
    private val postgrest = supabase.postgrest
    private val bucket = supabase.storage.from("food-images")

    /** Starts or reopens the caller's thread with a listing's donor. */
    suspend fun startListingConversation(foodPostId: String): Conversation =
        postgrest.rpc("start_listing_conversation", buildJsonObject { put("p_food_post_id", foodPostId) }).decodeAs()

    /** Opens the thread between a reservation's recipient and donor (either may call). */
    suspend fun openReservationConversation(reservationId: String): Conversation =
        postgrest.rpc("open_reservation_conversation", buildJsonObject { put("p_reservation_id", reservationId) }).decodeAs()

    suspend fun getMyConversations(): List<ConversationSummary> =
        postgrest.rpc("get_my_conversations").decodeList<ConversationSummary>().map { it.withSignedImage() }

    suspend fun getConversation(conversationId: String): ConversationSummary? =
        postgrest.rpc("get_my_conversations", buildJsonObject { put("p_conversation_id", conversationId) })
            .decodeList<ConversationSummary>().firstOrNull()?.withSignedImage()

    suspend fun getMessages(conversationId: String): List<ChatMessage> =
        supabase.from("messages").select {
            filter { filter("conversation_id", FilterOperator.EQ, conversationId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun sendMessage(conversationId: String, body: String): ChatMessage {
        val text = body.trim()
        require(text.isNotEmpty()) { "Type a message first." }
        require(text.length <= 2000) { "Messages can be at most 2000 characters." }
        return postgrest.rpc("send_message", buildJsonObject {
            put("p_conversation_id", conversationId); put("p_body", text)
        }).decodeAs()
    }

    suspend fun markRead(conversationId: String) {
        postgrest.rpc("mark_conversation_read", buildJsonObject { put("p_conversation_id", conversationId) })
    }

    /** New messages in one thread while it is open. */
    fun messageInserts(conversationId: String): Flow<ChatMessage> =
        realtimeInserts("messages", "conversation_id", conversationId)

    private suspend fun ConversationSummary.withSignedImage() =
        copy(displayImageUrl = bucket.signedUrlOrNull(foodImagePath))
}

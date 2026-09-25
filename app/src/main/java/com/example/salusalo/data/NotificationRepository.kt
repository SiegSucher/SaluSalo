package com.example.salusalo.data

import com.example.salusalo.model.AppNotification
import com.example.salusalo.model.NotificationPreferences
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * In-app alerts. The database creates them (new messages and reservation
 * changes); the client can only read its own and mark them read.
 */
class NotificationRepository(
    private val userRepository: UserRepository = UserRepository()
) {
    private val supabase = SupabaseClientProvider.client
    private val postgrest = supabase.postgrest

    suspend fun getNotifications(): List<AppNotification> =
        supabase.from("notifications").select {
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList()

    /** Marks the given alerts read, or every unread alert when [ids] is null. */
    suspend fun markRead(ids: List<String>? = null) {
        postgrest.rpc("mark_notifications_read", buildJsonObject {
            if (ids != null) putJsonArray("p_ids") { ids.forEach { add(JsonPrimitive(it)) } }
        })
    }

    /** New alerts for this profile while the app is open. */
    fun inserts(profileId: String): Flow<AppNotification> =
        realtimeInserts("notifications", "user_id", profileId)

    /** The caller's saved preferences, or the all-on defaults when none are saved yet. */
    suspend fun getPreferences(): NotificationPreferences {
        val profileId = userRepository.getCurrentProfile().id
        return supabase.from("notification_preferences").select()
            .decodeSingleOrNull<NotificationPreferences>()
            ?: NotificationPreferences(userId = profileId)
    }

    suspend fun savePreferences(preferences: NotificationPreferences): NotificationPreferences {
        // The row is always the caller's own profile; RLS rejects any other user_id.
        val own = preferences.copy(userId = userRepository.getCurrentProfile().id)
        supabase.from("notification_preferences").upsert(own) { onConflict = "user_id" }
        return own
    }
}

package com.example.salusalo.data

import com.example.salusalo.model.AppNotification
import com.example.salusalo.model.NotificationPreferences
import com.example.salusalo.model.isKnown
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * In-app alerts. The database creates them (new messages, reservation changes
 * and, since §27, admins' announcements); the client can only read its own and
 * mark them read. Alerts of a type this build doesn't know are dropped here.
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
        }.decodeList<AppNotification>().filter { it.isKnown }

    /** Marks the given alerts read, or every unread alert when [ids] is null. */
    suspend fun markRead(ids: List<String>? = null) {
        postgrest.rpc("mark_notifications_read", buildJsonObject {
            if (ids != null) putJsonArray("p_ids") { ids.forEach { add(JsonPrimitive(it)) } }
        })
    }

    /** New alerts for this profile while the app is open; [onRejoined] after a lost connection. */
    fun inserts(profileId: String, onRejoined: suspend () -> Unit): Flow<AppNotification> =
        realtimeInserts<AppNotification>("notifications", "user_id", profileId, onRejoined).filter { it.isKnown }

    /** The caller's saved preferences, or the all-on defaults when none are saved yet. */
    suspend fun getPreferences(): NotificationPreferences {
        val profileId = userRepository.getCurrentProfileId()
        return supabase.from("notification_preferences").select()
            .decodeSingleOrNull<NotificationPreferences>()
            ?: NotificationPreferences(userId = profileId)
    }

    suspend fun savePreferences(preferences: NotificationPreferences): NotificationPreferences {
        // The row is always the caller's own profile; RLS rejects any other user_id.
        val own = preferences.copy(userId = userRepository.getCurrentProfileId())
        supabase.from("notification_preferences").upsert(own) { onConflict = "user_id" }
        return own
    }
}

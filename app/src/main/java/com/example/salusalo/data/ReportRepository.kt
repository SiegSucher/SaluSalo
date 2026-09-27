package com.example.salusalo.data

import com.example.salusalo.model.ReportReason
import com.example.salusalo.model.ReportTarget
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reports on listings and people. Clients can't read reports at all; the only
 * call is submit_report(), which takes the reporter from the token and finds
 * the reported person itself (the listing's donor or the chat's other person).
 */
class ReportRepository {
    private val postgrest = SupabaseClientProvider.client.postgrest

    /** [targetId] is the listing for LISTING and DONOR, the conversation for CHAT. */
    suspend fun submitReport(target: ReportTarget, targetId: String, reason: ReportReason, details: String) {
        postgrest.rpc("submit_report", buildJsonObject {
            put("p_target", target.code)
            put("p_target_id", targetId)
            put("p_reason", reason.code)
            put("p_details", details.trim().ifBlank { null })
        })
    }
}

package com.example.salusalo.data

import com.example.salusalo.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.tasks.await

/**
 * Uses Firebase's signed ID token for every Supabase request.
 * Supabase must be configured with Firebase Third-Party Auth before requests
 * to protected tables or Storage will succeed.
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "Supabase URL is missing from local.properties."
        }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase publishable key is missing from local.properties."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            accessToken = {
                FirebaseAuth.getInstance()
                    .currentUser
                    ?.getIdToken(false)
                    ?.await()
                    ?.token
            }
            install(Postgrest)
            install(Storage)
            // Realtime also authenticates with the Firebase token above, read
            // when a channel joins. It is not refreshed automatically without
            // the Supabase Auth plugin; NotificationViewModel re-sends it.
            install(Realtime)
        }
    }
}
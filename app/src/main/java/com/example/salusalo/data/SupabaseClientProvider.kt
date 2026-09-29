package com.example.salusalo.data

import com.example.salusalo.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.tasks.await

/**
 * Uses Firebase's signed ID token for every Supabase request.
 * Supabase must be configured with Firebase Third-Party Auth before requests
 * to protected tables or Storage will succeed, and the token must carry the
 * claim role: "authenticated" (see [UserRepository.ensureSupabaseRole], which
 * calls the set-firebase-role Edge Function through the Functions plugin).
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
                val auth = FirebaseAuth.getInstance()
                try {
                    auth.currentUser?.getIdToken(false)?.await()?.token
                } catch (error: FirebaseAuthInvalidUserException) {
                    // The account was disabled or deleted, or its sign-in was
                    // revoked or expired: this session can't be used again, so
                    // end it (the app then returns to Sign In). A network
                    // failure is a different exception and keeps the session.
                    auth.signOut()
                    throw error
                }
            }
            install(Postgrest)
            install(Storage)
            // Realtime also authenticates with the Firebase token above, read
            // when a channel joins. It is not refreshed automatically without
            // the Supabase Auth plugin; NotificationViewModel re-sends it.
            install(Realtime)
            // Edge Functions get the same Firebase token and publishable key.
            install(Functions)
        }
    }
}
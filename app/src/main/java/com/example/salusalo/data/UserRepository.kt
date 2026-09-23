package com.example.salusalo.data

import com.example.salusalo.model.NewUserProfile
import com.example.salusalo.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator

class UserRepository {
    private val supabase = SupabaseClientProvider.client
    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Gets the current Firebase user, then finds or creates that user's one
     * Supabase profile. The Firebase UID is always read from FirebaseAuth; it
     * is never accepted as an argument from the UI.
     */
    suspend fun loadOrCreateCurrentProfile(
        displayNameOverride: String? = null
    ): UserProfile {
        val firebaseUser = firebaseAuth.currentUser
            ?: throw ProfileException.NotSignedIn

        val email = firebaseUser.email?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw ProfileException.MissingEmail

        findProfile(firebaseUser.uid)?.let { return it }

        val name = displayNameOverride?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: firebaseUser.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: email.substringBefore("@")

        val newProfile = NewUserProfile(
            firebaseUid = firebaseUser.uid,
            name = name,
            email = email
        )

        return try {
            supabase.from("users")
                .insert(newProfile) {
                    select()
                }
                .decodeSingle()
        } catch (error: Exception) {
            // A second request may have created the same profile first.
            findProfile(firebaseUser.uid) ?: throw error
        }
    }

    suspend fun getCurrentProfile(): UserProfile {
        val firebaseUid = firebaseAuth.currentUser?.uid
            ?: throw ProfileException.NotSignedIn
        return findProfile(firebaseUid) ?: throw ProfileException.NotFound
    }

    private suspend fun findProfile(firebaseUid: String): UserProfile? =
        supabase.from("users")
            .select {
                filter {
                    filter(
                        column = "firebase_uid",
                        operator = FilterOperator.EQ,
                        value = firebaseUid
                    )
                }
            }
            .decodeList<UserProfile>()
            .firstOrNull()
}

sealed class ProfileException(message: String) : Exception(message) {
    data object NotSignedIn : ProfileException("No Firebase user is signed in.")
    data object MissingEmail : ProfileException("The Firebase account has no email address.")
    data object NotFound : ProfileException("No profile exists for this account.")
}

fun profileErrorMessage(error: Throwable): String = when (error) {
    ProfileException.NotSignedIn ->
        "Please sign in before loading your profile."
    ProfileException.MissingEmail ->
        "Your account needs an email address before a profile can be created."
    ProfileException.NotFound ->
        "Your account profile could not be found."
    else ->
        "We could not load your profile. Check your internet connection and try again."
}
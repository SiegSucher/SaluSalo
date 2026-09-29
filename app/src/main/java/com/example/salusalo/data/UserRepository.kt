package com.example.salusalo.data

import com.example.salusalo.model.NewUserProfile
import com.example.salusalo.model.UserProfile
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

class UserRepository(
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private val supabase = SupabaseClientProvider.client
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val profileImages = supabase.storage.from("profile-images")

    /**
     * Makes sure the signed-in account's Firebase ID token carries the claim
     * role: "authenticated" that Supabase requires. Called before the first
     * Supabase request of a session. The cached token is checked first, then a
     * refreshed one (the claim may have been set meanwhile, for example by the
     * developer's backup script). Only when both lack it is the set-firebase-role
     * Edge Function called; it adds the claim to the caller's own account, which
     * it takes from the verified token. Then the token is refreshed once more.
     * One setup runs at a time for the whole app. Throws
     * [ProfileException.RoleSetupFailed] when the role can't be set up.
     */
    suspend fun ensureSupabaseRole() {
        roleSetupLock.withLock {
            val user = firebaseAuth.currentUser ?: throw ProfileException.NotSignedIn
            ensureRole(
                readClaims = { refresh -> user.getIdToken(refresh).await().claims },
                setRole = { supabase.functions.invoke(SET_ROLE_FUNCTION) }
            )
        }
    }

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
            findProfile(firebaseUser.uid)?.let { return it }
            // Otherwise a duplicate can only be the email: an earlier profile
            // uses it, for example from a Firebase account deleted and created
            // again. The user may restore it ([relinkProfile]).
            if (error is PostgrestRestException && error.code == "23505") throw ProfileException.EmailInUse
            throw error
        }
    }

    /**
     * Moves the earlier profile with this account's email to this account
     * (relink_my_profile), after the user confirmed it. The server allows it
     * only when Firebase's signed token says the email is verified, so the
     * Firebase user is reloaded and the token refreshed first. Returns null,
     * without calling the server, while the email is not verified.
     */
    suspend fun relinkProfile(): UserProfile? {
        val user = firebaseAuth.currentUser ?: throw ProfileException.NotSignedIn
        user.reload().await()
        if (!user.isEmailVerified) return null
        user.getIdToken(true).await()
        return supabase.postgrest.rpc("relink_my_profile").decodeAs()
    }

    suspend fun getCurrentProfile(): UserProfile {
        val firebaseUid = firebaseAuth.currentUser?.uid
            ?: throw ProfileException.NotSignedIn
        return findProfile(firebaseUid) ?: throw ProfileException.NotFound
    }

    /**
     * The signed-in account's profile ID, looked up once per Firebase account
     * and then remembered while the app runs, which saves a `users` request on
     * most loads. A profile's ID never changes (a restored profile keeps it);
     * every request is still checked against the token by RLS.
     */
    suspend fun getCurrentProfileId(): String {
        val firebaseUid = firebaseAuth.currentUser?.uid
            ?: throw ProfileException.NotSignedIn
        profileIdsByUid[firebaseUid]?.let { return it }
        return getCurrentProfile().id.also { profileIdsByUid[firebaseUid] = it }
    }

    /** Saves only the authenticated caller's name and bio through the restricted RPC. */
    suspend fun updateMyProfile(name: String, bio: String): UserProfile =
        supabase.postgrest.rpc("update_my_profile_details", buildJsonObject {
            put("p_name", name.trim())
            put("p_bio", bio.trim())
        }).decodeAs()

    /**
     * Uploads a new profile photo (already a JPEG) under the user's own folder,
     * points the profile at it, then removes the previous photo. Each upload
     * uses a new path, so the old picture is never served from a cache.
     */
    suspend fun uploadProfilePhoto(jpeg: ByteArray): UserProfile {
        val firebaseUid = firebaseAuth.currentUser?.uid ?: throw ProfileException.NotSignedIn
        val previousPath = getCurrentProfile().profileImageUrl
        val path = storageRepository.profileImagePath(firebaseUid)

        profileImages.upload(path, jpeg) { upsert = false }

        val updated = try {
            supabase.postgrest.rpc("set_my_profile_image", buildJsonObject {
                put("p_path", path)
            }).decodeAs<UserProfile>()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Only the database's own refusal proves the profile still points at
            // the old photo. After a lost answer, look before deleting the upload.
            val refused = error is PostgrestRestException && error.code != null
            val current = if (refused) null else try {
                getCurrentProfile()
            } catch (checkError: Exception) {
                if (checkError is CancellationException) throw checkError
                null
            }
            if (current?.profileImageUrl != path) {
                // Kept when the profile couldn't be read: it may point here already.
                if (refused || current != null) runCatching { profileImages.delete(path) }
                throw error
            }
            current
        }

        if (!previousPath.isNullOrBlank() && previousPath != path) {
            runCatching { profileImages.delete(previousPath) }
        }
        return updated
    }

    /** A 1-hour signed URL for the user's own profile photo, or null. */
    suspend fun profilePhotoUrl(path: String?): String? = profileImages.signedUrlOrNull(path)

    /**
     * Copies the email_verified claim of the Firebase ID token into the profile
     * (sync_my_email_verified). The server reads the claim from the token only
     * and returns the stored value. The Firebase user is reloaded first. With
     * [forceRefresh], or when Firebase already says the email is confirmed but
     * the cached token doesn't, the token is refreshed before the call, so a
     * link opened a moment ago (or on another phone) counts.
     */
    suspend fun syncEmailVerified(forceRefresh: Boolean): Boolean {
        val user = firebaseAuth.currentUser ?: throw ProfileException.NotSignedIn
        user.reload().await()
        val claimVerified = user.getIdToken(false).await().claims["email_verified"] == true
        if (forceRefresh || (user.isEmailVerified && !claimVerified)) {
            user.getIdToken(true).await()
        }
        return supabase.postgrest.rpc("sync_my_email_verified").decodeAs()
    }

    /** Sends Firebase's verification email to the signed-in account's address. */
    suspend fun sendVerificationEmail() {
        val user = firebaseAuth.currentUser ?: throw ProfileException.NotSignedIn
        try {
            user.sendEmailVerification().await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw VerificationException(
                if (error is FirebaseTooManyRequestsException) {
                    TOO_MANY_ATTEMPTS
                } else {
                    "The verification email could not be sent. Check your internet connection and try again."
                }
            )
        }
    }

    /** True when the signed-in Firebase account has an email/password sign-in. */
    fun hasPassword(): Boolean =
        firebaseAuth.currentUser?.providerData.orEmpty()
            .any { it.providerId == EmailAuthProvider.PROVIDER_ID }

    /**
     * Checks [currentPassword] by reauthenticating, then sets [newPassword].
     * Throws [PasswordChangeException] with a message for the user.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = firebaseAuth.currentUser ?: throw ProfileException.NotSignedIn
        val email = user.email ?: throw ProfileException.MissingEmail

        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw PasswordChangeException(
                when (error) {
                    is FirebaseAuthInvalidCredentialsException -> "Your current password is incorrect."
                    is FirebaseTooManyRequestsException -> TOO_MANY_ATTEMPTS
                    else -> PASSWORD_NOT_CHANGED
                }
            )
        }

        try {
            user.updatePassword(newPassword).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw PasswordChangeException(
                // FirebaseAuthWeakPasswordException is a kind of invalid-credentials
                // error, so it is checked first; here it is never about the current password.
                when (error) {
                    is FirebaseAuthWeakPasswordException -> "Choose a stronger new password."
                    is FirebaseTooManyRequestsException -> TOO_MANY_ATTEMPTS
                    else -> PASSWORD_NOT_CHANGED
                }
            )
        }
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

/** The Edge Function that adds the Supabase role claim to the caller's own Firebase account. */
private const val SET_ROLE_FUNCTION = "set-firebase-role"

/** One role setup at a time: sign-in and the check when the signed-in user changes can overlap. */
private val roleSetupLock = Mutex()

/** Profile IDs by Firebase UID, for [UserRepository.getCurrentProfileId]; shared by every repository. */
private val profileIdsByUid = java.util.concurrent.ConcurrentHashMap<String, String>()

/** True when Firebase ID token claims carry the role Supabase requires. */
internal fun hasSupabaseRole(claims: Map<String, Any?>): Boolean = claims["role"] == "authenticated"

/**
 * The steps of [UserRepository.ensureSupabaseRole] without Firebase or Supabase,
 * so they can be unit tested. [readClaims] reads the ID token's claims (true =
 * refresh the token first) and [setRole] calls the Edge Function, only when both
 * the cached and a refreshed token lack the role. Every failure except
 * cancellation becomes [ProfileException.RoleSetupFailed]; the original
 * exception is never shown or logged (its text can include the Supabase URL).
 */
internal suspend fun ensureRole(
    readClaims: suspend (refresh: Boolean) -> Map<String, Any?>,
    setRole: suspend () -> Unit
) {
    try {
        if (hasSupabaseRole(readClaims(false))) return
        if (hasSupabaseRole(readClaims(true))) return
        setRole()
        if (hasSupabaseRole(readClaims(true))) return
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw ProfileException.RoleSetupFailed
    }
    throw ProfileException.RoleSetupFailed
}

private const val TOO_MANY_ATTEMPTS = "Too many attempts. Please wait a few minutes and try again."
private const val PASSWORD_NOT_CHANGED =
    "Your password could not be changed. Check your internet connection and try again."

class PasswordChangeException(message: String) : Exception(message)

/** A verification-email failure, with a message for the user. */
class VerificationException(message: String) : Exception(message)

/**
 * The short error text from Supabase (for example "Enter your name." raised by
 * the database), or [fallback] for network and other errors. Only the short
 * text is shown: the full exception message can include the URL and headers.
 */
fun supabaseErrorText(error: Throwable, fallback: String): String =
    (error as? RestException)?.error?.takeIf { it.isNotBlank() } ?: fallback

/**
 * [supabaseErrorText] as a sentence: a server message without a full stop
 * (for example "Food post is no longer available") gets one.
 */
fun shortErrorText(error: Throwable, fallback: String): String =
    supabaseErrorText(error, fallback).trim().let { text ->
        if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) text else "$text."
    }

sealed class ProfileException(message: String) : Exception(message) {
    data object NotSignedIn : ProfileException("No Firebase user is signed in.")
    data object MissingEmail : ProfileException("The Firebase account has no email address.")
    data object NotFound : ProfileException("No profile exists for this account.")
    data object RoleSetupFailed : ProfileException("The account's Supabase role could not be set up.")
    data object EmailInUse : ProfileException("An earlier profile uses this account's email address.")
}

fun profileErrorMessage(error: Throwable): String = when (error) {
    ProfileException.NotSignedIn ->
        "Please sign in before loading your profile."
    ProfileException.MissingEmail ->
        "Your account needs an email address before a profile can be created."
    ProfileException.NotFound ->
        "Your account profile could not be found."
    ProfileException.RoleSetupFailed ->
        "Your account is signed in, but Salu-Salo couldn't finish setting it up. " +
                "Check your internet connection, then tap Try again."
    ProfileException.EmailInUse ->
        "An earlier Salu-Salo profile uses this email address, for example from an account that was " +
                "deleted and created again. You can restore that profile, with its listings, " +
                "reservations and chats, to this account."
    else ->
        "We could not load your profile. Check your internet connection and try again."
}
package com.example.salusalo.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.salusalo.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

/**
 * Shows the Google account picker through Credential Manager and returns the
 * chosen account's Google ID token, for Firebase's GoogleAuthProvider.
 *
 * [activityContext] must be an Activity context. The server client ID is the
 * Firebase web client that the Google Services plugin generates from
 * google-services.json; it is never hard-coded.
 */
suspend fun requestGoogleIdToken(activityContext: Context): String {
    val option = GetSignInWithGoogleOption.Builder(
        activityContext.getString(R.string.default_web_client_id)
    ).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    val credential = CredentialManager.create(activityContext)
        .getCredential(activityContext, request)
        .credential

    if (
        credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw IllegalStateException("Unexpected credential type: ${credential.type}")
}

/** Forgets the account chosen in Credential Manager, so the next sign-in asks again. */
suspend fun clearGoogleCredentialState(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Best effort: signing out of Firebase already happened.
    }
}

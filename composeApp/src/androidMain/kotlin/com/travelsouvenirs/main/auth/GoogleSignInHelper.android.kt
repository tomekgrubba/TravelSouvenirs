package com.travelsouvenirs.main.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.travelsouvenirs.main.BuildConfig
import java.lang.ref.WeakReference

actual val isGoogleSignInAvailable: Boolean = true

class AndroidGoogleSignInHelper(activity: Activity) : GoogleSignInHelper {

    private val activityRef = WeakReference(activity)

    override suspend fun getIdToken(): String {
        val activity = activityRef.get()
            ?: error("Activity has been destroyed; cannot complete Google Sign-In")
        val credentialManager = CredentialManager.create(activity)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = credentialManager.getCredential(
            context = activity,
            request = request,
        )
        return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    }
}

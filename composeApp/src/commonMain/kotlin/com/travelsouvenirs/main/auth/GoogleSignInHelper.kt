package com.travelsouvenirs.main.auth

interface GoogleSignInHelper {
    /** Returns a Google ID token for use with Firebase Auth. */
    suspend fun getIdToken(): String
}

/** Whether Google Sign-In is available on this platform. */
expect val isGoogleSignInAvailable: Boolean

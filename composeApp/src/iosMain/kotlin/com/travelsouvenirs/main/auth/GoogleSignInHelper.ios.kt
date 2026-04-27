package com.travelsouvenirs.main.auth

actual val isGoogleSignInAvailable: Boolean = false

/** iOS Google Sign-In is not yet supported via Kotlin/Native. Use email/password instead. */
class IosGoogleSignInHelper : GoogleSignInHelper {
    override suspend fun getIdToken(): String =
        throw UnsupportedOperationException("Google Sign-In is not supported on iOS.")
}

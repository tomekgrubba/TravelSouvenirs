package com.travelsouvenirs.main.auth

import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    /** The currently signed-in user, or null if signed out. */
    val currentUser: StateFlow<FirebaseUser?>

    /** Signs in or creates an account using email + password. */
    suspend fun signInWithEmail(email: String, password: String): FirebaseUser

    /** Creates a new account using email + password. */
    suspend fun createAccount(email: String, password: String): FirebaseUser

    /** Signs in using a Google ID token. Not supported on all platforms. */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser

    suspend fun signOut()
}

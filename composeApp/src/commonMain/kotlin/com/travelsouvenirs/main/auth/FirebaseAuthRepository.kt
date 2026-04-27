package com.travelsouvenirs.main.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FirebaseAuthRepository : AuthRepository {

    private val auth = Firebase.auth
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val currentUser: StateFlow<FirebaseUser?> =
        auth.authStateChanged.stateIn(scope, SharingStarted.Eagerly, auth.currentUser)

    override suspend fun signInWithEmail(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email, password).user!!

    override suspend fun createAccount(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email, password).user!!

    override suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.credential(idToken, null)
        return auth.signInWithCredential(credential).user!!
    }

    override suspend fun signOut() = auth.signOut()
}

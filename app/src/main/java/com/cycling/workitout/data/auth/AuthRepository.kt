package com.cycling.workitout.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val currentUser: StateFlow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, firebaseAuth.currentUser?.toAuthUser())

    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> = runCatching {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        result.user!!.toAuthUser()
    }

    suspend fun createAccount(email: String, password: String): Result<AuthUser> = runCatching {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user!!.toAuthUser()
    }

    suspend fun signInAnonymously(): Result<AuthUser> = runCatching {
        val result = firebaseAuth.signInAnonymously().await()
        result.user!!.toAuthUser()
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    displayName = displayName,
    isAnonymous = isAnonymous
)
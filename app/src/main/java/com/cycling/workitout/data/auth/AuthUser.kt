package com.cycling.workitout.data.auth

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isAnonymous: Boolean
)

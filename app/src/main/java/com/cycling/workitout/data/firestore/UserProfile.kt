package com.cycling.workitout.data.firestore

enum class Units { METRIC, IMPERIAL }

data class UserProfile(
    val displayName: String? = null,
    val ftpWatts: Int = 200,
    val weightKg: Int = 75,
    val maxHr: Int = 190,
    val units: Units = Units.METRIC,
    val photoUrl: String? = null,
)

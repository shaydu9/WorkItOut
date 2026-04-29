package com.cycling.workitout.data.firestore

// Firestore-backed ride record. Doc id lives in `id`. Mirrors the fields the old
// CompletedRideEntity carried — same JSON-blob trick for per-second samples.
data class Ride(
    val id: String = "",
    val name: String = "",
    val startedAtMillis: Long = 0L,
    val durationSeconds: Int = 0,
    val avgPowerWatts: Int = 0,
    val maxPowerWatts: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgCadence: Int = 0,
    val normalizedPowerWatts: Int = 0,
    val ftpWatts: Int = 0,
    val dataPointsJson: String = "",
    val stravaActivityId: Long? = null,
    val stravaUploadedAtMillis: Long? = null,
)

data class SavedWorkout(
    val id: String = "",          // == Firestore doc id == canonical workoutId
    val name: String = "",
    val description: String = "",
    val totalDurationSeconds: Int = 0,
    val savedAtMillis: Long = 0L,
    val intervalsJson: String = "",
)

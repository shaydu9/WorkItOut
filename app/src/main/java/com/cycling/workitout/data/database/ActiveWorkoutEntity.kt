package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(
    @PrimaryKey val id: Int = 0,
    val workoutDefinitionId: String?, // null for free rides
    val workoutName: String,
    val startedAtMillis: Long,
    val plannedDurationSeconds: Int, // For the future make-up suggestion
    val ftpWatts: Int,
    // Same JSON shape as CompletedRideEntity
    val dataPointsJson: String,
    val lastCheckpointAtMillis: Long
)
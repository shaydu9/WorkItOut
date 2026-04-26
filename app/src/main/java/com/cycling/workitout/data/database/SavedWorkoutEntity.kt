package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// A saved workout definition — intervals stored as a JSON blob since they're written once and never mutated.
@Entity(tableName = "saved_workouts")
data class SavedWorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: String,
    val name: String,
    val description: String,
    val totalDurationSeconds: Int,
    val savedAtMillis: Long,
    // JSON list shaped like [{"d":300,"p":180,"n":"Warm-up","z":"Z2_ENDURANCE"}, …]
    val intervalsJson: String
)

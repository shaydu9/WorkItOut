package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-saved workout definition that can be replayed without regenerating.
 *
 * Intervals are stored as a JSON blob (serialized with kotlinx.serialization)
 * because they're an immutable list written once on save.
 */
@Entity(tableName = "saved_workouts")
data class SavedWorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Original WorkoutDefinition.id (UUID). Used to detect duplicates. */
    val workoutId: String,

    val name: String,
    val description: String,

    /** Total duration across all intervals, seconds. */
    val totalDurationSeconds: Int,

    /** Epoch millis when the user saved this workout. */
    val savedAtMillis: Long,

    /**
     * JSON-encoded list of intervals.
     * Shape: `[{"d":300,"p":180,"n":"Warm-up","z":"Z2_ENDURANCE"}, …]`
     */
    val intervalsJson: String
)

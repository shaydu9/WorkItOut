package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A completed workout ride, persisted so the user can review past sessions
 * from the History screen.
 *
 * The [dataPointsJson] column stores the list of [RecordedDataPoint]s as a
 * JSON string (serialized with kotlinx.serialization). This avoids a second
 * table + foreign keys for what is effectively an immutable blob written once
 * at workout completion.
 */
@Entity(tableName = "completed_rides")
data class CompletedRideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Workout name (from the WorkoutDefinition). */
    val name: String,

    /** Wall-clock epoch millis when the workout started. */
    val startedAtMillis: Long,

    /** Actual ride duration in seconds (paused time excluded). */
    val durationSeconds: Int,

    /** Average power over the ride, watts. */
    val avgPowerWatts: Int,

    /** Max instantaneous power recorded, watts. */
    val maxPowerWatts: Int,

    /** Average heart rate, bpm. 0 if no HR sensor was connected. */
    val avgHeartRate: Int,

    /** Max heart rate, bpm. */
    val maxHeartRate: Int,

    /** Average cadence, rpm. */
    val avgCadence: Int,

    /** Normalised Power estimate, watts. */
    val normalizedPowerWatts: Int,

    /** FTP that was active during this ride — needed to compute IF / TSS retroactively. */
    val ftpWatts: Int,

    /**
     * JSON-encoded list of recorded data points for the power/HR/cadence graph
     * on the detail screen.  Shape: `[{"t":0,"p":150,"tp":160,"hr":130,"c":85}, …]`
     */
    val dataPointsJson: String,

    /**
     * Strava activity id once this ride has been uploaded; null if it never has been.
     * Used by the history detail screen to (a) suppress double-uploads and
     * (b) deep-link to the activity on Strava.
     */
    val stravaActivityId: Long? = null,

    /** Wall-clock epoch millis when the Strava upload succeeded; null if never uploaded. */
    val stravaUploadedAtMillis: Long? = null
)

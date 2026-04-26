package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// One persisted ride for the History screen — data points are stored as a JSON blob to avoid a child table.
@Entity(tableName = "completed_rides")
data class CompletedRideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val avgPowerWatts: Int,
    val maxPowerWatts: Int,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val avgCadence: Int,
    val normalizedPowerWatts: Int,
    val ftpWatts: Int,
    // JSON list shaped like [{"t":0,"p":150,"tp":160,"hr":130,"c":85}, …]
    val dataPointsJson: String,
    val stravaActivityId: Long? = null,
    val stravaUploadedAtMillis: Long? = null
)

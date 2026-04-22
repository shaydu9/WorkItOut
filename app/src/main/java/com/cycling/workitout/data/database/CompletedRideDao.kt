package com.cycling.workitout.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedRideDao {

    /** All rides, most-recent first. */
    @Query("SELECT * FROM completed_rides ORDER BY startedAtMillis DESC")
    fun getAll(): Flow<List<CompletedRideEntity>>

    /** Single ride by primary key. */
    @Query("SELECT * FROM completed_rides WHERE id = :id")
    suspend fun getById(id: Long): CompletedRideEntity?

    @Insert
    suspend fun insert(ride: CompletedRideEntity): Long

    @Query("DELETE FROM completed_rides WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Stamp a successful Strava upload onto a ride so we don't double-upload it. */
    @Query("UPDATE completed_rides SET stravaActivityId = :activityId, stravaUploadedAtMillis = :uploadedAtMillis WHERE id = :rideId")
    suspend fun markStravaUploaded(rideId: Long, activityId: Long, uploadedAtMillis: Long)
}

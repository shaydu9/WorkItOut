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
}

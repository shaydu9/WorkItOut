package com.cycling.workitout.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActiveWorkoutDao {

    // Save (or overwrite) the current workout checkpoint
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActiveWorkoutEntity)

    // Get the last saved checkpoint, or null if none exists
    @Query("SELECT * FROM active_workout WHERE id = 0")
    suspend fun get(): ActiveWorkoutEntity?

    // Remove the checkpoint (called on clean finish or user discard)
    @Query("DELETE FROM active_workout")
    suspend fun clear()
}
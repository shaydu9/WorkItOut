package com.cycling.workitout.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWorkoutDao {

    /** All saved workouts, most-recently saved first. */
    @Query("SELECT * FROM saved_workouts ORDER BY savedAtMillis DESC")
    fun getAll(): Flow<List<SavedWorkoutEntity>>

    @Query("SELECT * FROM saved_workouts WHERE id = :id")
    suspend fun getById(id: Long): SavedWorkoutEntity?

    /** Check whether a workout with this definition id is already saved. */
    @Query("SELECT COUNT(*) > 0 FROM saved_workouts WHERE workoutId = :workoutId")
    suspend fun existsByWorkoutId(workoutId: String): Boolean

    @Insert
    suspend fun insert(workout: SavedWorkoutEntity): Long

    @Query("DELETE FROM saved_workouts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

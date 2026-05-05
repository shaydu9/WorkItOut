package com.cycling.workitout.data.workout

import com.cycling.workitout.data.database.ActiveWorkoutDao
import com.cycling.workitout.data.database.ActiveWorkoutEntity

class WorkoutCheckpointStore (
    private val dao: ActiveWorkoutDao
) {
    suspend fun save(entity: ActiveWorkoutEntity) { dao.upsert(entity) }

    suspend fun load(): ActiveWorkoutEntity? = dao.get()

    suspend fun clear() = dao.clear()
}
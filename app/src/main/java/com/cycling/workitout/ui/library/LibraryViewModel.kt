package com.cycling.workitout.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.database.SavedWorkoutEntity
import com.cycling.workitout.ui.home.toWorkoutDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val dao = WorkItOutApplication.database.savedWorkoutDao()

    val savedWorkouts: Flow<List<SavedWorkoutEntity>> = dao.getAll()

    private val _selectedWorkout = MutableStateFlow<WorkoutDefinition?>(null)
    val selectedWorkout: StateFlow<WorkoutDefinition?> = _selectedWorkout.asStateFlow()

    fun selectWorkout(entity: SavedWorkoutEntity) {
        _selectedWorkout.value = entity.toWorkoutDefinition()
    }

    fun dismissPreview() {
        _selectedWorkout.value = null
    }

    fun deleteWorkout(entity: SavedWorkoutEntity) {
        viewModelScope.launch {
            dao.deleteById(entity.id)
        }
    }
}

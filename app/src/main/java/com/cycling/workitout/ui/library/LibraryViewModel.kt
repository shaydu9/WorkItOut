package com.cycling.workitout.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.database.SavedWorkoutEntity
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.withFtp
import com.cycling.workitout.ui.home.CompactInterval
import com.cycling.workitout.ui.home.toWorkoutDefinition
import com.cycling.workitout.workout.LocalWorkoutGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class LibraryViewModel(
    private val preferences: ThemePreferences = WorkItOutApplication.themePreferences
) : ViewModel() {

    private val dao = WorkItOutApplication.database.savedWorkoutDao()

    val savedWorkouts: Flow<List<SavedWorkoutEntity>> = dao.getAll()

    /** Canonical FTP, streamed from prefs. */
    val ftp: StateFlow<Int> = preferences.userFtpWatts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    /** Whether targets render as percent of FTP (vs. raw watts). */
    val displayAsPercent: StateFlow<Boolean> = preferences.displayTargetsAsPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 5×4 grid of starter workouts, re-derived when FTP changes. */
    val defaultWorkouts: StateFlow<List<WorkoutDefinition>> = preferences.userFtpWatts
        .map { ftpWatts -> LocalWorkoutGenerator.getDefaultLibrary(ftpWatts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedWorkout = MutableStateFlow<WorkoutDefinition?>(null)
    val selectedWorkout: StateFlow<WorkoutDefinition?> = _selectedWorkout.asStateFlow()

    fun selectSavedWorkout(entity: SavedWorkoutEntity) {
        _selectedWorkout.value = entity.toWorkoutDefinition(ftp.value).withFtp(ftp.value)
    }

    fun selectDefaultWorkout(workout: WorkoutDefinition) {
        _selectedWorkout.value = workout
    }

    fun dismissPreview() {
        _selectedWorkout.value = null
    }

    fun deleteWorkout(entity: SavedWorkoutEntity) {
        viewModelScope.launch {
            dao.deleteById(entity.id)
        }
    }

    /** Persist a default workout into the user library. */
    fun saveToLibrary(workout: WorkoutDefinition) {
        viewModelScope.launch {
            if (dao.existsByWorkoutId(workout.id)) {
                Timber.d("Workout ${workout.id} already in library")
                return@launch
            }
            val entity = SavedWorkoutEntity(
                workoutId = workout.id,
                name = workout.name,
                description = workout.description,
                totalDurationSeconds = workout.totalDurationSeconds,
                savedAtMillis = System.currentTimeMillis(),
                intervalsJson = Json.encodeToString(workout.intervals.map {
                    CompactInterval(
                        d = it.durationSeconds,
                        p = it.targetPowerWatts,
                        n = it.name,
                        z = it.zone.name,
                        pp = it.targetPowerPercentFtp
                    )
                })
            )
            dao.insert(entity)
            Timber.i("Saved starter workout to library: ${workout.name}")
        }
    }

    fun setDisplayAsPercent(asPercent: Boolean) {
        viewModelScope.launch { preferences.setDisplayTargetsAsPercent(asPercent) }
    }
}

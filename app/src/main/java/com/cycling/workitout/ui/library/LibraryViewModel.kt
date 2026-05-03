package com.cycling.workitout.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.firestore.SavedWorkout
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

   private val workoutRepository = WorkItOutApplication.workoutRepository

    val savedWorkouts: Flow<List<SavedWorkout>> = workoutRepository.getSavedWorkouts()

    /** Canonical FTP, streamed from profile. */
    val ftp: StateFlow<Int> = WorkItOutApplication.userProfileRepository.profile
        .map { it.ftpWatts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    /** Whether targets render as percent of FTP (vs. raw watts). */
    val displayAsPercent: StateFlow<Boolean> = preferences.displayTargetsAsPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 5×4 grid of starter workouts, re-derived when FTP changes. */
    val defaultWorkouts: StateFlow<List<WorkoutDefinition>> =
        WorkItOutApplication.userProfileRepository.profile
            .map { LocalWorkoutGenerator.getDefaultLibrary(it.ftpWatts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedWorkout = MutableStateFlow<WorkoutDefinition?>(null)
    val selectedWorkout: StateFlow<WorkoutDefinition?> = _selectedWorkout.asStateFlow()

    fun selectSavedWorkout(entity: SavedWorkout) {
        _selectedWorkout.value = entity.toWorkoutDefinition(ftp.value).withFtp(ftp.value)
    }

    fun selectDefaultWorkout(workout: WorkoutDefinition) {
        _selectedWorkout.value = workout
    }

    fun dismissPreview() {
        _selectedWorkout.value = null
    }

    fun deleteWorkout(entity: SavedWorkout) {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(entity.id)
        }
    }

    /** Persist a default workout into the user library. */
    fun saveToLibrary(workout: WorkoutDefinition) {
        viewModelScope.launch {
            if (workoutRepository.existsByWorkoutId(workout.id)) {
                Timber.tag("WORKOUT").d("Workout ${workout.id} already in library")
                return@launch
            }
            val entity = SavedWorkout(
                id = workout.id,
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
            workoutRepository.saveWorkout(entity)
            Timber.tag("WORKOUT").i("Saved starter workout to library: ${workout.name}")
        }
    }

    fun setDisplayAsPercent(asPercent: Boolean) {
        viewModelScope.launch { preferences.setDisplayTargetsAsPercent(asPercent) }
    }
}

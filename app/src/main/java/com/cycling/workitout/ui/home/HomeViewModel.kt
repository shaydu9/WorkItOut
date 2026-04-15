package com.cycling.workitout.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.workout.LocalWorkoutGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MODERATE("Moderate"),
    HARD("Hard"),
    VO2("VO2")
}

data class HomeUiState(
    val durationMinutes: Int = 45,
    val difficulty: Difficulty = Difficulty.MODERATE,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val preview: WorkoutDefinition? = null
)

class HomeViewModel(
    private val bleManager: BleManager,
    private val preferences: ThemePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val ftp: StateFlow<Int> = preferences.userFtpWatts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    val isTrainerConnected: StateFlow<Boolean> = bleManager.isTrainerConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDemoMode: StateFlow<Boolean> = bleManager.isDemoMode

    fun setDuration(minutes: Int) {
        _uiState.value = _uiState.value.copy(durationMinutes = minutes)
    }

    fun setDifficulty(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    /**
     * Generate a workout for the selected duration + difficulty.
     * Phase E (current): returns the hardcoded demo workout as a stand-in.
     * Phase D will replace this body with a Claude API call.
     * On success, the workout is stored in `uiState.preview` for the user to confirm.
     */
    fun generateWorkout() {
        _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
        viewModelScope.launch {
            try {
                // TODO(Phase D): replace with AiWorkoutService.generateWorkout(...)
                val state = _uiState.value
                val workout = LocalWorkoutGenerator.generate(
                    durationMinutes = state.durationMinutes,
                    difficulty = state.difficulty,
                    ftp = ftp.value
                )
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    preview = workout
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = t.message ?: "Failed to generate workout"
                )
            }
        }
    }

    fun dismissPreview() {
        _uiState.value = _uiState.value.copy(preview = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

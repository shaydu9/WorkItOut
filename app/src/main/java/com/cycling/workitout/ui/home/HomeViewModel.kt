package com.cycling.workitout.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.ai.AiWorkoutService
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.workout.LocalWorkoutGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

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
    val preview: WorkoutDefinition? = null,
    val customPromptOpen: Boolean = false,
    val customPromptText: String = ""
)

class HomeViewModel(
    private val bleManager: BleManager,
    private val preferences: ThemePreferences
) : ViewModel() {

    private val aiService = AiWorkoutService()

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

    fun openCustomPrompt() {
        _uiState.value = _uiState.value.copy(customPromptOpen = true)
    }

    fun closeCustomPrompt() {
        _uiState.value = _uiState.value.copy(customPromptOpen = false)
    }

    fun setCustomPromptText(text: String) {
        _uiState.value = _uiState.value.copy(customPromptText = text)
    }

    /**
     * Generate a workout for the selected duration + difficulty via the Claude API,
     * falling back to the local procedural generator if the call fails.
     */
    fun generateWorkout() {
        _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
        viewModelScope.launch {
            val state = _uiState.value
            val workout = try {
                aiService.generateStructured(
                    durationMinutes = state.durationMinutes,
                    difficulty = state.difficulty,
                    ftp = ftp.value
                )
            } catch (t: Throwable) {
                Timber.w(t, "AI generation failed, using local fallback")
                _uiState.value = _uiState.value.copy(
                    error = "AI unavailable (${t.message}). Using local fallback."
                )
                LocalWorkoutGenerator.generate(
                    durationMinutes = state.durationMinutes,
                    difficulty = state.difficulty,
                    ftp = ftp.value
                )
            }
            _uiState.value = _uiState.value.copy(
                isGenerating = false,
                preview = workout
            )
        }
    }

    /**
     * Generate a workout from a freeform user prompt (typed or transcribed from voice).
     */
    fun generateCustomWorkout() {
        val prompt = _uiState.value.customPromptText.trim()
        if (prompt.isBlank()) return
        _uiState.value = _uiState.value.copy(
            isGenerating = true,
            error = null,
            customPromptOpen = false
        )
        viewModelScope.launch {
            try {
                val workout = aiService.generateFromPrompt(prompt, ftp.value)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    preview = workout,
                    customPromptText = ""
                )
            } catch (t: Throwable) {
                Timber.w(t, "AI custom generation failed")
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = t.message ?: "Failed to generate custom workout"
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

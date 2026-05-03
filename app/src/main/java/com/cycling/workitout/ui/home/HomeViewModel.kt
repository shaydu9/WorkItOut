package com.cycling.workitout.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.data.ai.AiWorkoutService
import com.cycling.workitout.data.firestore.SavedWorkout
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.workout.LocalWorkoutGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    val ftp: StateFlow<Int> = WorkItOutApplication.userProfileRepository.profile
        .map { it.ftpWatts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    val displayAsPercent: StateFlow<Boolean> = preferences.displayTargetsAsPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTrainerConnected: StateFlow<Boolean> = bleManager.isTrainerConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startScan() { viewModelScope.launch { bleManager.startScan() }}

    fun stopScan() { viewModelScope.launch { bleManager.stopScan() }}

    fun connectDevice(device: BleDevice) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.SMART_TRAINER -> bleManager.connectTrainer(device)
                DeviceType.HEART_RATE_MONITOR -> bleManager.connectHeartRateMonitor(device)
                else -> Unit
            }
        }
    }

    fun setDisplayAsPercent(asPercent: Boolean) {
        viewModelScope.launch { preferences.setDisplayTargetsAsPercent(asPercent) }
    }

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

    // Calls Claude; falls back to the local procedural generator if the API call fails.
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
                Timber.tag("AI").w(t, "AI generation failed, using local fallback")
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
                Timber.tag("AI").w(t, "AI custom generation failed")
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

    private val workoutRepository = WorkItOutApplication.workoutRepository

    fun saveWorkoutToLibrary() {
        val workout = _uiState.value.preview ?: return
        viewModelScope.launch {
            if (workoutRepository.existsByWorkoutId(workout.id)) {
                Timber.tag("WORKOUT").d("Workout ${workout.id} already saved")
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
            Timber.tag("WORKOUT").i("Saved workout to library: ${workout.name}")
        }
    }
}

// Compact JSON shape for intervals in the saved_workouts table — pp is null on pre-v2 rows.
@Serializable
data class CompactInterval(
    val d: Int,
    val p: Int,
    val n: String,
    val z: String,
    val pp: Float? = null
)

// Rebuilds a WorkoutDefinition from a saved row, back-filling %FTP from snapshot watts on legacy rows.
fun SavedWorkout.toWorkoutDefinition(currentFtp: Int): WorkoutDefinition {
    val intervals = Json.decodeFromString<List<CompactInterval>>(intervalsJson).map {
        val pct = it.pp
            ?: (if (currentFtp > 0) it.p.toFloat() / currentFtp.toFloat() else 0.65f)
        WorkoutIntervalDef(
            durationSeconds = it.d,
            targetPowerPercentFtp = pct,
            targetPowerWatts = it.p,
            name = it.n,
            zone = PowerZone.valueOf(it.z)
        )
    }
    return WorkoutDefinition(
        id = id,
        name = name,
        description = description,
        intervals = intervals
    )
}

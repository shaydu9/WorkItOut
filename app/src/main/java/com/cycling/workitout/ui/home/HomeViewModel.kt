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
import com.cycling.workitout.data.database.ActiveWorkoutEntity
import com.cycling.workitout.data.firestore.Ride
import com.cycling.workitout.data.firestore.SavedWorkout
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.workout.WorkoutCheckpointStore
import com.cycling.workitout.ui.workout.CompactDataPoint
import com.cycling.workitout.workout.LocalWorkoutGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import timber.log.Timber

private const val MIN_INTENSITY = 0.70f
private const val MAX_INTENSITY = 1.30f

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
    val customPromptText: String = "",
    val recoveryCheckpoint: ActiveWorkoutEntity? = null,
    // Intensity multiplier applied to the previewed workout. 1.0 = AI's original suggestion.
    // Adjusted via the −/+ pill in the preview sheet, reset on dismiss/regenerate.
    val intensityScale: Float = 1f,
)

// Captures the inputs to the last generation so the reload button can repeat it verbatim.
private sealed class LastGenRequest {
    data class Structured(val durationMinutes: Int, val difficulty: Difficulty) : LastGenRequest()
    data class Custom(val prompt: String) : LastGenRequest()
}

class HomeViewModel(
    private val bleManager: BleManager,
    private val preferences: ThemePreferences
) : ViewModel() {

    private val aiService = AiWorkoutService()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastGenRequest: LastGenRequest? = null

    private val checkpointStore = WorkoutCheckpointStore(
        WorkItOutApplication.database.activeWorkoutDao()
    )

    val ftp: StateFlow<Int> = WorkItOutApplication.userProfileRepository.profile
        .map { it.ftpWatts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    val displayAsPercent: StateFlow<Boolean> = preferences.displayTargetsAsPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTrainerConnected: StateFlow<Boolean> = bleManager.isTrainerConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCadenceSensorConnected: StateFlow<Boolean> = bleManager.isCadenceSensorConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val trainerProvidesCadence: StateFlow<Boolean> = bleManager.trainerProvidesCadence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startScan() { viewModelScope.launch { bleManager.startScan() }}

    fun stopScan() { viewModelScope.launch { bleManager.stopScan() }}

    init {
        viewModelScope.launch {
            val checkpoint = checkpointStore.load()
            if (checkpoint != null && checkpoint.dataPointsJson.length > 10) {
                // Rough check: decode and count points to filter out sub-30s rides
                val points = try {
                    Json.decodeFromString<List<CompactDataPoint>>(checkpoint.dataPointsJson)
                } catch (e: Exception) { emptyList() }
                if (points.size > 30) {
                    _uiState.value = _uiState.value.copy(recoveryCheckpoint = checkpoint)
                } else {
                    checkpointStore.clear() // Too short, silently discard
                }
            }
        }
    }

    fun reconnectSavedDevices() {
        viewModelScope.launch {
            val saved = WorkItOutApplication.deviceRepository.getAllDevices().first()
            saved.forEach { device ->
                Timber.tag("BLE").d("Reconnecting ${device.deviceType} ${device.macAddress}")
                val mac = device.macAddress
                when (device.deviceType) {
                    DeviceType.HEART_RATE_MONITOR -> bleManager.reconnectHeartRateMonitor(mac)
                    DeviceType.SMART_TRAINER -> bleManager.reconnectTrainer(mac)
                    DeviceType.POWER_METER -> bleManager.reconnectPowerMeter(mac)
                    DeviceType.CADENCE_SENSOR -> bleManager.reconnectCadenceSensor(mac)
                    else -> {}
                }
            }
        }
    }

    fun connectDevice(device: BleDevice) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.SMART_TRAINER -> bleManager.connectTrainer(device)
                DeviceType.HEART_RATE_MONITOR -> bleManager.connectHeartRateMonitor(device)
                DeviceType.CADENCE_SENSOR -> bleManager.connectCadenceSensor(device)
                else -> Unit
            }
            WorkItOutApplication.deviceRepository.saveDevice(device)
        }
    }

    fun dismissRecovery() {
        viewModelScope.launch {
            checkpointStore.clear()
            _uiState.value = _uiState.value.copy(recoveryCheckpoint = null)
        }
    }

    fun saveRecoverRide() {
        val checkpoint = _uiState.value.recoveryCheckpoint ?: return
        viewModelScope.launch {
            try {
                val points =
                    Json.decodeFromString<List<CompactDataPoint>>(checkpoint.dataPointsJson)
                if (points.isEmpty()) {
                    dismissRecovery()
                    return@launch
                }

                val power = points.map { it.p }
                val avgPower = power.average().toInt()
                val maxPower = power.max()
                val avgHr = points.map { it.hr }.average().toInt()
                val maxHr = points.maxOf { it.hr }
                val avgCadence = points.map { it.c }.average().toInt()
                val durationSec = points.lastOrNull()?.t ?: 0

                val ride = Ride(
                    name = checkpoint.workoutName,
                    startedAtMillis = checkpoint.startedAtMillis,
                    avgPowerWatts = avgPower,
                    maxPowerWatts = maxPower,
                    avgHeartRate = avgHr,
                    maxHeartRate = maxHr,
                    avgCadence = avgCadence,
                    durationSeconds = durationSec,
                    normalizedPowerWatts = 0, //NP needs raw 1-Hz data — compact points are already averaged
                    ftpWatts = checkpoint.ftpWatts,
                    dataPointsJson = checkpoint.dataPointsJson
                )

                val newId = WorkItOutApplication.rideRepository.saveRide(ride)
                checkpointStore.clear()
                _uiState.value = _uiState.value.copy(recoveryCheckpoint = null)
                Timber.tag("RECOVERY")
                    .i("Recovered ride saved: ${checkpoint.workoutName} (id=$newId)")

                // Trigger Strava auto-upload if enabled
                val autoUpload = preferences.autoUploadToStravaOnFinish.first()
                val stravaRepo = WorkItOutApplication.stravaRepository
                if (autoUpload && stravaRepo.isConnected.value) {
                    WorkItOutApplication.historyStravaUploader.upload(newId)
                }
            } catch (t: Throwable) {
                Timber.tag("RECOVERY").e(t, "Failed to save recovered ride")
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
        val state = _uiState.value
        val request = LastGenRequest.Structured(state.durationMinutes, state.difficulty)
        lastGenRequest = request
        runStructuredGeneration(request)
    }

    fun generateCustomWorkout() {
        val prompt = _uiState.value.customPromptText.trim()
        if (prompt.isBlank()) return
        val request = LastGenRequest.Custom(prompt)
        lastGenRequest = request
        _uiState.value = _uiState.value.copy(customPromptOpen = false, customPromptText = "")
        runCustomGeneration(request)
    }

    // Re-runs whichever generation produced the current preview. Resets the intensity pill —
    // a fresh suggestion shouldn't inherit the prior trim.
    fun regenerateWorkout() {
        val request = lastGenRequest ?: return
        _uiState.value = _uiState.value.copy(intensityScale = 1f)
        when (request) {
            is LastGenRequest.Structured -> runStructuredGeneration(request)
            is LastGenRequest.Custom -> runCustomGeneration(request)
        }
    }

    private fun runStructuredGeneration(request: LastGenRequest.Structured) {
        _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
        viewModelScope.launch {
            val workout = try {
                aiService.generateStructured(
                    durationMinutes = request.durationMinutes,
                    difficulty = request.difficulty,
                    ftp = ftp.value
                )
            } catch (t: Throwable) {
                Timber.tag("AI").w(t, "AI generation failed, using local fallback")
                _uiState.value = _uiState.value.copy(
                    error = "AI unavailable (${t.message}). Using local fallback."
                )
                LocalWorkoutGenerator.generate(
                    durationMinutes = request.durationMinutes,
                    difficulty = request.difficulty,
                    ftp = ftp.value
                )
            }
            _uiState.value = _uiState.value.copy(
                isGenerating = false,
                preview = workout
            )
        }
    }

    private fun runCustomGeneration(request: LastGenRequest.Custom) {
        _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
        viewModelScope.launch {
            try {
                val workout = aiService.generateFromPrompt(request.prompt, ftp.value)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    preview = workout
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

    // Bumps the preview intensity in 5% steps, clamped between 70% and 130%.
    fun adjustIntensity(deltaPercent: Int) {
        val current = _uiState.value.intensityScale
        val next = ((current * 100f).roundToInt() + deltaPercent) / 100f
        val clamped = next.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        if (clamped != current) {
            _uiState.value = _uiState.value.copy(intensityScale = clamped)
        }
    }

    fun dismissPreview() {
        _uiState.value = _uiState.value.copy(preview = null, intensityScale = 1f)
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

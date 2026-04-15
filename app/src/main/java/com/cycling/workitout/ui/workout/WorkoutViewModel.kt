package com.cycling.workitout.ui.workout

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.LiveMetrics
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutProgress
import com.cycling.workitout.data.export.WorkoutExporter
import com.cycling.workitout.ui.components.WorkoutInterval
import com.cycling.workitout.workout.WorkoutEngine
import com.cycling.workitout.workout.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class WorkoutViewModel(
    private val bleManager: BleManager,
    workoutDefinition: WorkoutDefinition? = null
) : ViewModel() {

    private val workoutEngine = WorkoutEngine(viewModelScope)

    val workoutProgress: StateFlow<WorkoutProgress> = workoutEngine.progress
    val recordedData: StateFlow<List<RecordedDataPoint>> = workoutEngine.recordedData

    val liveMetrics: StateFlow<LiveMetrics> = combine(
        bleManager.heartRateData,
        bleManager.powerData,
        bleManager.isHeartRateConnected,
        bleManager.isPowerMeterConnected
    ) { heartRateData, powerData, isHrConnected, isPowerConnected ->
        LiveMetrics(
            heartRate = heartRateData.heartRate,
            power = powerData.power,
            cadence = powerData.cadence,
            isHeartRateConnected = isHrConnected,
            isPowerMeterConnected = isPowerConnected
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LiveMetrics()
    )

    // UI-mapped intervals for the workout graph
    val workoutIntervals: List<WorkoutInterval>

    val isDemoMode: StateFlow<Boolean> = bleManager.isDemoMode

    // ERG mode toggle — when ON, target power is pushed to the trainer on each interval change.
    // When OFF, the workout timer continues but no FTMS writes happen.
    private val _ergEnabled = MutableStateFlow(true)
    val ergEnabled: StateFlow<Boolean> = _ergEnabled.asStateFlow()

    /** UI state for the post-workout .fit export. */
    sealed class ExportState {
        object Idle : ExportState()
        object InProgress : ExportState()
        data class Ready(val file: File) : ExportState()
        data class Failed(val message: String) : ExportState()
    }
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    init {
        val workout = workoutDefinition ?: WorkoutRepository.getDemoWorkout()
        workoutEngine.loadWorkout(workout)

        // Map to UI intervals for graphs
        workoutIntervals = workout.intervals.map {
            WorkoutInterval(
                durationSeconds = it.durationSeconds,
                targetPower = it.targetPowerWatts,
                name = it.name,
                color = Color(it.zone.colorHex)
            )
        }

        // Wire engine callbacks to BLE — gated by ERG toggle.
        workoutEngine.onTargetPowerChanged = { watts ->
            if (bleManager.isDemoMode.value) {
                bleManager.setDemoTargetPower(watts)
            } else if (_ergEnabled.value) {
                bleManager.setTargetPower(watts)
            }
        }

        workoutEngine.onWorkoutStarted = {
            if (!bleManager.isDemoMode.value && _ergEnabled.value) {
                bleManager.requestFtmsControl()
                bleManager.startFtmsWorkout()
            }
        }

        workoutEngine.onWorkoutStopped = {
            if (!bleManager.isDemoMode.value && _ergEnabled.value) {
                bleManager.stopFtmsWorkout()
            }
        }

        // Forward sensor data to engine for recording
        viewModelScope.launch {
            combine(
                bleManager.powerData,
                bleManager.heartRateData
            ) { power, hr -> Triple(power.power, hr.heartRate, power.cadence) }
                .collect { (power, hr, cadence) ->
                    workoutEngine.recordDataPoint(power, hr, cadence)
                }
        }
    }

    fun startWorkout() = workoutEngine.start()
    fun pauseWorkout() = workoutEngine.pause()
    fun resumeWorkout() = workoutEngine.resume()
    fun stopWorkout() = workoutEngine.stop()

    /**
     * Silently write the completed workout to a .fit file in app-private storage.
     * Idempotent — no-op if the file is already written for this session.
     * The resulting file is held in [exportState] as [ExportState.Ready] so the
     * post-workout UI can offer an "Upload to Strava" action against it.
     */
    fun exportFitSilently(context: Context) {
        if (_exportState.value is ExportState.Ready ||
            _exportState.value is ExportState.InProgress) return

        val workout = workoutEngine.workoutDefinition
        val startedAt = workoutEngine.workoutStartEpochMillis
        val records = workoutEngine.recordedData.value

        if (workout == null || startedAt == 0L) {
            _exportState.value = ExportState.Failed("No workout data to export.")
            return
        }

        _exportState.value = ExportState.InProgress
        viewModelScope.launch {
            try {
                val file = WorkoutExporter.exportToFit(
                    context = context.applicationContext,
                    workout = workout,
                    startEpochMillis = startedAt,
                    records = records
                )
                _exportState.value = ExportState.Ready(file)
                Timber.i("Workout auto-exported to ${file.absolutePath}")
            } catch (t: Throwable) {
                Timber.e(t, "Failed to export .fit")
                _exportState.value = ExportState.Failed(t.message ?: "Export failed")
            }
        }
    }

    /**
     * Toggle ERG mode. When turning OFF, release trainer control. When turning back ON,
     * re-acquire control and immediately push the current target so the trainer catches up.
     */
    fun setErgEnabled(enabled: Boolean) {
        if (_ergEnabled.value == enabled) return
        _ergEnabled.value = enabled
        if (bleManager.isDemoMode.value) return
        viewModelScope.launch {
            if (enabled) {
                bleManager.requestFtmsControl()
                bleManager.startFtmsWorkout()
                val current = workoutEngine.progress.value.targetPowerWatts
                if (current > 0) bleManager.setTargetPower(current)
            } else {
                bleManager.stopFtmsWorkout()
            }
        }
    }
}

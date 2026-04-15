package com.cycling.workitout.ui.workout

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.LiveMetrics
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutProgress
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

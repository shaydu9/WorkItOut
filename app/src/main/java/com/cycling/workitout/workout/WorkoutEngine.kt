package com.cycling.workitout.workout

import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutProgress
import com.cycling.workitout.data.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Core workout state machine that drives a structured workout.
 * Separated from ViewModel and BLE for testability.
 */
class WorkoutEngine(private val coroutineScope: CoroutineScope) {

    private val _progress = MutableStateFlow(WorkoutProgress())
    val progress: StateFlow<WorkoutProgress> = _progress.asStateFlow()

    private val _recordedData = MutableStateFlow<List<RecordedDataPoint>>(emptyList())
    val recordedData: StateFlow<List<RecordedDataPoint>> = _recordedData.asStateFlow()

    private var workout: WorkoutDefinition? = null
    private var tickJob: Job? = null
    private var startTimeMillis: Long = 0L

    /** Wall-clock epoch millis when the workout started (0 before start). */
    val workoutStartEpochMillis: Long get() = startTimeMillis
    val workoutDefinition: WorkoutDefinition? get() = workout
    private var pausedElapsedSeconds: Int = 0
    private var currentIntervalIndex: Int = 0
    private var intervalStartTotalSeconds: Int = 0

    // Callbacks for the ViewModel to wire to BLE
    var onTargetPowerChanged: ((Int) -> Unit)? = null
    var onWorkoutStarted: (() -> Unit)? = null
    var onWorkoutStopped: (() -> Unit)? = null

    fun loadWorkout(definition: WorkoutDefinition) {
        workout = definition
        currentIntervalIndex = 0
        intervalStartTotalSeconds = 0

        val firstInterval = definition.intervals.firstOrNull()
        _progress.value = WorkoutProgress(
            workoutState = WorkoutState.NOT_STARTED,
            workoutName = definition.name,
            currentIntervalIndex = 0,
            totalIntervals = definition.intervals.size,
            currentIntervalName = firstInterval?.name ?: "",
            intervalElapsedSeconds = 0,
            intervalRemainingSeconds = firstInterval?.durationSeconds ?: 0,
            intervalDurationSeconds = firstInterval?.durationSeconds ?: 0,
            totalElapsedSeconds = 0,
            totalRemainingSeconds = definition.totalDurationSeconds,
            totalDurationSeconds = definition.totalDurationSeconds,
            targetPowerWatts = firstInterval?.targetPowerWatts ?: 0,
            currentZone = firstInterval?.zone ?: com.cycling.workitout.data.PowerZone.Z1_RECOVERY
        )

        Timber.d("Workout loaded: ${definition.name} (${definition.intervals.size} intervals, ${definition.totalDurationSeconds}s)")
    }

    fun start() {
        val w = workout ?: return
        if (_progress.value.workoutState == WorkoutState.RUNNING) return

        Timber.d("Starting workout: ${w.name}")

        currentIntervalIndex = 0
        intervalStartTotalSeconds = 0
        pausedElapsedSeconds = 0
        startTimeMillis = System.currentTimeMillis()
        _recordedData.value = emptyList()

        val firstInterval = w.intervals.first()
        onWorkoutStarted?.invoke()
        onTargetPowerChanged?.invoke(firstInterval.targetPowerWatts)

        updateProgress(0)
        startTicking()
    }

    fun pause() {
        if (_progress.value.workoutState != WorkoutState.RUNNING) return

        Timber.d("Pausing workout")
        tickJob?.cancel()
        tickJob = null

        // Save elapsed time so we can resume from this point
        pausedElapsedSeconds = _progress.value.totalElapsedSeconds

        _progress.value = _progress.value.copy(workoutState = WorkoutState.PAUSED)
    }

    fun resume() {
        if (_progress.value.workoutState != WorkoutState.PAUSED) return

        Timber.d("Resuming workout")
        startTimeMillis = System.currentTimeMillis() - (pausedElapsedSeconds * 1000L)
        startTicking()
    }

    fun stop() {
        Timber.d("Stopping workout")
        tickJob?.cancel()
        tickJob = null

        _progress.value = _progress.value.copy(workoutState = WorkoutState.COMPLETED)
        onWorkoutStopped?.invoke()
    }

    /**
     * Record a data point from sensor data. Called by the ViewModel each time new data arrives.
     */
    fun recordDataPoint(power: Int, heartRate: Int, cadence: Int) {
        if (_progress.value.workoutState != WorkoutState.RUNNING) return

        val point = RecordedDataPoint(
            timeSeconds = _progress.value.totalElapsedSeconds,
            actualPower = power,
            targetPower = _progress.value.targetPowerWatts,
            heartRate = heartRate,
            cadence = cadence,
            epochMillis = System.currentTimeMillis()
        )

        val current = _recordedData.value.toMutableList()
        current.add(point)
        _recordedData.value = current
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = coroutineScope.launch {
            _progress.value = _progress.value.copy(workoutState = WorkoutState.RUNNING)
            while (true) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        val w = workout ?: return

        // Use wall-clock time for accuracy
        val totalElapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()

        // Check if workout is done
        if (totalElapsed >= w.totalDurationSeconds) {
            tickJob?.cancel()
            tickJob = null
            _progress.value = _progress.value.copy(
                workoutState = WorkoutState.COMPLETED,
                totalElapsedSeconds = w.totalDurationSeconds,
                totalRemainingSeconds = 0,
                intervalRemainingSeconds = 0
            )
            onWorkoutStopped?.invoke()
            Timber.d("Workout completed!")
            return
        }

        // Find current interval based on total elapsed time
        var cumulative = 0
        var newIntervalIndex = 0
        for ((index, interval) in w.intervals.withIndex()) {
            if (totalElapsed < cumulative + interval.durationSeconds) {
                newIntervalIndex = index
                break
            }
            cumulative += interval.durationSeconds
            if (index == w.intervals.lastIndex) {
                newIntervalIndex = index
            }
        }

        // Interval changed
        if (newIntervalIndex != currentIntervalIndex) {
            currentIntervalIndex = newIntervalIndex
            intervalStartTotalSeconds = cumulative

            val newInterval = w.intervals[currentIntervalIndex]
            onTargetPowerChanged?.invoke(newInterval.targetPowerWatts)
            Timber.d("Interval changed to: ${newInterval.name} (${newInterval.targetPowerWatts}W)")
        }

        updateProgress(totalElapsed)
    }

    private fun updateProgress(totalElapsed: Int) {
        val w = workout ?: return
        val interval = w.intervals.getOrNull(currentIntervalIndex) ?: return

        // Calculate cumulative seconds up to current interval
        var cumulative = 0
        for (i in 0 until currentIntervalIndex) {
            cumulative += w.intervals[i].durationSeconds
        }

        val intervalElapsed = totalElapsed - cumulative
        val intervalRemaining = (interval.durationSeconds - intervalElapsed).coerceAtLeast(0)

        _progress.value = WorkoutProgress(
            workoutState = _progress.value.workoutState,
            workoutName = w.name,
            currentIntervalIndex = currentIntervalIndex,
            totalIntervals = w.intervals.size,
            currentIntervalName = interval.name,
            intervalElapsedSeconds = intervalElapsed,
            intervalRemainingSeconds = intervalRemaining,
            intervalDurationSeconds = interval.durationSeconds,
            totalElapsedSeconds = totalElapsed,
            totalRemainingSeconds = (w.totalDurationSeconds - totalElapsed).coerceAtLeast(0),
            totalDurationSeconds = w.totalDurationSeconds,
            targetPowerWatts = interval.targetPowerWatts,
            currentZone = interval.zone
        )
    }
}

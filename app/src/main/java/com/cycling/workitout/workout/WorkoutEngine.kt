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

class WorkoutEngine(private val coroutineScope: CoroutineScope) {

    private val _progress = MutableStateFlow(WorkoutProgress())
    val progress: StateFlow<WorkoutProgress> = _progress.asStateFlow()

    // Append-only backing list — mutated under [backingLock] from the BLE callback thread.
    // Public consumers see immutable snapshots through [recordedData], pushed at ~1Hz from
    // the tick coroutine (and once more on stop/complete) so the UI doesn't churn the heap.
    private val backing = ArrayList<RecordedDataPoint>(4096)
    private val backingLock = Any()

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
    private var lastSummaryAtSecond: Int = 0

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
        cumulativeDistanceMeters = 0.0
        lastRecordedEpochMillis = 0L
        lastSummaryAtSecond = 0
        synchronized(backingLock) { backing.clear() }
        _recordedData.value = emptyList()
        Timber.i("▶ Workout start: ${w.name} — ${w.intervals.size} intervals, ${w.totalDurationSeconds / 60}:${"%02d".format(w.totalDurationSeconds % 60)} total")

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
        publishSnapshot()
        onWorkoutStopped?.invoke()
    }

    // Set by the ViewModel once the user's weight is loaded; null in tests.
    var speedEstimator: VirtualSpeedEstimator? = null

    /** Cumulative distance (meters) across this workout, incremented per recorded point. */
    private var cumulativeDistanceMeters: Double = 0.0
    private var lastRecordedEpochMillis: Long = 0L

    fun recordDataPoint(power: Int, heartRate: Int, cadence: Int) {
        if (_progress.value.workoutState != WorkoutState.RUNNING) return

        val now = System.currentTimeMillis()
        val speedMps = speedEstimator?.speedMpsFor(power) ?: 0f
        if (lastRecordedEpochMillis > 0L) {
            val dtSec = (now - lastRecordedEpochMillis).coerceIn(0, 5_000) / 1000.0
            cumulativeDistanceMeters += speedMps * dtSec
        }
        lastRecordedEpochMillis = now

        val point = RecordedDataPoint(
            timeSeconds = _progress.value.totalElapsedSeconds,
            actualPower = power,
            targetPower = _progress.value.targetPowerWatts,
            heartRate = heartRate,
            cadence = cadence,
            epochMillis = now,
            speedMps = speedMps,
            distanceMeters = cumulativeDistanceMeters.toFloat()
        )

        synchronized(backingLock) { backing.add(point) }
    }

    // Snapshot the backing list and push it to the public StateFlow. Cheap to call —
    // ArrayList(other) is O(n) but only runs once per second instead of per BLE sample.
    private fun publishSnapshot() {
        val snapshot: List<RecordedDataPoint> = synchronized(backingLock) {
            ArrayList(backing)
        }
        _recordedData.value = snapshot
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

        val totalElapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()

        if (totalElapsed >= w.totalDurationSeconds) {
            tickJob?.cancel()
            tickJob = null
            _progress.value = _progress.value.copy(
                workoutState = WorkoutState.COMPLETED,
                totalElapsedSeconds = w.totalDurationSeconds,
                totalRemainingSeconds = 0,
                intervalRemainingSeconds = 0
            )
            publishSnapshot()
            logFinalSummary(w)
            onWorkoutStopped?.invoke()
            return
        }

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

        if (newIntervalIndex != currentIntervalIndex) {
            currentIntervalIndex = newIntervalIndex
            intervalStartTotalSeconds = cumulative

            val newInterval = w.intervals[currentIntervalIndex]
            onTargetPowerChanged?.invoke(newInterval.targetPowerWatts)
            Timber.i("→ Interval ${currentIntervalIndex + 1}/${w.intervals.size}: ${newInterval.name} @ ${newInterval.targetPowerWatts}W (${newInterval.durationSeconds}s) [${fmtClock(totalElapsed)}]")
        }

        if (totalElapsed - lastSummaryAtSecond >= 60) {
            lastSummaryAtSecond = totalElapsed
            logMinuteSummary(totalElapsed)
        }

        publishSnapshot()
        updateProgress(totalElapsed)
    }

    private fun logMinuteSummary(totalElapsed: Int) {
        // Read the last ~60 samples directly from the backing list — avoids depending on the
        // throttled snapshot, which lags by up to 1s.
        val recent: List<RecordedDataPoint> = synchronized(backingLock) {
            if (backing.isEmpty()) emptyList()
            else backing.subList((backing.size - 60).coerceAtLeast(0), backing.size).toList()
        }
        if (recent.isEmpty()) return
        val avgPower = recent.map { it.actualPower }.average().toInt()
        val target = _progress.value.targetPowerWatts
        val avgHr = recent.mapNotNull { it.heartRate.takeIf { hr -> hr > 0 } }.average().let { if (it.isNaN()) 0 else it.toInt() }
        val avgCad = recent.mapNotNull { it.cadence.takeIf { c -> c > 0 } }.average().let { if (it.isNaN()) 0 else it.toInt() }
        val intervalName = _progress.value.currentIntervalName
        Timber.i("⏱ ${fmtClock(totalElapsed)} [$intervalName] target=${target}W actual=${avgPower}W hr=${avgHr} cad=${avgCad}")
    }

    private fun logFinalSummary(w: WorkoutDefinition) {
        val all: List<RecordedDataPoint> = synchronized(backingLock) { ArrayList(backing) }
        if (all.isEmpty()) {
            Timber.i("■ Workout complete (no samples recorded)")
            return
        }
        val powers = all.map { it.actualPower }.filter { it > 0 }
        val hrs = all.map { it.heartRate }.filter { it > 0 }
        val cads = all.map { it.cadence }.filter { it > 0 }
        val distance = all.last().distanceMeters

        Timber.i("■ Workout complete: ${w.name}")
        Timber.i("   duration=${fmtClock(w.totalDurationSeconds)} samples=${all.size} distance=${"%.2f".format(distance / 1000f)}km")
        if (powers.isNotEmpty()) {
            Timber.i("   power: avg=${powers.average().toInt()}W max=${powers.max()}W")
        }
        if (hrs.isNotEmpty()) {
            Timber.i("   hr: avg=${hrs.average().toInt()} max=${hrs.max()}")
        }
        if (cads.isNotEmpty()) {
            Timber.i("   cadence: avg=${cads.average().toInt()} max=${cads.max()}")
        }

        var cursor = 0
        for ((idx, interval) in w.intervals.withIndex()) {
            val endSec = cursor + interval.durationSeconds
            val slice = all.filter { it.timeSeconds in cursor until endSec }
            val sliceAvgP = slice.map { it.actualPower }.filter { it > 0 }.average().let { if (it.isNaN()) 0 else it.toInt() }
            val sliceAvgH = slice.map { it.heartRate }.filter { it > 0 }.average().let { if (it.isNaN()) 0 else it.toInt() }
            Timber.i("   #${idx + 1} ${interval.name}: target=${interval.targetPowerWatts}W actual=${sliceAvgP}W hr=$sliceAvgH (${interval.durationSeconds}s)")
            cursor = endSec
        }
    }

    private fun fmtClock(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun updateProgress(totalElapsed: Int) {
        val w = workout ?: return
        val interval = w.intervals.getOrNull(currentIntervalIndex) ?: return

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

package com.cycling.workitout.ui.workout

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.LiveMetrics
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutProgress
import com.cycling.workitout.data.WorkoutState
import com.cycling.workitout.data.firestore.Ride
import com.cycling.workitout.data.export.WorkoutExporter
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.strava.StravaRepository
import com.cycling.workitout.ui.components.WorkoutInterval
import com.cycling.workitout.workout.VirtualSpeedEstimator
import com.cycling.workitout.workout.WorkoutEngine
import com.cycling.workitout.workout.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class WorkoutViewModel(
    private val bleManager: BleManager,
    workoutDefinition: WorkoutDefinition? = null,
    private val stravaRepository: StravaRepository = WorkItOutApplication.stravaRepository,
    private val themePreferences: ThemePreferences = WorkItOutApplication.themePreferences
) : ViewModel() {

    private val workoutEngine = WorkoutEngine(viewModelScope)

    // Single-owner token for trainer ERG writes. Acquiring here invalidates any prior
    // owner (e.g. a leaked previous WorkoutViewModel still on the back stack), so its
    // setTargetPower / resender calls are dropped at the BLE layer instead of fighting us.
    private val ergToken: Any = bleManager.acquireErgControl()

    init {
        Timber.tag("ERG").d(
            "WorkoutViewModel init: vm=${System.identityHashCode(this)} " +
            "token=${System.identityHashCode(ergToken)}"
        )
    }

    // ── Strava integration ────────────────────────────────────────────
    val stravaConnected: StateFlow<Boolean> = stravaRepository.isConnected
    val stravaUploadState: StateFlow<StravaRepository.UploadState> = stravaRepository.uploadState

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

    // ── Display preference (W vs. % FTP) ──────────────────────────────
    val displayAsPercent: StateFlow<Boolean> = themePreferences.displayTargetsAsPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** User's current FTP — used to compute live power as a %-FTP on the active-workout UI. */
    val currentFtp: StateFlow<Int> = WorkItOutApplication.userProfileRepository.profile
        .map { it.ftpWatts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    fun setDisplayAsPercent(asPercent: Boolean) {
        viewModelScope.launch { themePreferences.setDisplayTargetsAsPercent(asPercent) }
    }

    // ERG mode toggle — when ON, target power is pushed to the trainer on each interval change.
    // When OFF, the workout timer continues but no FTMS writes happen.
    private val _ergEnabled = MutableStateFlow(true)
    val ergEnabled: StateFlow<Boolean> = _ergEnabled.asStateFlow()

    // True for ~1.5s while the watchdog auto-rearms ERG; UI shows a transient "Re-arming…" hint.
    private val _ergRearming = MutableStateFlow(false)
    val ergRearming: StateFlow<Boolean> = _ergRearming.asStateFlow()

    // Wall-clock of last interval transition — watchdog suppresses near boundaries while the
    // trainer ramps to the new target.
    @Volatile private var lastIntervalTransitionMs: Long = 0L

    // Sliding window of recent re-arm timestamps — caps the watchdog at MAX_REARMS_PER_30S.
    private val rearmTimestamps = ArrayDeque<Long>()

    // Countdown state: wait for first pedal → count 5..1 → start. Gives the trainer time to latch ERG.
    sealed class StartupState {
        object Idle : StartupState()
        object Waiting : StartupState()
        data class Counting(val secondsLeft: Int) : StartupState()
    }
    private val _startupState = MutableStateFlow<StartupState>(StartupState.Idle)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    /** UI state for the post-workout .fit export. */
    sealed class ExportState {
        object Idle : ExportState()
        object InProgress : ExportState()
        data class Ready(val file: File) : ExportState()
        data class Failed(val message: String) : ExportState()
    }
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** Tracks whether we've already persisted this ride so we don't double-save. */
    private var rideSaved = false

    // Non-null once the ride is saved; triggers navigation to the ride detail screen.
    private val _savedRideId = MutableStateFlow<String?>(null)
    val savedRideId: StateFlow<String?> = _savedRideId.asStateFlow()

    init {
        stravaRepository.resetUploadState()

        val workout = workoutDefinition ?: WorkoutRepository.getDemoWorkout()
        workoutEngine.loadWorkout(workout)

        // Speed/distance go into the .fit file for Strava; the live UI doesn't show them.
        viewModelScope.launch {
            val weight = themePreferences.userWeightKg.first()
            workoutEngine.speedEstimator = VirtualSpeedEstimator(riderWeightKg = weight.toDouble())
        }

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
            lastIntervalTransitionMs = System.currentTimeMillis()
            if (_ergEnabled.value) {
                bleManager.setTargetPower(ergToken, watts)
            }
        }

        workoutEngine.onWorkoutStarted = {
            bleManager.setWorkoutActive(true)
            if (_ergEnabled.value) {
                bleManager.requestFtmsControl(ergToken)
                bleManager.startFtmsWorkout(ergToken)
            }
        }

        workoutEngine.onWorkoutStopped = {
            if (_ergEnabled.value) {
                bleManager.stopFtmsWorkout(ergToken)
            }
            bleManager.setWorkoutActive(false)
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

        // Pre-send the first target on screen entry so the trainer is already locked by the time the user hits Start.
        viewModelScope.launch {
            if (_ergEnabled.value) {
                val firstTarget = workoutEngine.progress.value.targetPowerWatts
                if (firstTarget > 0) {
                    bleManager.requestFtmsControl(ergToken)
                    bleManager.setTargetPower(ergToken, firstTarget)
                    Timber.tag("ERG").i("Pre-sent first interval target $firstTarget W on screen entry")
                }
            }
        }

        // Auto-arm on entry so the "Start pedaling" prompt is the first thing the rider sees.
        viewModelScope.launch {
            runStartupCountdown()
            _startupState.value = StartupState.Idle
            workoutEngine.start()
        }

        // ERG watchdog — runs for the lifetime of the VM, only acts while a workout is RUNNING
        // and ERG is enabled. Cheap (1 Hz tick reading already-cached StateFlow values).
        viewModelScope.launch { runErgWatchdog() }
    }

    fun startWorkout() {
        if (_startupState.value != StartupState.Idle) return
        viewModelScope.launch {
            // Reopen the burst window so the trainer gets fresh frames during the countdown.
            if (_ergEnabled.value) {
                val firstTarget = workoutEngine.progress.value.targetPowerWatts
                if (firstTarget > 0) {
                    bleManager.setTargetPower(ergToken, firstTarget)
                    Timber.tag("ERG").i("Startup: reopened burst for first target $firstTarget W")
                }
            }
            runStartupCountdown()
            _startupState.value = StartupState.Idle
            workoutEngine.start()
        }
    }

    // Waits for a pedal stroke, counts 5..1 uninterrupted — resets if the rider stops mid-count.
    private suspend fun runStartupCountdown() {
        val cadenceFlow = bleManager.powerData.map { it.cadence }
        while (true) {
            _startupState.value = StartupState.Waiting
            cadenceFlow.first { it > 0 }

            var aborted = false
            for (sec in 5 downTo 1) {
                _startupState.value = StartupState.Counting(sec)
                // Wait one second, but bail out early if cadence drops to 0.
                val stopped = withTimeoutOrNull(1_000L) {
                    cadenceFlow.first { it == 0 }
                }
                if (stopped != null) {
                    Timber.tag("ERG").i("Startup countdown aborted at $sec — rider stopped pedaling")
                    aborted = true
                    break
                }
            }
            if (!aborted) return
        }
    }

    fun pauseWorkout() = workoutEngine.pause()
    fun resumeWorkout() = workoutEngine.resume()
    fun stopWorkout() = workoutEngine.stop()

    // Idempotent — skips if already written this session.
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
                Timber.tag("FIT_EXPORT").i("Workout auto-exported to ${file.absolutePath}")
            } catch (t: Throwable) {
                Timber.tag("FIT_EXPORT").e(t, "Failed to export .fit")
                _exportState.value = ExportState.Failed(t.message ?: "Export failed")
            }
        }
    }

    fun uploadExportedFitToStrava() {
        val ready = _exportState.value as? ExportState.Ready ?: return
        val workoutName = workoutEngine.workoutDefinition?.name ?: "WorkItOut session"
        stravaRepository.uploadFit(ready.file, workoutName)
    }

    // Computes summary stats and serializes data points to JSON, then persists to Room.
    fun saveRideToHistory() {
        if (rideSaved) return
        val workout = workoutEngine.workoutDefinition ?: return
        val startedAt = workoutEngine.workoutStartEpochMillis
        val records = workoutEngine.recordedData.value
        if (startedAt == 0L || records.isEmpty()) return
        rideSaved = true

        viewModelScope.launch {
            try {
                val ftp = WorkItOutApplication.userProfileRepository.profile.value.ftpWatts
                val powers = records.map { it.actualPower }
                val avgPower = powers.average().toInt()
                val maxPower = powers.max()
                val avgHr = records.map { it.heartRate }.average().toInt()
                val maxHr = records.maxOf { it.heartRate }
                val avgCadence = records.map { it.cadence }.average().toInt()

                val np = computeNormalizedPower(powers)
                val durationSec = records.lastOrNull()?.timeSeconds ?: 0

                // Collapse to 1 Hz: the recorder emits several rows per second (sensors
                // arrive at different rates), but the detail chart's X-axis is whole seconds,
                // so duplicate-second rows would just inflate the Firestore payload past its
                // 1 MB per-field cap. Averaging within each second is lossless for the chart.
                val compactPoints = records
                    .groupBy { it.timeSeconds }
                    .entries
                    .sortedBy { it.key }
                    .map { (second, group) ->
                        CompactDataPoint(
                            t = second,
                            p = group.map { it.actualPower }.average().roundToInt(),
                            tp = group.last().targetPower,
                            hr = group.map { it.heartRate }.average().roundToInt(),
                            c = group.map { it.cadence }.average().roundToInt(),
                        )
                    }
                val json = Json.encodeToString(compactPoints)

                val entity = Ride(
                    name = workout.name,
                    startedAtMillis = startedAt,
                    durationSeconds = durationSec,
                    avgPowerWatts = avgPower,
                    maxPowerWatts = maxPower,
                    avgHeartRate = avgHr,
                    maxHeartRate = maxHr,
                    avgCadence = avgCadence,
                    normalizedPowerWatts = np,
                    ftpWatts = ftp,
                    dataPointsJson = json
                )
                val newId = WorkItOutApplication.rideRepository.saveRide(entity)
                _savedRideId.value = newId
                Timber.tag("WORKOUT").i("Ride saved to history: ${workout.name} (id=$newId)")

                val autoUpload = themePreferences.autoUploadToStravaOnFinish.first()
                if (autoUpload && stravaRepository.isConnected.value) {
                    // Wait for the .fit export before uploading — otherwise OkHttp sends a partial file.
                    Timber.tag("WORKOUT").i("Auto-upload enabled — waiting for .fit export to finish for ride $newId")
                    val terminal = withTimeoutOrNull(30_000L) {
                        exportState.first {
                            it is ExportState.Ready || it is ExportState.Failed
                        }
                    }
                    if (terminal is ExportState.Failed) {
                        Timber.tag("WORKOUT").w("Silent export failed — auto-upload will regenerate the .fit")
                    } else if (terminal == null) {
                        Timber.tag("WORKOUT").w("Timed out waiting for .fit export; uploader will regenerate")
                    }
                    Timber.tag("WORKOUT").i("Kicking history upload for ride $newId")
                    WorkItOutApplication.historyStravaUploader.upload(newId)
                }
            } catch (t: Throwable) {
                Timber.tag("WORKOUT").e(t, "Failed to save ride to history")
            }
        }
    }

    // ERG watchdog: detects "trainer locked to wrong target" (Mode A) and "trainer dropped to
    // freewheel" (Mode B). On confirmed deviation, fires the same off→on cycle the rider would
    // do manually — that's known-good and produces a fresh burst of Page 49 frames at 3 Hz.
    //
    // Tunables are conservative on purpose: we'd rather miss the first second of a real drop
    // than auto-rearm on a sprint or an interval-edge ramp.
    private suspend fun runErgWatchdog() {
        val deviationFraction = 0.20            // ±20% off target counts as deviation
        val sustainedSeconds = 2                // need 2 consecutive seconds before firing
        val transitionSuppressMs = 5_000L       // skip the trainer's own ramp after each interval
        val minCadence = 30                     // ignore coast/stop seconds
        val rearmCooldownMs = 5_000L            // don't rearm again until at least this elapsed
        val maxRearmsPer30s = 3                 // back off if we keep firing — likely a real fault

        var consecutiveDeviationSec = 0
        var lastRearmMs = 0L

        while (true) {
            delay(1_000L)

            if (!_ergEnabled.value) { consecutiveDeviationSec = 0; continue }
            if (workoutEngine.progress.value.workoutState != WorkoutState.RUNNING) {
                consecutiveDeviationSec = 0; continue
            }

            val now = System.currentTimeMillis()
            if (now - lastIntervalTransitionMs < transitionSuppressMs) {
                consecutiveDeviationSec = 0; continue
            }

            val target = workoutEngine.progress.value.targetPowerWatts
            if (target <= 0) { consecutiveDeviationSec = 0; continue }

            val live = bleManager.powerData.value
            if (live.cadence < minCadence) { consecutiveDeviationSec = 0; continue }

            val deviation = abs(live.power - target).toDouble() / target.toDouble()
            if (deviation <= deviationFraction) {
                consecutiveDeviationSec = 0
                continue
            }

            consecutiveDeviationSec++
            if (consecutiveDeviationSec < sustainedSeconds) continue

            if (now - lastRearmMs < rearmCooldownMs) continue

            // Rate limit: drop expired entries, bail if still over budget.
            while (rearmTimestamps.isNotEmpty() && now - rearmTimestamps.first() > 30_000L) {
                rearmTimestamps.removeFirst()
            }
            if (rearmTimestamps.size >= maxRearmsPer30s) {
                Timber.tag("ERG").w("ERG watchdog: over budget ($maxRearmsPer30s rearms in 30s) — backing off")
                consecutiveDeviationSec = 0
                continue
            }

            Timber.tag("ERG").w("ERG watchdog: actual=${live.power}W target=${target}W (${(deviation * 100).toInt()}% off, ${consecutiveDeviationSec}s) — auto-rearming")
            rearmTimestamps.addLast(now)
            lastRearmMs = now
            consecutiveDeviationSec = 0
            rearmErg(target)
        }
    }

    // The toggle off→on cycle the rider would do by hand. For FE-C this is:
    //   stopFtmsWorkout()  → sends Page 49 target=0W, stops resender
    //   delay 250ms        → trainer registers the stop
    //   setTargetPower()   → fresh Page 49 with the current target, restarts 3 Hz resender
    private fun rearmErg(targetWatts: Int) {
        viewModelScope.launch {
            _ergRearming.value = true
            try {
                bleManager.stopFtmsWorkout(ergToken)
                delay(250L)
                bleManager.requestFtmsControl(ergToken)
                bleManager.startFtmsWorkout(ergToken)
                bleManager.setTargetPower(ergToken, targetWatts)
                // Keep the UI hint up for a moment so the rider sees the app handled it.
                delay(1_500L)
            } finally {
                _ergRearming.value = false
            }
        }
    }

    // Tear down on VM destruction — critical so a navigated-away workout stops driving the
    // trainer. Order matters: drop engine callbacks before releasing the BLE token, so any
    // in-flight tick can't fire a setTargetPower after release.
    override fun onCleared() {
        super.onCleared()
        workoutEngine.onTargetPowerChanged = null
        workoutEngine.onWorkoutStarted = null
        workoutEngine.onWorkoutStopped = null
        // releaseErgControl() sends the trainer to freewheel and stops the FE-C resender.
        // Idempotent — safe even if a newer VM has already preempted us.
        bleManager.releaseErgControl(ergToken)
        bleManager.setWorkoutActive(false)
        Timber.tag("ERG").d("WorkoutViewModel cleared (vm=${System.identityHashCode(this)}) — released, engine callbacks unwired")
    }

    // When re-enabling ERG, re-acquire control and push the current target immediately.
    fun setErgEnabled(enabled: Boolean) {
        if (_ergEnabled.value == enabled) return
        _ergEnabled.value = enabled
        viewModelScope.launch {
            if (enabled) {
                bleManager.requestFtmsControl(ergToken)
                bleManager.startFtmsWorkout(ergToken)
                val current = workoutEngine.progress.value.targetPowerWatts
                if (current > 0) bleManager.setTargetPower(ergToken, current)
            } else {
                bleManager.stopFtmsWorkout(ergToken)
            }
        }
    }
}

/** Compact serializable data point for the JSON blob stored in completed_rides. */
@Serializable
data class CompactDataPoint(
    val t: Int,   // timeSeconds
    val p: Int,   // actualPower
    val tp: Int,  // targetPower
    val hr: Int,  // heartRate
    val c: Int    // cadence
)

// NP formula: 30s rolling average → 4th power → mean → 4th root.
private fun computeNormalizedPower(powers: List<Int>): Int {
    if (powers.size < 30) return powers.average().toInt()
    val rolling = powers.windowed(30) { window -> window.average() }
    val fourthPowerMean = rolling.map { it.pow(4.0) }.average()
    return fourthPowerMean.pow(0.25).toInt()
}

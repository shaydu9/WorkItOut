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
import com.cycling.workitout.data.database.CompletedRideEntity
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

class WorkoutViewModel(
    private val bleManager: BleManager,
    workoutDefinition: WorkoutDefinition? = null,
    private val stravaRepository: StravaRepository = WorkItOutApplication.stravaRepository,
    private val themePreferences: ThemePreferences = WorkItOutApplication.themePreferences
) : ViewModel() {

    private val workoutEngine = WorkoutEngine(viewModelScope)

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

    val isDemoMode: StateFlow<Boolean> = bleManager.isDemoMode

    // ── Display preference (W vs. % FTP) ──────────────────────────────
    val displayAsPercent: StateFlow<Boolean> = themePreferences.displayTargetsAsPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** User's current FTP — used to compute live power as a %-FTP on the active-workout UI. */
    val currentFtp: StateFlow<Int> = themePreferences.userFtpWatts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    fun setDisplayAsPercent(asPercent: Boolean) {
        viewModelScope.launch { themePreferences.setDisplayTargetsAsPercent(asPercent) }
    }

    // ERG mode toggle — when ON, target power is pushed to the trainer on each interval change.
    // When OFF, the workout timer continues but no FTMS writes happen.
    private val _ergEnabled = MutableStateFlow(true)
    val ergEnabled: StateFlow<Boolean> = _ergEnabled.asStateFlow()

    /**
     * Pre-start UX: when the user taps Play we don't call WorkoutEngine.start() immediately.
     * Instead we wait for the first pedal stroke, then run a 5s countdown. This gives the
     * Tacx trainer time to fully latch ERG while the rider is already spinning up to tempo,
     * avoiding the ~10s cold-start overshoot we used to see (raw power spiking to ~2× target
     * for the first few seconds of recording).
     *
     * Rationale for 5s: FE-C burst ~3s to latch + rider spin-up 3-4s + reaction time.
     * Matches the TrainerRoad convention, so users with muscle memory feel at home.
     *
     * Idle     → user hasn't pressed Play yet (or workout is already running/done)
     * Waiting  → Play pressed, waiting for first non-zero cadence
     * Counting → pedaling detected, counting down 5..1. If cadence drops to 0 we reset
     *            back to Waiting (no point counting down if the rider stopped).
     *
     * Demo mode skips the entire flow — we don't have a real trainer to latch.
     */
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

    /**
     * Row id of the just-saved ride. Non-null once [saveRideToHistory] has succeeded.
     * The Compose layer observes this and navigates to the ride-detail screen so the
     * user lands on the same screen they'd see from History — completing the loop.
     */
    private val _savedRideId = MutableStateFlow<Long?>(null)
    val savedRideId: StateFlow<Long?> = _savedRideId.asStateFlow()

    init {
        // Fresh workout session — clear any stale upload result from the last run.
        stravaRepository.resetUploadState()

        val workout = workoutDefinition ?: WorkoutRepository.getDemoWorkout()
        workoutEngine.loadWorkout(workout)

        // Pull the user's body weight once and build the virtual-speed estimator.
        // Speed + distance are written into the exported .fit file so Strava /
        // Garmin show a realistic "Virtual Ride" with distance and avg speed;
        // the live UI never surfaces them.
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
            if (bleManager.isDemoMode.value) {
                bleManager.setDemoTargetPower(watts)
            } else if (_ergEnabled.value) {
                bleManager.setTargetPower(watts)
            }
        }

        workoutEngine.onWorkoutStarted = {
            // Flip BLE into verbose-logging mode: per-sample sensor reads and
            // per-write ACKs are only useful mid-ride, so the manager keeps
            // quiet until a workout is actually running.
            bleManager.setWorkoutActive(true)
            if (!bleManager.isDemoMode.value && _ergEnabled.value) {
                bleManager.requestFtmsControl()
                bleManager.startFtmsWorkout()
            }
        }

        workoutEngine.onWorkoutStopped = {
            if (!bleManager.isDemoMode.value && _ergEnabled.value) {
                bleManager.stopFtmsWorkout()
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

        // ── Pre-send the first interval target as soon as the screen opens ──
        // Previously we waited until the user tapped Start before sending any
        // Page 49 frame. On Tacx FE-C trainers that means the first pedal
        // stroke races the trainer's ERG latch — the rider often gets 20-30s
        // of overshoot before the target is actually honored. By pushing the
        // target on screen entry, combined with the FE-C burst window in
        // BleManager.setTargetPower, the trainer is already locked into ERG
        // by the time the user hits Start.
        viewModelScope.launch {
            if (!bleManager.isDemoMode.value && _ergEnabled.value) {
                val firstTarget = workoutEngine.progress.value.targetPowerWatts
                if (firstTarget > 0) {
                    bleManager.requestFtmsControl()
                    bleManager.setTargetPower(firstTarget)
                    Timber.i("Pre-sent first interval target $firstTarget W on screen entry")
                }
            }
        }

        // ── Auto-arm the startup countdown on screen entry (non-demo) ──
        // Historically the countdown only ran after the Play tap. In practice
        // riders clip in, start pedaling, and expect the workout to begin —
        // matches Zwift / TrainerRoad muscle memory. We move straight into
        // Waiting so the "START PEDALING" overlay is the first thing users
        // see; the first pedal stroke triggers the 5s countdown, which runs
        // WorkoutEngine.start() at zero. The Play button still works (demo
        // mode needs it), but it's redundant for a real ride.
        if (!bleManager.isDemoMode.value) {
            viewModelScope.launch {
                runStartupCountdown()
                _startupState.value = StartupState.Idle
                workoutEngine.start()
            }
        }
    }

    /**
     * User tapped Play. In demo mode, start immediately. Otherwise run the pre-start
     * flow: wait for first pedal stroke, then 5s countdown, then kick the engine.
     * Re-entrant guard: if we're already in Waiting/Counting, ignore further presses.
     */
    fun startWorkout() {
        if (_startupState.value != StartupState.Idle) return
        if (bleManager.isDemoMode.value) {
            workoutEngine.start()
            return
        }
        viewModelScope.launch {
            // Reopen the FE-C burst window so the trainer gets fresh Page 49 frames
            // during the countdown — the pre-send from screen entry may have expired.
            if (_ergEnabled.value) {
                val firstTarget = workoutEngine.progress.value.targetPowerWatts
                if (firstTarget > 0) {
                    bleManager.setTargetPower(firstTarget)
                    Timber.i("Startup: reopened burst for first target $firstTarget W")
                }
            }
            runStartupCountdown()
            _startupState.value = StartupState.Idle
            workoutEngine.start()
        }
    }

    /**
     * Loop until we get a full uninterrupted 5-second countdown. If the rider stops
     * pedaling mid-count (cadence → 0), reset to Waiting and try again.
     */
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
                    Timber.i("Startup countdown aborted at $sec — rider stopped pedaling")
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
     * Push the freshly-exported .fit to Strava. No-op unless [exportFitSilently] has
     * completed and the user is connected. The activity title matches the workout name.
     */
    fun uploadExportedFitToStrava() {
        val ready = _exportState.value as? ExportState.Ready ?: return
        val workoutName = workoutEngine.workoutDefinition?.name ?: "WorkItOut session"
        stravaRepository.uploadFit(ready.file, workoutName)
    }

    /**
     * Persist the completed ride to Room. Called once when the workout reaches COMPLETED.
     * Computes summary stats (avg/max power, NP, avg/max HR, avg cadence) from the
     * recorded data points and serializes the data points as compact JSON for the
     * detail-screen graph.
     */
    fun saveRideToHistory() {
        if (rideSaved) return
        val workout = workoutEngine.workoutDefinition ?: return
        val startedAt = workoutEngine.workoutStartEpochMillis
        val records = workoutEngine.recordedData.value
        if (startedAt == 0L || records.isEmpty()) return
        rideSaved = true

        viewModelScope.launch {
            try {
                val ftp = WorkItOutApplication.themePreferences.userFtpWatts.first()
                val powers = records.map { it.actualPower }
                val avgPower = powers.average().toInt()
                val maxPower = powers.max()
                val avgHr = records.map { it.heartRate }.average().toInt()
                val maxHr = records.maxOf { it.heartRate }
                val avgCadence = records.map { it.cadence }.average().toInt()

                // Normalised Power: rolling 30-second average of the 4th power, then 4th root.
                val np = computeNormalizedPower(powers)

                val durationSec = records.lastOrNull()?.timeSeconds ?: 0

                // Compact JSON: small keys to keep the blob lean.
                val compactPoints = records.map {
                    CompactDataPoint(it.timeSeconds, it.actualPower, it.targetPower, it.heartRate, it.cadence)
                }
                val json = Json.encodeToString(compactPoints)

                val entity = CompletedRideEntity(
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
                val newId = WorkItOutApplication.database.completedRideDao().insert(entity)
                _savedRideId.value = newId
                Timber.i("Ride saved to history: ${workout.name} (id=$newId)")

                // Optional auto-upload: if the user opted in and Strava is connected,
                // hand the brand-new ride to the history uploader. We deliberately
                // route through HistoryStravaUploader (not stravaRepository.uploadFit)
                // so the ride row gets stamped with the activityId on success — that's
                // what suppresses the manual "Upload to Strava" button on the ride
                // detail screen we're about to navigate to.
                val autoUpload = themePreferences.autoUploadToStravaOnFinish.first()
                if (autoUpload && stravaRepository.isConnected.value) {
                    // Wait for the silent .fit export to finish before handing
                    // the ride to the uploader. saveRideToHistory() and
                    // exportFitSilently() are fired back-to-back from
                    // WorkoutScreen's LaunchedEffect(COMPLETED); without this
                    // wait, OkHttp stats the still-growing .fit file, sends
                    // Content-Length for the partial size, then streams more
                    // bytes than it promised → ProtocolException: "expected
                    // 4479 bytes but received 8192". 30s cap so we never
                    // deadlock; if export fails, the uploader's own
                    // regenerate-if-missing path covers us.
                    Timber.i("Auto-upload enabled — waiting for .fit export to finish for ride $newId")
                    val terminal = withTimeoutOrNull(30_000L) {
                        exportState.first {
                            it is ExportState.Ready || it is ExportState.Failed
                        }
                    }
                    if (terminal is ExportState.Failed) {
                        Timber.w("Silent export failed — auto-upload will regenerate the .fit")
                    } else if (terminal == null) {
                        Timber.w("Timed out waiting for .fit export; uploader will regenerate")
                    }
                    Timber.i("Kicking history upload for ride $newId")
                    WorkItOutApplication.historyStravaUploader.upload(newId)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Failed to save ride to history")
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

/** Compact serializable data point for the JSON blob stored in completed_rides. */
@Serializable
data class CompactDataPoint(
    val t: Int,   // timeSeconds
    val p: Int,   // actualPower
    val tp: Int,  // targetPower
    val hr: Int,  // heartRate
    val c: Int    // cadence
)

/**
 * Normalised Power: 30-second rolling average → raise to 4th power → mean → 4th root.
 * Falls back to average power when fewer than 30 samples.
 */
private fun computeNormalizedPower(powers: List<Int>): Int {
    if (powers.size < 30) return powers.average().toInt()
    val rolling = powers.windowed(30) { window -> window.average() }
    val fourthPowerMean = rolling.map { it.pow(4.0) }.average()
    return fourthPowerMean.pow(0.25).toInt()
}

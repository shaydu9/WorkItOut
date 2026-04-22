package com.cycling.workitout.data.strava

import android.content.Context
import com.cycling.workitout.data.database.CompletedRideDao
import com.cycling.workitout.data.export.WorkoutExporter
import com.cycling.workitout.data.preferences.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Uploads past rides from the history screen to Strava, idempotently.
 *
 * Why this isn't just another method on [StravaRepository]:
 *  - The live post-workout flow owns [StravaRepository.uploadState] (a single
 *    app-wide flow). If history re-uploads scribbled over that flow, the
 *    live workout screen would flash "Uploading…" for an unrelated ride.
 *  - History uploads are per-ride: we need separate state per row so the
 *    right detail screen updates, not some singleton.
 *
 * So this class:
 *  - keeps a per-ride [StateFlow] map,
 *  - serialises concurrent `upload()` calls behind a mutex (one-at-a-time is
 *    plenty; Strava's upload API is rate-limited anyway),
 *  - regenerates the .fit on the fly when the original file has been evicted
 *    (e.g. user cleared app storage) using [WorkoutExporter.exportFromHistory],
 *  - stamps the DB row on success so the button disappears next time.
 */
class HistoryStravaUploader(
    private val appContext: Context,
    private val rideDao: CompletedRideDao,
    private val stravaRepository: StravaRepository,
    private val themePreferences: ThemePreferences
) {

    sealed class UploadState {
        object Idle : UploadState()
        object Uploading : UploadState()
        data class Success(val activityId: Long) : UploadState()
        data class Failed(val message: String) : UploadState()
    }

    private val perRideState = mutableMapOf<Long, MutableStateFlow<UploadState>>()
    private val mapLock = Any()
    private val uploadMutex = Mutex()

    /** Compose-friendly state flow for a specific ride. Safe to call on main. */
    fun stateFor(rideId: Long): StateFlow<UploadState> = flowFor(rideId).asStateFlow()

    private fun flowFor(rideId: Long): MutableStateFlow<UploadState> = synchronized(mapLock) {
        perRideState.getOrPut(rideId) { MutableStateFlow(UploadState.Idle) }
    }

    /**
     * Upload ride [rideId] to Strava. No-ops (silently) if the ride is already
     * uploaded. Emits Uploading → (Success | Failed) on the flow for this ride.
     */
    suspend fun upload(rideId: Long) {
        val flow = flowFor(rideId)
        if (flow.value is UploadState.Uploading) return

        val ride = rideDao.getById(rideId)
        if (ride == null) {
            flow.value = UploadState.Failed("Ride not found")
            return
        }
        if (ride.stravaActivityId != null) {
            // Already uploaded — surface it as Success so the UI treats it as done.
            flow.value = UploadState.Success(ride.stravaActivityId)
            return
        }
        if (!stravaRepository.isConnected.value) {
            flow.value = UploadState.Failed("Strava not connected")
            return
        }

        uploadMutex.withLock {
            flow.value = UploadState.Uploading
            try {
                val fitPath = WorkoutExporter.fitFileFor(appContext, ride.startedAtMillis)
                val fitFile = if (fitPath.exists()) {
                    Timber.d("Using existing .fit for history upload: ${fitPath.name}")
                    fitPath
                } else {
                    Timber.d("Regenerating .fit from stored samples for ride $rideId")
                    val weight = themePreferences.userWeightKg.first()
                    WorkoutExporter.exportFromHistory(appContext, ride, weight)
                }

                val activityId = stravaRepository.uploadFitForHistory(fitFile, ride.name)
                rideDao.markStravaUploaded(
                    rideId = rideId,
                    activityId = activityId,
                    uploadedAtMillis = System.currentTimeMillis()
                )
                flow.value = UploadState.Success(activityId)
                Timber.i("Strava history upload ok: ride=$rideId activity=$activityId")
            } catch (t: Throwable) {
                Timber.e(t, "Strava history upload failed for ride $rideId")
                flow.value = UploadState.Failed(t.message ?: "Upload failed")
            }
        }
    }

    /** Drop a Failed state back to Idle so the user can retry from a clean button. */
    fun clearError(rideId: Long) {
        val flow = flowFor(rideId)
        if (flow.value is UploadState.Failed) flow.value = UploadState.Idle
    }
}

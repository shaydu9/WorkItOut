package com.cycling.workitout.data.strava

import android.content.Context
import com.cycling.workitout.data.export.WorkoutExporter
import com.cycling.workitout.data.firestore.RideRepository
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.export.ExportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// Per-ride upload state so history uploads don't clobber the live workout screen's state.
class HistoryStravaUploader(
    private val appContext: Context,
    private val rideRepository: RideRepository,
    private val stravaRepository: StravaRepository,
    private val themePreferences: ThemePreferences
) {

    sealed class UploadState {
        object Idle : UploadState()
        object Uploading : UploadState()
        data class Success(val activityId: Long) : UploadState()
        data class Failed(val message: String) : UploadState()
    }

    private val perRideState = mutableMapOf<String, MutableStateFlow<UploadState>>()
    private val mapLock = Any()
    private val uploadMutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun stateFor(rideId: String): StateFlow<UploadState> = flowFor(rideId).asStateFlow()

    private fun flowFor(rideId: String): MutableStateFlow<UploadState> = synchronized(mapLock) {
        perRideState.getOrPut(rideId) { MutableStateFlow(UploadState.Idle) }
    }

    fun upload(rideId: String) {
        if (flowFor(rideId).value is UploadState.Uploading) return

        scope.launch {
            val flow = flowFor(rideId)

            val ride = rideRepository.getRideById(rideId)
            if (ride == null) {
                flow.value = UploadState.Failed("Ride not found")
                return@launch
            }
            if (ride.stravaActivityId != null) {
                // Already uploaded — surface it as Success so the UI treats it as done.
                flow.value = UploadState.Success(ride.stravaActivityId)
                return@launch
            }
            if (!stravaRepository.isConnected.value) {
                flow.value = UploadState.Failed("Strava not connected")
                return@launch
            }

            uploadMutex.withLock {
                flow.value = UploadState.Uploading
                try {
                    val fitPath = WorkoutExporter.fitFileFor(appContext, ride.startedAtMillis)
                    val fitFile = if (fitPath.exists()) {
                        Timber.tag("STRAVA")
                            .d("Using existing .fit for history upload: ${fitPath.name}")
                        fitPath
                    } else {
                        Timber.tag("STRAVA")
                            .d("Regenerating .fit from stored samples for ride $rideId")
                        val weight = themePreferences.userWeightKg.first()
                        WorkoutExporter.exportFromHistory(appContext, ride, weight)
                    }

                    val description = StravaActivityDescription.from(ride)
                    val activityId =
                        stravaRepository.uploadFitForHistory(fitFile, ride.name, description)
                    rideRepository.markStravaUploaded(
                        rideId,
                        activityId,
                        System.currentTimeMillis()
                    )
                    flow.value = UploadState.Success(activityId)
                    Timber.tag("STRAVA")
                        .i("Strava history upload ok: ride=$rideId activity=$activityId")
                } catch (t: Throwable) {
                    Timber.tag("STRAVA").e(t, "Strava history upload failed for ride $rideId")
                    flow.value = UploadState.Failed(t.message ?: "Upload failed")
                }
            }
        }
    }

    fun scheduleAutoUpload(rideId: String, fitExportState: StateFlow<ExportState>) {
        scope.launch {
            val autoUpload = themePreferences.autoUploadToStravaOnFinish.first()
            if (!autoUpload) return@launch

            val terminal = withTimeoutOrNull(30_000L) {
                fitExportState.first { it is ExportState.Ready || it is ExportState.Failed }
            }
            if (terminal is ExportState.Failed) {
                Timber.tag("STRAVA").w("Export failed — uploader will regenerate .fit for $rideId")
            } else if (terminal == null) {
                Timber.tag("STRAVA").w("Export timed out — uploader will regenerate .fit for $rideId")
            }
            upload(rideId)
        }
    }

    /** Drop a Failed state back to Idle so the user can retry from a clean button. */
    fun clearError(rideId: String) {
        val flow = flowFor(rideId)
        if (flow.value is UploadState.Failed) flow.value = UploadState.Idle
    }
}

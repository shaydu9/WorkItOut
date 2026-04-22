package com.cycling.workitout.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.data.database.CompletedRideEntity
import com.cycling.workitout.data.strava.HistoryStravaUploader
import com.cycling.workitout.ui.workout.CompactDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

class RideDetailViewModel(private val rideId: Long) : ViewModel() {

    private val rideDao = WorkItOutApplication.database.completedRideDao()
    private val historyUploader = WorkItOutApplication.historyStravaUploader
    private val stravaRepository = WorkItOutApplication.stravaRepository

    private val _ride = MutableStateFlow<CompletedRideEntity?>(null)
    val ride: StateFlow<CompletedRideEntity?> = _ride.asStateFlow()

    private val _dataPoints = MutableStateFlow<List<CompactDataPoint>>(emptyList())
    val dataPoints: StateFlow<List<CompactDataPoint>> = _dataPoints.asStateFlow()

    /** Per-ride upload state — shared with whatever else is observing this ride. */
    val uploadState: StateFlow<HistoryStravaUploader.UploadState> = historyUploader.stateFor(rideId)

    /** Whether Strava is currently linked at all (drives the connect-prompt UI). */
    val isStravaConnected: StateFlow<Boolean> = stravaRepository.isConnected

    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            loadRide()
        }
        // Whenever an upload finishes, re-read the entity so the persisted
        // stravaActivityId surfaces in the UI without a screen restart.
        viewModelScope.launch {
            uploadState.collect { state ->
                if (state is HistoryStravaUploader.UploadState.Success) {
                    loadRide()
                }
            }
        }
    }

    private suspend fun loadRide() {
        val entity = rideDao.getById(rideId)
        _ride.value = entity
        entity?.let {
            try {
                _dataPoints.value = json.decodeFromString<List<CompactDataPoint>>(it.dataPointsJson)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to parse data points for ride $rideId")
            }
        }
    }

    fun uploadToStrava() {
        viewModelScope.launch { historyUploader.upload(rideId) }
    }

    fun clearUploadError() = historyUploader.clearError(rideId)
}

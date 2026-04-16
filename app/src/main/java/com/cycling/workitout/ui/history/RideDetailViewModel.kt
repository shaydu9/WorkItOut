package com.cycling.workitout.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.data.database.CompletedRideEntity
import com.cycling.workitout.ui.workout.CompactDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

class RideDetailViewModel(rideId: Long) : ViewModel() {

    private val _ride = MutableStateFlow<CompletedRideEntity?>(null)
    val ride: StateFlow<CompletedRideEntity?> = _ride.asStateFlow()

    private val _dataPoints = MutableStateFlow<List<CompactDataPoint>>(emptyList())
    val dataPoints: StateFlow<List<CompactDataPoint>> = _dataPoints.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val entity = WorkItOutApplication.database.completedRideDao().getById(rideId)
            _ride.value = entity
            entity?.let {
                try {
                    _dataPoints.value = json.decodeFromString<List<CompactDataPoint>>(it.dataPointsJson)
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to parse data points for ride $rideId")
                }
            }
        }
    }
}

package com.cycling.workitout.ui.livedata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.LiveMetrics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Live Data screen
 */
class LiveDataViewModel(private val bleManager: BleManager) : ViewModel() {
    
    /**
     * Combine all sensor data into a single LiveMetrics object
     */
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
}

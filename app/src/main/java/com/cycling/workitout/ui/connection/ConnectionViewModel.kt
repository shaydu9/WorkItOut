package com.cycling.workitout.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Connection screen
 */
class ConnectionViewModel(private val bleManager: BleManager) : ViewModel() {
    
    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isPowerMeterConnected: StateFlow<Boolean> = bleManager.isPowerMeterConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    fun startScan() {
        viewModelScope.launch {
            bleManager.startScan()
        }
    }
    
    fun stopScan() {
        viewModelScope.launch {
            bleManager.stopScan()
        }
    }
    
    fun connectDevice(device: BleDevice) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.HEART_RATE_MONITOR -> bleManager.connectHeartRateMonitor(device)
                DeviceType.POWER_METER -> bleManager.connectPowerMeter(device)
                else -> { /* Handle other device types if needed */ }
            }
        }
    }
    
    fun disconnectHeartRate() {
        viewModelScope.launch {
            bleManager.disconnectHeartRateMonitor()
        }
    }
    
    fun disconnectPowerMeter() {
        viewModelScope.launch {
            bleManager.disconnectPowerMeter()
        }
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }
    
    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}

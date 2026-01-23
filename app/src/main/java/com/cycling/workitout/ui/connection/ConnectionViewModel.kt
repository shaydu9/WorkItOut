package com.cycling.workitout.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.database.EquipmentProfileEntity
import com.cycling.workitout.data.database.SavedDeviceEntity
import com.cycling.workitout.data.repository.DeviceRepository
import com.cycling.workitout.data.repository.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Connection screen
 */
class ConnectionViewModel(
    private val bleManager: BleManager,
    private val deviceRepository: DeviceRepository = WorkItOutApplication.deviceRepository,
    private val profileRepository: ProfileRepository = WorkItOutApplication.profileRepository
) : ViewModel() {
    
    // BLE scanning and connection states
    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isPowerMeterConnected: StateFlow<Boolean> = bleManager.isPowerMeterConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    // Saved devices from database
    val savedDevices: StateFlow<List<SavedDeviceEntity>> = deviceRepository.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Available profiles
    val profiles: StateFlow<List<EquipmentProfileEntity>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Active profile
    val activeProfile: StateFlow<EquipmentProfileEntity?> = profileRepository.getActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
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
            
            // Save device to database after successful connection
            // Optionally assign to active profile if one exists
            val profileIds = activeProfile.value?.profileId?.let { listOf(it) } ?: emptyList()
            deviceRepository.saveDevice(device, profileIds)
        }
    }
    
    /**
     * Reconnect to a saved device using its MAC address
     */
    fun reconnectDevice(device: SavedDeviceEntity) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.HEART_RATE_MONITOR -> bleManager.reconnectHeartRateMonitor(device.macAddress)
                DeviceType.POWER_METER -> bleManager.reconnectPowerMeter(device.macAddress)
                else -> { /* Handle other device types if needed */ }
            }
        }
    }
    
    /**
     * Rename a saved device
     */
    fun renameDevice(macAddress: String, newName: String) {
        viewModelScope.launch {
            deviceRepository.updateDeviceName(macAddress, newName)
        }
    }
    
    /**
     * Forget (delete) a saved device
     */
    fun forgetDevice(macAddress: String) {
        viewModelScope.launch {
            deviceRepository.deleteDevice(macAddress)
        }
    }
    
    /**
     * Assign device to a profile (adds to existing assignments)
     */
    fun assignDeviceToProfile(macAddress: String, profileId: String) {
        viewModelScope.launch {
            deviceRepository.assignDeviceToProfile(macAddress, profileId)
        }
    }
    
    /**
     * Unassign device from a profile
     */
    fun unassignDeviceFromProfile(macAddress: String, profileId: String) {
        viewModelScope.launch {
            deviceRepository.unassignDeviceFromProfile(macAddress, profileId)
        }
    }
    
    /**
     * Toggle device assignment to a profile
     */
    fun toggleDeviceProfileAssignment(macAddress: String, profileId: String, isCurrentlyAssigned: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyAssigned) {
                deviceRepository.unassignDeviceFromProfile(macAddress, profileId)
            } else {
                deviceRepository.assignDeviceToProfile(macAddress, profileId)
            }
        }
    }
    
    /**
     * Check if a device is already saved
     */
    suspend fun isDeviceSaved(macAddress: String): Boolean {
        return deviceRepository.isDeviceSaved(macAddress)
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

package com.cycling.workitout.ui.profiledetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.HeartRateData
import com.cycling.workitout.data.PowerData
import com.cycling.workitout.data.database.EquipmentProfileEntity
import com.cycling.workitout.data.database.SavedDeviceEntity
import com.cycling.workitout.data.repository.DeviceRepository
import com.cycling.workitout.data.repository.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

class ProfileDetailViewModel(
    private val bleManager: BleManager,
    private val profileRepository: ProfileRepository = WorkItOutApplication.profileRepository,
    private val deviceRepository: DeviceRepository = WorkItOutApplication.deviceRepository
) : ViewModel() {
    
    private val _profileId = MutableStateFlow<String?>(null)
    private val _profile = MutableStateFlow<EquipmentProfileEntity?>(null)
    
    // Profile data
    val profile: StateFlow<EquipmentProfileEntity?> = _profile.asStateFlow()
    
    // Devices for this profile
    val devices: StateFlow<List<SavedDeviceEntity>> = _profileId
        .flatMapLatest { id ->
            if (id != null) {
                deviceRepository.getDevicesByProfile(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // All available devices
    val allDevices: StateFlow<List<SavedDeviceEntity>> = deviceRepository.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Live sensor data
    val heartRateData: StateFlow<HeartRateData> = bleManager.heartRateData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeartRateData(0))
    
    val powerData: StateFlow<PowerData> = bleManager.powerData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PowerData(0, 0))
    
    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isPowerMeterConnected: StateFlow<Boolean> = bleManager.isPowerMeterConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isDemoMode: StateFlow<Boolean> = bleManager.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    // Check if current profile is the demo profile
    val isDemoProfile: StateFlow<Boolean> = profile
        .map { it?.profileId == ProfileRepository.DEMO_PROFILE_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    fun loadProfile(profileId: String) {
        val previousProfileId = _profileId.value
        Timber.d("Switching from profile: $previousProfileId → $profileId")
        
        // Always disable demo mode first when switching profiles
        // This ensures clean state when transitioning between profiles
        if (bleManager.isDemoMode.value) {
            Timber.d("Disabling demo mode before profile switch")
            bleManager.disableDemoMode()
        }
        
        // Clear previous profile data immediately to prevent showing wrong data
        _profile.value = null
        _profileId.value = profileId
        
        // Load the new profile from repository
        viewModelScope.launch {
            val loadedProfile = profileRepository.getProfileById(profileId)
            Timber.d("Loaded profile: ${loadedProfile?.name} (ID: ${loadedProfile?.profileId})")
            _profile.value = loadedProfile
            
            // Enable demo mode ONLY if this is the demo profile
            if (loadedProfile?.profileId == ProfileRepository.DEMO_PROFILE_ID) {
                Timber.d("Enabling demo mode for demo profile")
                bleManager.enableDemoMode()
            } else {
                Timber.d("Not demo profile, demo mode stays disabled")
            }
        }
    }
    
    fun reconnectDevice(device: SavedDeviceEntity) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.HEART_RATE_MONITOR -> bleManager.reconnectHeartRateMonitor(device.macAddress)
                DeviceType.POWER_METER -> bleManager.reconnectPowerMeter(device.macAddress)
                else -> { /* Handle other device types */ }
            }
        }
    }
    
    fun disconnectDevice(device: SavedDeviceEntity) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.HEART_RATE_MONITOR -> bleManager.disconnectHeartRateMonitor()
                DeviceType.POWER_METER -> bleManager.disconnectPowerMeter()
                else -> { /* Handle other device types */ }
            }
        }
    }
    
    fun getDemoPhaseDescription(): String {
        return bleManager.getDemoPhaseDescription()
    }
    
    fun getDemoElapsedTime(): String {
        return bleManager.getDemoElapsedTime()
    }
    
    fun getDemoElapsedSeconds(): Int {
        return bleManager.getDemoElapsedSeconds()
    }
    
    /**
     * Assign a device to the current profile
     */
    fun assignDeviceToProfile(deviceMacAddress: String) {
        viewModelScope.launch {
            val profileId = _profileId.value ?: return@launch
            deviceRepository.assignDeviceToProfile(deviceMacAddress, profileId)
        }
    }
    
    /**
     * Remove a device from the current profile
     */
    fun removeDeviceFromProfile(deviceMacAddress: String) {
        viewModelScope.launch {
            val profileId = _profileId.value ?: return@launch
            deviceRepository.unassignDeviceFromProfile(deviceMacAddress, profileId)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Timber.d("ViewModel cleared, cleaning up")
        // Always disable demo mode when ViewModel is destroyed to ensure clean state
        if (bleManager.isDemoMode.value) {
            Timber.d("Disabling demo mode on cleanup")
            bleManager.disableDemoMode()
        }
    }
}

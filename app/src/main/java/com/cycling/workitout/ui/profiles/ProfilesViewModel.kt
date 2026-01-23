package com.cycling.workitout.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.database.EquipmentProfileEntity
import com.cycling.workitout.data.database.SavedDeviceEntity
import com.cycling.workitout.data.repository.DeviceRepository
import com.cycling.workitout.data.repository.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Profiles screen
 */
class ProfilesViewModel(
    private val bleManager: BleManager? = null,
    private val profileRepository: ProfileRepository = WorkItOutApplication.profileRepository,
    private val deviceRepository: DeviceRepository = WorkItOutApplication.deviceRepository
) : ViewModel() {
    
    // All profiles
    val profiles: StateFlow<List<EquipmentProfileEntity>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Active profile
    val activeProfile: StateFlow<EquipmentProfileEntity?> = profileRepository.getActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    // All saved devices
    val savedDevices: StateFlow<List<SavedDeviceEntity>> = deviceRepository.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /**
     * Create a new profile
     */
    fun createProfile(name: String, icon: String = "🚴") {
        viewModelScope.launch {
            profileRepository.createProfile(name, icon)
        }
    }
    
    /**
     * Update profile
     */
    fun updateProfile(profileId: String, name: String, icon: String) {
        // Prevent editing of demo profile
        if (profileRepository.isDemoProfile(profileId)) {
            return
        }
        
        viewModelScope.launch {
            profileRepository.updateProfile(profileId, name, icon)
        }
    }
    
    /**
     * Delete a profile
     */
    fun deleteProfile(profileId: String) {
        // Prevent deletion of demo profile
        if (profileRepository.isDemoProfile(profileId)) {
            return
        }
        
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
        }
    }
    
    /**
     * Set active profile (and connect to its devices)
     */
    fun setActiveProfile(profileId: String) {
        viewModelScope.launch {
            // Check if this is the demo profile
            if (profileRepository.isDemoProfile(profileId)) {
                // Enable demo mode
                bleManager?.enableDemoMode()
                profileRepository.setActiveProfile(profileId)
            } else {
                // Disable demo mode if it was active
                bleManager?.disableDemoMode()
                
                // Set the profile as active
                profileRepository.setActiveProfile(profileId)
                
                // Auto-connect to all devices assigned to this profile
                if (bleManager != null) {
                    val devices = deviceRepository.getDevicesByProfile(profileId).first()
                    devices.forEach { savedDevice ->
                        // Connect based on device type using MAC address
                        when (savedDevice.deviceType) {
                            com.cycling.workitout.data.DeviceType.HEART_RATE_MONITOR -> {
                                bleManager.reconnectHeartRateMonitor(savedDevice.macAddress)
                            }
                            com.cycling.workitout.data.DeviceType.POWER_METER -> {
                                bleManager.reconnectPowerMeter(savedDevice.macAddress)
                            }
                            else -> {
                                // Handle other device types as needed
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Deactivate all profiles
     */
    fun deactivateAllProfiles() {
        viewModelScope.launch {
            // Disable demo mode if it was active
            bleManager?.disableDemoMode()
            profileRepository.deactivateAllProfiles()
        }
    }
    
    /**
     * Reorder profiles (excluding demo profile)
     */
    fun reorderProfiles(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentProfiles = profiles.value.toMutableList()
            
            // Filter out demo profile for reordering
            val regularProfiles = currentProfiles.filter { !profileRepository.isDemoProfile(it.profileId) }
            val demoProfile = currentProfiles.find { profileRepository.isDemoProfile(it.profileId) }
            
            if (fromIndex >= 0 && toIndex >= 0 && 
                fromIndex < regularProfiles.size && toIndex < regularProfiles.size) {
                
                // Perform the move
                val reordered = regularProfiles.toMutableList()
                val item = reordered.removeAt(fromIndex)
                reordered.add(toIndex, item)
                
                // Save the new order
                profileRepository.reorderProfiles(reordered.map { it.profileId })
            }
        }
    }
    
    /**
     * Assign device to profile
     */
    fun assignDeviceToProfile(deviceMac: String, profileId: String?) {
        viewModelScope.launch {
            if (profileId != null) {
                deviceRepository.assignDeviceToProfile(deviceMac, profileId)
            }
        }
    }
    
    /**
     * Get devices for a specific profile
     */
    fun getDevicesForProfile(profileId: String): StateFlow<List<SavedDeviceEntity>> {
        return deviceRepository.getDevicesByProfile(profileId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
    
    /**
     * Check if a profile is the demo profile
     */
    fun isDemoProfile(profileId: String): Boolean {
        return profileRepository.isDemoProfile(profileId)
    }
}

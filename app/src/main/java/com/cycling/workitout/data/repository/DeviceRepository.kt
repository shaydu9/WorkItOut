package com.cycling.workitout.data.repository

import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.database.DeviceProfileCrossRef
import com.cycling.workitout.data.database.DeviceProfileCrossRefDao
import com.cycling.workitout.data.database.SavedDeviceDao
import com.cycling.workitout.data.database.SavedDeviceEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository for managing saved BLE devices
 * Abstracts database operations from ViewModels
 */
class DeviceRepository(
    private val savedDeviceDao: SavedDeviceDao,
    private val crossRefDao: DeviceProfileCrossRefDao
) {
    
    /**
     * Get all saved devices
     */
    fun getAllDevices(): Flow<List<SavedDeviceEntity>> {
        return savedDeviceDao.getAllDevices()
    }
    
    /**
     * Get devices by profile
     */
    fun getDevicesByProfile(profileId: String): Flow<List<SavedDeviceEntity>> {
        return savedDeviceDao.getDevicesByProfile(profileId)
    }
    
    /**
     * Get unassigned devices
     */
    fun getUnassignedDevices(): Flow<List<SavedDeviceEntity>> {
        return savedDeviceDao.getUnassignedDevices()
    }
    
    /**
     * Save a newly connected device
     */
    suspend fun saveDevice(bleDevice: BleDevice, profileIds: List<String> = emptyList()) {
        val existing = savedDeviceDao.getDeviceByMac(bleDevice.address)
        
        if (existing == null) {
            // New device - insert
            val entity = SavedDeviceEntity(
                macAddress = bleDevice.address,
                manufacturerName = bleDevice.name,
                customName = null, // No custom name yet
                deviceType = bleDevice.deviceType,
                firstConnectedTimestamp = System.currentTimeMillis(),
                lastConnectedTimestamp = System.currentTimeMillis(),
                connectionCount = 1
            )
            savedDeviceDao.insertDevice(entity)
            
            // Assign to profiles if provided
            profileIds.forEach { profileId ->
                crossRefDao.assignDeviceToProfile(
                    DeviceProfileCrossRef(bleDevice.address, profileId)
                )
            }
        } else {
            // Existing device - update connection info
            savedDeviceDao.updateConnectionInfo(
                bleDevice.address,
                System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update device's custom name
     */
    suspend fun updateDeviceName(macAddress: String, customName: String) {
        savedDeviceDao.updateDeviceName(macAddress, customName)
    }
    
    /**
     * Assign device to a profile (adds to existing assignments)
     */
    suspend fun assignDeviceToProfile(macAddress: String, profileId: String) {
        crossRefDao.assignDeviceToProfile(
            DeviceProfileCrossRef(macAddress, profileId)
        )
    }
    
    /**
     * Unassign device from a profile
     */
    suspend fun unassignDeviceFromProfile(macAddress: String, profileId: String) {
        crossRefDao.unassignDeviceFromProfile(
            DeviceProfileCrossRef(macAddress, profileId)
        )
    }
    
    /**
     * Get all profiles a device is assigned to
     */
    fun getProfilesForDevice(macAddress: String): Flow<List<String>> {
        return crossRefDao.getProfilesForDevice(macAddress)
    }
    
    /**
     * Check if a device is assigned to a profile
     */
    suspend fun isDeviceAssignedToProfile(macAddress: String, profileId: String): Boolean {
        return crossRefDao.isDeviceAssignedToProfile(macAddress, profileId)
    }
    
    /**
     * Check if device is already saved
     */
    suspend fun isDeviceSaved(macAddress: String): Boolean {
        return savedDeviceDao.getDeviceByMac(macAddress) != null
    }
    
    /**
     * Get saved device by MAC address
     */
    suspend fun getDeviceByMac(macAddress: String): SavedDeviceEntity? {
        return savedDeviceDao.getDeviceByMac(macAddress)
    }
    
    /**
     * Delete a device
     */
    suspend fun deleteDevice(macAddress: String) {
        savedDeviceDao.deleteDeviceByMac(macAddress)
    }
    
    /**
     * Delete all devices
     */
    suspend fun deleteAllDevices() {
        savedDeviceDao.deleteAllDevices()
    }
}

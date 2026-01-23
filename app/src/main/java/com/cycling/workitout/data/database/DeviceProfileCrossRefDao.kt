package com.cycling.workitout.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for device-profile relationships (many-to-many)
 */
@Dao
interface DeviceProfileCrossRefDao {
    
    /**
     * Assign a device to a profile
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignDeviceToProfile(crossRef: DeviceProfileCrossRef)
    
    /**
     * Unassign a device from a profile
     */
    @Delete
    suspend fun unassignDeviceFromProfile(crossRef: DeviceProfileCrossRef)
    
    /**
     * Unassign a device from all profiles
     */
    @Query("DELETE FROM device_profile_cross_ref WHERE macAddress = :macAddress")
    suspend fun unassignDeviceFromAllProfiles(macAddress: String)
    
    /**
     * Unassign all devices from a profile
     */
    @Query("DELETE FROM device_profile_cross_ref WHERE profileId = :profileId")
    suspend fun unassignAllDevicesFromProfile(profileId: String)
    
    /**
     * Get all profile IDs for a device
     */
    @Query("SELECT profileId FROM device_profile_cross_ref WHERE macAddress = :macAddress")
    fun getProfilesForDevice(macAddress: String): Flow<List<String>>
    
    /**
     * Get all device MAC addresses for a profile
     */
    @Query("SELECT macAddress FROM device_profile_cross_ref WHERE profileId = :profileId")
    fun getDevicesForProfile(profileId: String): Flow<List<String>>
    
    /**
     * Check if a device is assigned to a profile
     */
    @Query("SELECT EXISTS(SELECT 1 FROM device_profile_cross_ref WHERE macAddress = :macAddress AND profileId = :profileId)")
    suspend fun isDeviceAssignedToProfile(macAddress: String, profileId: String): Boolean
}

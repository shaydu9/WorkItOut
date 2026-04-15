package com.cycling.workitout.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved BLE devices
 */
@Dao
interface SavedDeviceDao {
    
    /**
     * Get all saved devices as a Flow (reactive updates)
     */
    @Query("SELECT * FROM saved_devices ORDER BY lastConnectedTimestamp DESC")
    fun getAllDevices(): Flow<List<SavedDeviceEntity>>
    
    /**
     * Get device by MAC address
     */
    @Query("SELECT * FROM saved_devices WHERE macAddress = :macAddress")
    suspend fun getDeviceByMac(macAddress: String): SavedDeviceEntity?
    
    /**
     * Insert or update a device
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDeviceEntity)
    
    /**
     * Update device's custom name
     */
    @Query("UPDATE saved_devices SET customName = :customName WHERE macAddress = :macAddress")
    suspend fun updateDeviceName(macAddress: String, customName: String)
    
    /**
     * Update last connected timestamp and increment connection count
     */
    @Query("""
        UPDATE saved_devices 
        SET lastConnectedTimestamp = :timestamp, 
            connectionCount = connectionCount + 1 
        WHERE macAddress = :macAddress
    """)
    suspend fun updateConnectionInfo(macAddress: String, timestamp: Long)
    
    /**
     * Delete a device
     */
    @Delete
    suspend fun deleteDevice(device: SavedDeviceEntity)
    
    /**
     * Delete device by MAC address
     */
    @Query("DELETE FROM saved_devices WHERE macAddress = :macAddress")
    suspend fun deleteDeviceByMac(macAddress: String)
    
    /**
     * Delete all devices
     */
    @Query("DELETE FROM saved_devices")
    suspend fun deleteAllDevices()
}

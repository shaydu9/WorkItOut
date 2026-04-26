package com.cycling.workitout.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeviceDao {

    @Query("SELECT * FROM saved_devices ORDER BY lastConnectedTimestamp DESC")
    fun getAllDevices(): Flow<List<SavedDeviceEntity>>

    @Query("SELECT * FROM saved_devices WHERE macAddress = :macAddress")
    suspend fun getDeviceByMac(macAddress: String): SavedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDeviceEntity)

    @Query("UPDATE saved_devices SET customName = :customName WHERE macAddress = :macAddress")
    suspend fun updateDeviceName(macAddress: String, customName: String)

    @Query("""
        UPDATE saved_devices
        SET lastConnectedTimestamp = :timestamp,
            connectionCount = connectionCount + 1
        WHERE macAddress = :macAddress
    """)
    suspend fun updateConnectionInfo(macAddress: String, timestamp: Long)

    @Delete
    suspend fun deleteDevice(device: SavedDeviceEntity)

    @Query("DELETE FROM saved_devices WHERE macAddress = :macAddress")
    suspend fun deleteDeviceByMac(macAddress: String)

    @Query("DELETE FROM saved_devices")
    suspend fun deleteAllDevices()
}

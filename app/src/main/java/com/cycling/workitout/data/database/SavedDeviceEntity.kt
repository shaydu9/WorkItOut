package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.workitout.data.DeviceType

/**
 * Database entity for saved BLE devices
 */
@Entity(tableName = "saved_devices")
data class SavedDeviceEntity(
    @PrimaryKey
    val macAddress: String,                 // Unique identifier (Bluetooth MAC)
    val manufacturerName: String,           // Original device name from BLE
    val customName: String?,                // User-assigned custom name
    val deviceType: DeviceType,             // HR_MONITOR, POWER_METER, etc.
    val firstConnectedTimestamp: Long,      // When first connected
    val lastConnectedTimestamp: Long,       // When last connected
    val connectionCount: Int = 0            // How many times connected
    // Note: profileId removed - now using junction table for many-to-many relationship
) {
    /**
     * Get display name - custom name if available, otherwise manufacturer name
     */
    fun getDisplayName(): String = customName ?: manufacturerName
}

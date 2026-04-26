package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.workitout.data.DeviceType

// A previously paired BLE device, keyed by MAC address.
@Entity(tableName = "saved_devices")
data class SavedDeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val manufacturerName: String,
    val customName: String?,
    val deviceType: DeviceType,
    val firstConnectedTimestamp: Long,
    val lastConnectedTimestamp: Long,
    val connectionCount: Int = 0
) {
    fun getDisplayName(): String = customName ?: manufacturerName
}

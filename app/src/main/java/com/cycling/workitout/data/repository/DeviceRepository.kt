package com.cycling.workitout.data.repository

import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.database.SavedDeviceDao
import com.cycling.workitout.data.database.SavedDeviceEntity
import kotlinx.coroutines.flow.Flow

// Persists paired BLE devices so the app can auto-reconnect on startup.
class DeviceRepository(
    private val savedDeviceDao: SavedDeviceDao
) {

    fun getAllDevices(): Flow<List<SavedDeviceEntity>> {
        return savedDeviceDao.getAllDevices()
    }

    suspend fun saveDevice(bleDevice: BleDevice) {
        val existing = savedDeviceDao.getDeviceByMac(bleDevice.address)
        if (existing == null) {
            val now = System.currentTimeMillis()
            savedDeviceDao.insertDevice(
                SavedDeviceEntity(
                    macAddress = bleDevice.address,
                    manufacturerName = bleDevice.name,
                    customName = null,
                    deviceType = bleDevice.deviceType,
                    firstConnectedTimestamp = now,
                    lastConnectedTimestamp = now,
                    connectionCount = 1
                )
            )
        } else {
            // Refresh deviceType on re-pair so reclassifications (e.g. POWER_METER → SMART_TRAINER) take effect.
            val now = System.currentTimeMillis()
            savedDeviceDao.insertDevice(
                existing.copy(
                    deviceType = bleDevice.deviceType,
                    manufacturerName = bleDevice.name,
                    lastConnectedTimestamp = now,
                    connectionCount = existing.connectionCount + 1
                )
            )
        }
    }

    suspend fun updateDeviceName(macAddress: String, customName: String) {
        savedDeviceDao.updateDeviceName(macAddress, customName)
    }

    suspend fun isDeviceSaved(macAddress: String): Boolean {
        return savedDeviceDao.getDeviceByMac(macAddress) != null
    }

    suspend fun getDeviceByMac(macAddress: String): SavedDeviceEntity? {
        return savedDeviceDao.getDeviceByMac(macAddress)
    }

    suspend fun deleteDevice(macAddress: String) {
        savedDeviceDao.deleteDeviceByMac(macAddress)
    }

    suspend fun deleteAllDevices() {
        savedDeviceDao.deleteAllDevices()
    }
}

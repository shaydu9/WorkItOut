package com.cycling.workitout.data.repository

import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.database.SavedDeviceDao
import com.cycling.workitout.data.database.SavedDeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing saved BLE devices.
 * Profile concept removed in Phase G — this just persists paired devices
 * so the app can auto-reconnect on startup.
 */
class DeviceRepository(
    private val savedDeviceDao: SavedDeviceDao
) {

    fun getAllDevices(): Flow<List<SavedDeviceEntity>> {
        return savedDeviceDao.getAllDevices()
    }

    /**
     * Save a newly connected device (insert) or bump its last-connected timestamp
     * if it's already known.
     */
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
            // Always update the device type on re-pair. A device previously saved
            // as POWER_METER might now correctly be classified as SMART_TRAINER
            // (e.g. Tacx Neo 2T after the scan-classification fix).
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

package com.cycling.workitout.ble

import java.util.UUID

/**
 * Bluetooth LE GATT service and characteristic UUIDs for cycling sensors
 */
object BleConstants {
    // Standard BLE Services
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    
    val CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb")
    
    val FITNESS_MACHINE_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    
    // Client Characteristic Configuration Descriptor (for enabling notifications)
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Scan timeout
    const val SCAN_PERIOD: Long = 10000 // 10 seconds
}

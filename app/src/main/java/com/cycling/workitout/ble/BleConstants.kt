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

    // FTMS Characteristics
    val FITNESS_MACHINE_FEATURE_CHAR_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb")
    val INDOOR_BIKE_DATA_CHAR_UUID: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")
    val TRAINING_STATUS_CHAR_UUID: UUID = UUID.fromString("00002AD3-0000-1000-8000-00805f9b34fb")
    val SUPPORTED_RESISTANCE_LEVEL_RANGE_CHAR_UUID: UUID = UUID.fromString("00002AD6-0000-1000-8000-00805f9b34fb")
    val FTMS_CONTROL_POINT_CHAR_UUID: UUID = UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb")
    val FTMS_STATUS_CHAR_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805f9b34fb")

    // FTMS Control Point Opcodes
    const val FTMS_REQUEST_CONTROL: Byte = 0x00
    const val FTMS_RESET: Byte = 0x01
    const val FTMS_SET_TARGET_POWER: Byte = 0x05
    const val FTMS_START_RESUME: Byte = 0x07
    const val FTMS_STOP_PAUSE: Byte = 0x08

    // Client Characteristic Configuration Descriptor (for enabling notifications)
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Scan timeout
    const val SCAN_PERIOD: Long = 10000 // 10 seconds
}

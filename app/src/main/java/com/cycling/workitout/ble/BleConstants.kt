package com.cycling.workitout.ble

import java.util.UUID

// BLE GATT UUIDs and FTMS opcodes for cycling sensors and trainers.
object BleConstants {
    // Standard BLE Services
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    
    val CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb")

    // Cycling Speed and Cadence Service (CSC) — provides reliable cadence
    val CYCLING_SPEED_CADENCE_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    val CSC_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")
    
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

    // Tacx FE-C-over-BLE — proprietary tunnel for ANT+ FE-C frames; used when a trainer ships in FE-C mode instead of FTMS.
    val TACX_FEC_SERVICE_UUID: UUID = UUID.fromString("6e40fec1-b5a3-f393-e0a9-e50e24dcca9e")
    val TACX_FEC_WRITE_CHAR_UUID: UUID = UUID.fromString("6e40fec3-b5a3-f393-e0a9-e50e24dcca9e")
    val TACX_FEC_NOTIFY_CHAR_UUID: UUID = UUID.fromString("6e40fec2-b5a3-f393-e0a9-e50e24dcca9e")

    // ANT+ FE-C message framing constants
    const val ANT_SYNC: Byte = 0xA4.toByte()
    const val ANT_MSG_ACKNOWLEDGED_DATA: Byte = 0x4F
    const val ANT_FEC_CHANNEL: Byte = 0x05
    const val ANT_PAGE_TARGET_POWER: Byte = 0x31   // Page 49

    // Scan timeout
    const val SCAN_PERIOD: Long = 10000 // 10 seconds
}

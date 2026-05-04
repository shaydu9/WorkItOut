package com.cycling.workitout.data

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: DeviceType
)

enum class DeviceType {
    HEART_RATE_MONITOR,
    POWER_METER,
    SMART_TRAINER,
    CADENCE_SENSOR,
    UNKNOWN
}

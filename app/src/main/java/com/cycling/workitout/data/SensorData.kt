package com.cycling.workitout.data

data class HeartRateData(
    val heartRate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class PowerData(
    val power: Int = 0,
    val cadence: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class LiveMetrics(
    val heartRate: Int = 0,
    val power: Int = 0,
    val cadence: Int = 0,
    val isHeartRateConnected: Boolean = false,
    val isPowerMeterConnected: Boolean = false
)

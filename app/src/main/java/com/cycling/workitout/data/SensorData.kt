package com.cycling.workitout.data

/**
 * Heart rate measurement data
 */
data class HeartRateData(
    val heartRate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Power meter measurement data
 */
data class PowerData(
    val power: Int = 0,
    val cadence: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Combined sensor data for display
 */
data class LiveMetrics(
    val heartRate: Int = 0,
    val power: Int = 0,
    val cadence: Int = 0,
    val isHeartRateConnected: Boolean = false,
    val isPowerMeterConnected: Boolean = false
)

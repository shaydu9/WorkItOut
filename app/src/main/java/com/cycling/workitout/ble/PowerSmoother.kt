package com.cycling.workitout.ble

import java.util.LinkedList

// Rolling-average power smoother — same approach Garmin/Zwift/Hammerhead use.
class PowerSmoother(
    private var smoothingWindowSeconds: Int = 3
) {
    private val powerReadings = LinkedList<PowerReading>()

    data class PowerReading(
        val power: Int,
        val timestamp: Long
    )

    fun addReading(power: Int): Int {
        val currentTime = System.currentTimeMillis()
        powerReadings.add(PowerReading(power, currentTime))

        val windowStartTime = currentTime - (smoothingWindowSeconds * 1000)
        while (powerReadings.isNotEmpty() && powerReadings.first.timestamp < windowStartTime) {
            powerReadings.removeFirst()
        }

        return if (powerReadings.isNotEmpty()) {
            powerReadings.map { it.power }.average().toInt()
        } else {
            power
        }
    }

    fun setSmoothingWindow(seconds: Int) {
        if (seconds > 0) {
            smoothingWindowSeconds = seconds
            val currentTime = System.currentTimeMillis()
            val windowStartTime = currentTime - (smoothingWindowSeconds * 1000)
            while (powerReadings.isNotEmpty() && powerReadings.first.timestamp < windowStartTime) {
                powerReadings.removeFirst()
            }
        }
    }

    fun getSmoothingWindow(): Int = smoothingWindowSeconds

    fun clear() {
        powerReadings.clear()
    }

    fun getCurrentSmoothedPower(): Int {
        return if (powerReadings.isNotEmpty()) {
            powerReadings.map { it.power }.average().toInt()
        } else {
            0
        }
    }
}

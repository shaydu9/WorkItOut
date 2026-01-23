package com.cycling.workitout.ble

import java.util.LinkedList

/**
 * Power smoothing utility using a moving average window
 * Similar to Garmin, Zwift, and Hammerhead implementation
 */
class PowerSmoother(
    private var smoothingWindowSeconds: Int = 3
) {
    // Store power readings with timestamps
    private val powerReadings = LinkedList<PowerReading>()
    
    data class PowerReading(
        val power: Int,
        val timestamp: Long
    )
    
    /**
     * Add a new power reading and get the smoothed value
     */
    fun addReading(power: Int): Int {
        val currentTime = System.currentTimeMillis()
        
        // Add new reading
        powerReadings.add(PowerReading(power, currentTime))
        
        // Remove readings outside the smoothing window
        val windowStartTime = currentTime - (smoothingWindowSeconds * 1000)
        while (powerReadings.isNotEmpty() && powerReadings.first.timestamp < windowStartTime) {
            powerReadings.removeFirst()
        }
        
        // Calculate average
        return if (powerReadings.isNotEmpty()) {
            powerReadings.map { it.power }.average().toInt()
        } else {
            power
        }
    }
    
    /**
     * Update the smoothing window duration
     */
    fun setSmoothingWindow(seconds: Int) {
        if (seconds > 0) {
            smoothingWindowSeconds = seconds
            // Clean up old readings that are now outside the new window
            val currentTime = System.currentTimeMillis()
            val windowStartTime = currentTime - (smoothingWindowSeconds * 1000)
            while (powerReadings.isNotEmpty() && powerReadings.first.timestamp < windowStartTime) {
                powerReadings.removeFirst()
            }
        }
    }
    
    /**
     * Get current smoothing window in seconds
     */
    fun getSmoothingWindow(): Int = smoothingWindowSeconds
    
    /**
     * Clear all readings (useful when disconnecting)
     */
    fun clear() {
        powerReadings.clear()
    }
    
    /**
     * Get the current smoothed power value without adding a new reading
     */
    fun getCurrentSmoothedPower(): Int {
        return if (powerReadings.isNotEmpty()) {
            powerReadings.map { it.power }.average().toInt()
        } else {
            0
        }
    }
}

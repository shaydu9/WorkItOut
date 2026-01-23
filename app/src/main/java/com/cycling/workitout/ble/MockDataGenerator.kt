package com.cycling.workitout.ble

import com.cycling.workitout.data.HeartRateData
import com.cycling.workitout.data.PowerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates realistic mock cycling data for demo/testing purposes
 * Simulates a realistic cycling workout with variations
 */
class MockDataGenerator(private val scope: CoroutineScope) {
    
    private var mockDataJob: Job? = null
    private var elapsedSeconds = 0
    
    // Simulated workout state
    private var baseHeartRate = 120 // Base HR around threshold
    private var basePower = 200 // Base power around FTP
    private var baseCadence = 85 // Typical cadence
    
    // State flows for mock data
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _heartRateData = MutableStateFlow(HeartRateData(0))
    val heartRateData: StateFlow<HeartRateData> = _heartRateData.asStateFlow()
    
    private val _powerData = MutableStateFlow(PowerData(0, 0))
    val powerData: StateFlow<PowerData> = _powerData.asStateFlow()
    
    /**
     * Start generating mock data
     */
    fun start() {
        if (_isActive.value) return
        
        _isActive.value = true
        elapsedSeconds = 0
        
        mockDataJob = scope.launch {
            while (_isActive.value) {
                generateRealisticData()
                delay(1000) // Update every second
                elapsedSeconds++
            }
        }
    }
    
    /**
     * Stop generating mock data
     */
    fun stop() {
        _isActive.value = false
        mockDataJob?.cancel()
        mockDataJob = null
        _heartRateData.value = HeartRateData(0)
        _powerData.value = PowerData(0, 0)
        elapsedSeconds = 0
    }
    
    /**
     * Generate realistic cycling data with variations
     * Simulates intervals, fatigue, and natural variations
     */
    private fun generateRealisticData() {
        // Create workout phases with intervals
        val workoutPhase = (elapsedSeconds / 30) % 4 // 30-second phases
        
        // Adjust base values based on workout phase
        when (workoutPhase) {
            0 -> { // Easy/recovery
                baseHeartRate = 110
                basePower = 150
                baseCadence = 75
            }
            1 -> { // Moderate
                baseHeartRate = 135
                basePower = 220
                baseCadence = 88
            }
            2 -> { // Hard interval
                baseHeartRate = 165
                basePower = 300
                baseCadence = 95
            }
            3 -> { // Sprint!
                baseHeartRate = 175
                basePower = 380
                baseCadence = 105
            }
        }
        
        // Add natural variations using sine wave + random noise
        val time = elapsedSeconds.toDouble()
        val sineVariation = sin(time / 10.0) * 0.1 // Smooth wave
        val randomNoise = (Random.nextDouble() - 0.5) * 0.15 // Random fluctuation
        val totalVariation = 1.0 + sineVariation + randomNoise
        
        // Generate heart rate (60-190 bpm range)
        val heartRate = (baseHeartRate * totalVariation).toInt().coerceIn(60, 190)
        
        // Generate power (0-450W range, can drop to 0 during coasting)
        val powerVariation = 1.0 + (Random.nextDouble() - 0.5) * 0.2
        var power = (basePower * powerVariation).toInt().coerceIn(0, 450)
        
        // Occasionally simulate coasting (5% chance)
        if (Random.nextDouble() < 0.05) {
            power = Random.nextInt(0, 30) // Very low power while coasting
        }
        
        // Generate cadence (0-120 rpm range)
        val cadenceVariation = 1.0 + (Random.nextDouble() - 0.5) * 0.15
        var cadence = (baseCadence * cadenceVariation).toInt().coerceIn(0, 120)
        
        // Cadence drops when coasting
        if (power < 30) {
            cadence = Random.nextInt(0, 40)
        }
        
        // Simulate fatigue over time (slight HR drift up, power down)
        val fatigueMinutes = elapsedSeconds / 60.0
        val fatigueFactor = (fatigueMinutes * 0.002).coerceIn(0.0, 0.1)
        val heartRateWithFatigue = (heartRate * (1.0 + fatigueFactor)).toInt().coerceIn(60, 190)
        val powerWithFatigue = (power * (1.0 - fatigueFactor * 0.5)).toInt().coerceIn(0, 450)
        
        // Update state flows
        _heartRateData.value = HeartRateData(heartRate = heartRateWithFatigue)
        _powerData.value = PowerData(power = powerWithFatigue, cadence = cadence)
    }
    
    /**
     * Get a description of current workout phase
     */
    fun getCurrentPhaseDescription(): String {
        val phase = (elapsedSeconds / 30) % 4
        return when (phase) {
            0 -> "Recovery"
            1 -> "Endurance"
            2 -> "Threshold"
            3 -> "Sprint"
            else -> "Active"
        }
    }
    
    /**
     * Get elapsed time formatted as MM:SS
     */
    fun getElapsedTimeFormatted(): String {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    fun getElapsedSeconds(): Int {
        return elapsedSeconds
    }
}

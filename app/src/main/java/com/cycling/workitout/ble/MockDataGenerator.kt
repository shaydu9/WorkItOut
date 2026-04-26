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

// Synthesizes plausible HR/power/cadence streams for the demo mode.
class MockDataGenerator(private val scope: CoroutineScope) {

    private var mockDataJob: Job? = null
    private var elapsedSeconds = 0

    private var externalTargetPower: Int? = null

    private var baseHeartRate = 120
    private var basePower = 200
    private var baseCadence = 85

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _heartRateData = MutableStateFlow(HeartRateData(0))
    val heartRateData: StateFlow<HeartRateData> = _heartRateData.asStateFlow()

    private val _powerData = MutableStateFlow(PowerData(0, 0))
    val powerData: StateFlow<PowerData> = _powerData.asStateFlow()

    fun start() {
        if (_isActive.value) return
        
        _isActive.value = true
        elapsedSeconds = 0
        
        mockDataJob = scope.launch {
            while (_isActive.value) {
                generateRealisticData()
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    fun stop() {
        _isActive.value = false
        mockDataJob?.cancel()
        mockDataJob = null
        _heartRateData.value = HeartRateData(0)
        _powerData.value = PowerData(0, 0)
        elapsedSeconds = 0
    }
    
    fun setTargetPower(watts: Int) {
        externalTargetPower = watts
    }

    fun clearTargetPower() {
        externalTargetPower = null
    }

    private fun generateRealisticData() {
        val targetPower = externalTargetPower

        if (targetPower != null) {
            // Track the workout target so demo data tracks ERG behavior.
            basePower = targetPower
            baseCadence = when {
                targetPower < 140 -> 75
                targetPower < 200 -> 82
                targetPower < 280 -> 90
                targetPower < 350 -> 95
                else -> 100
            }
            baseHeartRate = when {
                targetPower < 140 -> 110
                targetPower < 200 -> 130
                targetPower < 280 -> 150
                targetPower < 350 -> 170
                else -> 180
            }
        } else {
            // No external target — cycle through 30s easy/moderate/hard/sprint phases.
            val workoutPhase = (elapsedSeconds / 30) % 4
            when (workoutPhase) {
                0 -> { baseHeartRate = 110; basePower = 150; baseCadence = 75 }
                1 -> { baseHeartRate = 135; basePower = 220; baseCadence = 88 }
                2 -> { baseHeartRate = 165; basePower = 300; baseCadence = 95 }
                3 -> { baseHeartRate = 175; basePower = 380; baseCadence = 105 }
            }
        }

        val time = elapsedSeconds.toDouble()
        val sineVariation = sin(time / 10.0) * 0.1
        val randomNoise = (Random.nextDouble() - 0.5) * 0.15
        val totalVariation = 1.0 + sineVariation + randomNoise

        val heartRate = (baseHeartRate * totalVariation).toInt().coerceIn(60, 190)

        val powerVariation = 1.0 + (Random.nextDouble() - 0.5) * 0.2
        var power = (basePower * powerVariation).toInt().coerceIn(0, 450)

        // 5% chance of a coasting blip.
        if (Random.nextDouble() < 0.05) {
            power = Random.nextInt(0, 30)
        }

        val cadenceVariation = 1.0 + (Random.nextDouble() - 0.5) * 0.15
        var cadence = (baseCadence * cadenceVariation).toInt().coerceIn(0, 120)
        if (power < 30) {
            cadence = Random.nextInt(0, 40)
        }

        // Slight HR drift up + power drift down to fake fatigue.
        val fatigueMinutes = elapsedSeconds / 60.0
        val fatigueFactor = (fatigueMinutes * 0.002).coerceIn(0.0, 0.1)
        val heartRateWithFatigue = (heartRate * (1.0 + fatigueFactor)).toInt().coerceIn(60, 190)
        val powerWithFatigue = (power * (1.0 - fatigueFactor * 0.5)).toInt().coerceIn(0, 450)

        _heartRateData.value = HeartRateData(heartRate = heartRateWithFatigue)
        _powerData.value = PowerData(power = powerWithFatigue, cadence = cadence)
    }

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
    
    fun getElapsedTimeFormatted(): String {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    fun getElapsedSeconds(): Int {
        return elapsedSeconds
    }
}

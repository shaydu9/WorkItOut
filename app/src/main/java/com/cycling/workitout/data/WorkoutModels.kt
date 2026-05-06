package com.cycling.workitout.data

import kotlin.math.roundToInt

// Coggan power zones — Z1 recovery through Z6 anaerobic.
enum class PowerZone(val label: String, val colorHex: Long) {
    Z1_RECOVERY("Recovery", 0xFF9E9E9E),       // Grey
    Z2_ENDURANCE("Endurance", 0xFF2196F3),      // Blue
    Z3_TEMPO("Tempo", 0xFF4CAF50),              // Green
    Z4_THRESHOLD("Threshold", 0xFFFFC107),      // Yellow
    Z5_VO2MAX("VO2 Max", 0xFFFF9800),           // Orange
    Z6_ANAEROBIC("Anaerobic", 0xFFFF5252);      // Red

    companion object {
        fun fromPercentFtp(percentFtp: Double): PowerZone = when {
            percentFtp < 0.55 -> Z1_RECOVERY
            percentFtp < 0.75 -> Z2_ENDURANCE
            percentFtp < 0.90 -> Z3_TEMPO
            percentFtp < 1.05 -> Z4_THRESHOLD
            percentFtp < 1.20 -> Z5_VO2MAX
            else -> Z6_ANAEROBIC
        }

        fun fromAbsoluteWatts(watts: Int, ftp: Int): PowerZone =
            fromPercentFtp(watts.toDouble() / ftp.toDouble())
    }
}

// One interval — canonical %FTP plus a snapshot watts value resolved at load time.
data class WorkoutIntervalDef(
    val durationSeconds: Int,
    val targetPowerPercentFtp: Float,
    val targetPowerWatts: Int,
    val name: String,
    val zone: PowerZone
)

data class WorkoutDefinition(
    val id: String,
    val name: String,
    val description: String,
    val intervals: List<WorkoutIntervalDef>,
    val totalDurationSeconds: Int = intervals.sumOf { it.durationSeconds },
    // Free ride: no ERG target, no time limit. Rider starts/stops manually; the live
    // graph just shows produced watts over elapsed time. Intervals must be empty.
    val isFreeRide: Boolean = false
)

// Recomputes targetPowerWatts from each interval's canonical %FTP — call at load time so saved workouts track the user's current FTP.
fun WorkoutDefinition.withFtp(ftp: Int): WorkoutDefinition =
    copy(intervals = intervals.map {
        it.copy(targetPowerWatts = (it.targetPowerPercentFtp * ftp).roundToInt().coerceAtLeast(40))
    })

enum class WorkoutState {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    COMPLETED
}

data class WorkoutProgress(
    val workoutState: WorkoutState = WorkoutState.NOT_STARTED,
    val workoutName: String = "",
    val currentIntervalIndex: Int = 0,
    val totalIntervals: Int = 0,
    val currentIntervalName: String = "",
    val intervalElapsedSeconds: Int = 0,
    val intervalRemainingSeconds: Int = 0,
    val intervalDurationSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val totalRemainingSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val targetPowerWatts: Int = 0,
    val currentZone: PowerZone = PowerZone.Z1_RECOVERY
)

// One sample captured during a ride — feeds the live graph and the .fit exporter.
data class RecordedDataPoint(
    val timeSeconds: Int,
    val actualPower: Int,
    val targetPower: Int,
    val heartRate: Int,
    val cadence: Int,
    val epochMillis: Long = 0L,
    val speedMps: Float = 0f,
    val distanceMeters: Float = 0f
)

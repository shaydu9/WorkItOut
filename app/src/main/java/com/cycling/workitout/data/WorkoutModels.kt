package com.cycling.workitout.data

/**
 * Power zone classification based on Coggan standard zones
 */
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

/**
 * A single interval in a structured workout
 */
data class WorkoutIntervalDef(
    val durationSeconds: Int,
    val targetPowerWatts: Int,
    val name: String,
    val zone: PowerZone
)

/**
 * A complete workout definition
 */
data class WorkoutDefinition(
    val id: String,
    val name: String,
    val description: String,
    val intervals: List<WorkoutIntervalDef>,
    val totalDurationSeconds: Int = intervals.sumOf { it.durationSeconds }
)

/**
 * Workout execution state
 */
enum class WorkoutState {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    COMPLETED
}

/**
 * Snapshot of the workout engine's current state
 */
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

/**
 * A recorded data point during the workout (for the real-time graph)
 */
data class RecordedDataPoint(
    val timeSeconds: Int,
    val actualPower: Int,
    val targetPower: Int,
    val heartRate: Int,
    val cadence: Int,
    /** Wall-clock epoch millis when this point was recorded. Used by the .fit exporter. */
    val epochMillis: Long = 0L,
    /** Virtual speed derived from power + weight. */
    val speedMps: Float = 0f,
    /** Running cumulative distance at this point, in meters. */
    val distanceMeters: Float = 0f
)

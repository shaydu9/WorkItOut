package com.cycling.workitout.workout

import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef

// Demo-mode fallback workouts — watts assume 200W FTP and get re-resolved via withFtp() on entry.
object WorkoutRepository {

    private const val DEFAULT_FTP = 200

    private fun ival(
        duration: Int,
        percentFtp: Double,
        name: String,
        zone: PowerZone
    ): WorkoutIntervalDef = WorkoutIntervalDef(
        durationSeconds = duration,
        targetPowerPercentFtp = percentFtp.toFloat(),
        targetPowerWatts = (percentFtp * DEFAULT_FTP).toInt().coerceAtLeast(40),
        name = name,
        zone = zone
    )

    fun getDemoWorkout(): WorkoutDefinition {
        return WorkoutDefinition(
            id = "sweet_spot_30",
            name = "30-Min Sweet Spot",
            description = "Warmup, Sweet Spot intervals with recovery, Cooldown",
            intervals = listOf(
                ival(300, 0.65, "Warmup", PowerZone.Z2_ENDURANCE),
                ival(480, 0.90, "Sweet Spot 1", PowerZone.Z4_THRESHOLD),
                ival(120, 0.55, "Recovery", PowerZone.Z1_RECOVERY),
                ival(480, 0.92, "Sweet Spot 2", PowerZone.Z4_THRESHOLD),
                ival(120, 0.55, "Recovery", PowerZone.Z1_RECOVERY),
                ival(300, 0.50, "Cooldown", PowerZone.Z1_RECOVERY)
            )
        )
    }

    fun getAvailableWorkouts(): List<WorkoutDefinition> {
        return listOf(
            getDemoWorkout(),
            WorkoutDefinition(
                id = "ftp_test_20",
                name = "20-Min FTP Test",
                description = "Warmup, 20-minute all-out effort, Cooldown",
                intervals = listOf(
                    ival(300, 0.55, "Warmup", PowerZone.Z2_ENDURANCE),
                    ival(60, 0.80, "Opener 1", PowerZone.Z3_TEMPO),
                    ival(60, 0.50, "Rest", PowerZone.Z1_RECOVERY),
                    ival(60, 1.00, "Opener 2", PowerZone.Z4_THRESHOLD),
                    ival(120, 0.50, "Rest", PowerZone.Z1_RECOVERY),
                    ival(1200, 1.05, "FTP Effort", PowerZone.Z4_THRESHOLD),
                    ival(300, 0.50, "Cooldown", PowerZone.Z1_RECOVERY)
                )
            ),
            WorkoutDefinition(
                id = "vo2max_intervals",
                name = "VO2max Intervals",
                description = "5x3min VO2max efforts with recovery",
                intervals = listOf(
                    ival(300, 0.65, "Warmup", PowerZone.Z2_ENDURANCE),
                    ival(180, 1.20, "VO2 #1", PowerZone.Z5_VO2MAX),
                    ival(180, 0.50, "Recovery", PowerZone.Z1_RECOVERY),
                    ival(180, 1.20, "VO2 #2", PowerZone.Z5_VO2MAX),
                    ival(180, 0.50, "Recovery", PowerZone.Z1_RECOVERY),
                    ival(180, 1.20, "VO2 #3", PowerZone.Z5_VO2MAX),
                    ival(180, 0.50, "Recovery", PowerZone.Z1_RECOVERY),
                    ival(180, 1.20, "VO2 #4", PowerZone.Z5_VO2MAX),
                    ival(180, 0.50, "Recovery", PowerZone.Z1_RECOVERY),
                    ival(180, 1.20, "VO2 #5", PowerZone.Z5_VO2MAX),
                    ival(300, 0.50, "Cooldown", PowerZone.Z1_RECOVERY)
                )
            )
        )
    }
}

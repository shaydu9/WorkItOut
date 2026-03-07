package com.cycling.workitout.workout

import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef

object WorkoutRepository {

    fun getDemoWorkout(): WorkoutDefinition {
        return WorkoutDefinition(
            id = "sweet_spot_30",
            name = "30-Min Sweet Spot",
            description = "Warmup, Sweet Spot intervals with recovery, Cooldown",
            intervals = listOf(
                WorkoutIntervalDef(300, 150, "Warmup", PowerZone.Z2_ENDURANCE),
                WorkoutIntervalDef(480, 250, "Sweet Spot 1", PowerZone.Z4_THRESHOLD),
                WorkoutIntervalDef(120, 130, "Recovery", PowerZone.Z1_RECOVERY),
                WorkoutIntervalDef(480, 260, "Sweet Spot 2", PowerZone.Z4_THRESHOLD),
                WorkoutIntervalDef(120, 130, "Recovery", PowerZone.Z1_RECOVERY),
                WorkoutIntervalDef(300, 120, "Cooldown", PowerZone.Z1_RECOVERY)
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
                    WorkoutIntervalDef(300, 130, "Warmup", PowerZone.Z2_ENDURANCE),
                    WorkoutIntervalDef(60, 200, "Opener 1", PowerZone.Z3_TEMPO),
                    WorkoutIntervalDef(60, 130, "Rest", PowerZone.Z1_RECOVERY),
                    WorkoutIntervalDef(60, 250, "Opener 2", PowerZone.Z4_THRESHOLD),
                    WorkoutIntervalDef(120, 130, "Rest", PowerZone.Z1_RECOVERY),
                    WorkoutIntervalDef(1200, 270, "FTP Effort", PowerZone.Z4_THRESHOLD),
                    WorkoutIntervalDef(300, 120, "Cooldown", PowerZone.Z1_RECOVERY)
                )
            ),
            WorkoutDefinition(
                id = "vo2max_intervals",
                name = "VO2max Intervals",
                description = "5x3min VO2max efforts with recovery",
                intervals = listOf(
                    WorkoutIntervalDef(300, 150, "Warmup", PowerZone.Z2_ENDURANCE),
                    WorkoutIntervalDef(180, 320, "VO2 #1", PowerZone.Z5_VO2MAX),
                    WorkoutIntervalDef(180, 130, "Recovery", PowerZone.Z1_RECOVERY),
                    WorkoutIntervalDef(180, 320, "VO2 #2", PowerZone.Z5_VO2MAX),
                    WorkoutIntervalDef(180, 130, "Recovery", PowerZone.Z1_RECOVERY),
                    WorkoutIntervalDef(180, 320, "VO2 #3", PowerZone.Z5_VO2MAX),
                    WorkoutIntervalDef(180, 130, "Recovery", PowerZone.Z1_RECOVERY),
                    WorkoutIntervalDef(180, 320, "VO2 #4", PowerZone.Z5_VO2MAX),
                    WorkoutIntervalDef(180, 130, "Recovery", PowerZone.Z1_RECOVERY),
                    WorkoutIntervalDef(180, 320, "VO2 #5", PowerZone.Z5_VO2MAX),
                    WorkoutIntervalDef(300, 120, "Cooldown", PowerZone.Z1_RECOVERY)
                )
            )
        )
    }
}

package com.cycling.workitout.workout

import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.ui.home.Difficulty
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Procedural fallback workout generator.
 * Stand-in until Phase D wires Claude API in AiWorkoutService.
 * Produces a structured workout that scales with duration, difficulty, and FTP.
 */
object LocalWorkoutGenerator {

    fun generate(durationMinutes: Int, difficulty: Difficulty, ftp: Int): WorkoutDefinition {
        val totalSec = durationMinutes * 60
        val warmup = (totalSec * 0.15).roundToInt().coerceAtLeast(180)
        val cooldown = (totalSec * 0.12).roundToInt().coerceAtLeast(180)
        val mainBlock = totalSec - warmup - cooldown

        // Build an interval from a canonical FTP percentage — we stamp both
        // the percent (the source of truth) and a snapshot in watts at the
        // user's current FTP for immediate consumption by the engine.
        fun ival(duration: Int, percentFtp: Double, name: String, zone: PowerZone) =
            WorkoutIntervalDef(
                durationSeconds = duration,
                targetPowerPercentFtp = percentFtp.toFloat(),
                targetPowerWatts = max(40, (ftp * percentFtp).roundToInt()),
                name = name,
                zone = zone
            )

        val intervals = mutableListOf<WorkoutIntervalDef>()
        intervals += ival(warmup, 0.55, "Warmup", PowerZone.Z2_ENDURANCE)

        when (difficulty) {
            Difficulty.EASY -> {
                // Long Z2 endurance with two short tempo surges
                val surge = 120
                val surgesTotal = surge * 2
                val z2 = (mainBlock - surgesTotal) / 3
                intervals += ival(z2, 0.65, "Endurance 1", PowerZone.Z2_ENDURANCE)
                intervals += ival(surge, 0.85, "Tempo Surge 1", PowerZone.Z3_TEMPO)
                intervals += ival(z2, 0.65, "Endurance 2", PowerZone.Z2_ENDURANCE)
                intervals += ival(surge, 0.85, "Tempo Surge 2", PowerZone.Z3_TEMPO)
                intervals += ival(mainBlock - 2 * z2 - surgesTotal, 0.65, "Endurance 3", PowerZone.Z2_ENDURANCE)
            }

            Difficulty.MODERATE -> {
                // Sweet spot intervals: ~88-92% FTP, 1:3 work:recovery short
                val recovery = 120
                val workCount = when {
                    durationMinutes <= 30 -> 2
                    durationMinutes <= 45 -> 3
                    durationMinutes <= 60 -> 4
                    durationMinutes <= 75 -> 5
                    else -> 6
                }
                val totalRecovery = recovery * (workCount - 1)
                val workSec = (mainBlock - totalRecovery) / workCount
                repeat(workCount) { i ->
                    intervals += ival(workSec, 0.90, "Sweet Spot ${i + 1}", PowerZone.Z4_THRESHOLD)
                    if (i < workCount - 1) {
                        intervals += ival(recovery, 0.50, "Recovery", PowerZone.Z1_RECOVERY)
                    }
                }
            }

            Difficulty.HARD -> {
                // Threshold intervals at 100-105% FTP
                val recovery = 180
                val workCount = when {
                    durationMinutes <= 30 -> 2
                    durationMinutes <= 45 -> 3
                    durationMinutes <= 60 -> 3
                    durationMinutes <= 75 -> 4
                    else -> 5
                }
                val totalRecovery = recovery * (workCount - 1)
                val workSec = (mainBlock - totalRecovery) / workCount
                repeat(workCount) { i ->
                    intervals += ival(workSec, 1.02, "Threshold ${i + 1}", PowerZone.Z4_THRESHOLD)
                    if (i < workCount - 1) {
                        intervals += ival(recovery, 0.50, "Recovery", PowerZone.Z1_RECOVERY)
                    }
                }
            }

            Difficulty.VO2 -> {
                // 3-minute VO2 efforts at 115-120% FTP, 1:1 recovery
                val work = 180
                val recovery = 180
                val pairs = (mainBlock / (work + recovery)).coerceAtLeast(1)
                repeat(pairs) { i ->
                    intervals += ival(work, 1.18, "VO2 #${i + 1}", PowerZone.Z5_VO2MAX)
                    if (i < pairs - 1) {
                        intervals += ival(recovery, 0.50, "Recovery", PowerZone.Z1_RECOVERY)
                    }
                }
                // Pad any remaining seconds with recovery
                val used = pairs * work + (pairs - 1) * recovery
                val leftover = mainBlock - used
                if (leftover > 30) {
                    intervals += ival(leftover, 0.55, "Spin", PowerZone.Z1_RECOVERY)
                }
            }
        }

        intervals += ival(cooldown, 0.50, "Cooldown", PowerZone.Z1_RECOVERY)

        // Drop any zero/negative intervals from integer rounding
        val cleaned = intervals.filter { it.durationSeconds > 0 }

        val name = when (difficulty) {
            Difficulty.EASY -> "${durationMinutes}-Min Endurance"
            Difficulty.MODERATE -> "${durationMinutes}-Min Sweet Spot"
            Difficulty.HARD -> "${durationMinutes}-Min Threshold"
            Difficulty.VO2 -> "${durationMinutes}-Min VO2 Max"
        }
        val description = when (difficulty) {
            Difficulty.EASY -> "Aerobic base ride with brief tempo surges"
            Difficulty.MODERATE -> "Sweet spot intervals at ~90% FTP with short recoveries"
            Difficulty.HARD -> "Threshold repeats at ~102% FTP"
            Difficulty.VO2 -> "Hard 3-minute VO2 max efforts with equal recovery"
        }

        return WorkoutDefinition(
            id = "local_${difficulty.name.lowercase()}_$durationMinutes",
            name = name,
            description = description,
            intervals = cleaned
        )
    }

    /** The durations offered in the default starter library (also the Home quick-pick set). */
    val DEFAULT_DURATIONS: List<Int> = listOf(30, 45, 60, 75, 90)

    /**
     * A 5×4 grid of starter workouts: every supported duration at every difficulty.
     * Deterministic — same inputs, same output — so we don't need to persist these.
     */
    fun getDefaultLibrary(ftp: Int): List<WorkoutDefinition> =
        DEFAULT_DURATIONS.flatMap { duration ->
            Difficulty.values().map { difficulty -> generate(duration, difficulty, ftp) }
        }
}

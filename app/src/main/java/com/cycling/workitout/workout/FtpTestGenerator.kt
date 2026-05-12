package com.cycling.workitout.workout

import com.cycling.workitout.data.FtpTestKind
import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef

object FtpTestGenerator {
    private fun roundToNearest20(w: Int): Int = ((w + 10) / 20) * 20
    private fun roundUpToNearest20(w: Int): Int = ((w + 19) / 20) * 20

    fun ramp(currentFtp: Int): WorkoutDefinition {
        val step = 20
        val start = roundToNearest20((currentFtp * 0.50).toInt()).coerceAtLeast(100)
        val ceiling = roundUpToNearest20((currentFtp * 1.35).toInt())

        fun ival(
            duration: Int, watts: Int, name: String, measurement: Boolean = false
        ) =
            WorkoutIntervalDef(
                durationSeconds = duration,
                targetPowerPercentFtp = watts.toFloat() / currentFtp.coerceAtLeast(1),
                targetPowerWatts = watts,
                name = name,
                zone = PowerZone.fromAbsoluteWatts(watts, currentFtp.coerceAtLeast(1)),
                ftpTestMeasurement = measurement,
                ergDisabled = false
            )

        val intervals = mutableListOf<WorkoutIntervalDef>()
        intervals += ival(300, start, "Warmup")
        (start..ceiling step step).forEachIndexed { i, watts ->
            intervals += ival(60, watts, "Step ${i + 1} (${watts}W)", measurement = true)
        }
        intervals += ival(300, start, "Cooldown")

        return WorkoutDefinition(
            id = "ftp_test_ramp",
            name = "FTP Ramp Test",
            description = "Stepped 1-minute increases until you can't hold cadence. FTP ≈ best 1-minute power × 0.75.",
            intervals = intervals,
            ftpTest = FtpTestKind.RAMP
        )
    }

    fun twentyMin(currentFtp: Int): WorkoutDefinition {
        fun ival(
            duration: Int,
            percentFtp: Float,
            name: String,
            zone: PowerZone,
            measurement: Boolean = false,
            ergOff: Boolean = false
        ): WorkoutIntervalDef {
            val watts = (currentFtp * percentFtp).toInt().coerceAtLeast(40)
            return WorkoutIntervalDef(
                durationSeconds = duration,
                targetPowerPercentFtp = percentFtp,
                targetPowerWatts = watts,
                name = name,
                zone = zone,
                ftpTestMeasurement = measurement,
                ergDisabled = ergOff
            )
        }

        val intervals = listOf(
            ival(600, 0.55f, "Warmup", PowerZone.Z2_ENDURANCE),
            ival(300, 1.05f, "Blowout", PowerZone.Z5_VO2MAX),
            ival(600, 0.50f, "Recovery", PowerZone.Z1_RECOVERY),
            ival(
                1200,
                1.00f,
                "20-Min Test",
                PowerZone.Z4_THRESHOLD,
                measurement = true,
                ergOff = true
            ),
            ival(300, 0.50f, "Cooldown", PowerZone.Z1_RECOVERY)
        )

        return WorkoutDefinition(
            id = "ftp_test_20min",
            name = "FTP 20-Min Test",
            description = "Warmup, 5-min blowout, recovery, then 20 min all-out (ERG off — you drive the pace). FTP ≈ 20-min avg × 0.95.",
            intervals = intervals,
            ftpTest = FtpTestKind.TWENTY_MIN
        )
    }
}
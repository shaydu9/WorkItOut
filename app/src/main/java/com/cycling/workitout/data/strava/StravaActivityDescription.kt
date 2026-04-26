package com.cycling.workitout.data.strava

import com.cycling.workitout.data.database.CompletedRideEntity
import kotlin.math.roundToInt

// Builds the text we send as the Strava activity description. Strava already shows
// duration + distance on the activity itself, so we don't repeat those — just the
// derived training metrics that aren't visible by default.
object StravaActivityDescription {

    fun from(ride: CompletedRideEntity): String = buildString {
        appendLine("Avg Power: ${ride.avgPowerWatts} W")
        appendLine("NP: ${ride.normalizedPowerWatts} W")
        appendLine("Max Power: ${ride.maxPowerWatts} W")
        appendLine("Avg HR: ${ride.avgHeartRate} bpm")
        appendLine("TSS: ${tss(ride)}")
        appendLine()
        append("Powered by WorkItOut")
    }

    // TSS = 100 × IF² × hours, where IF = NP / FTP. Returns 0 if FTP is missing
    // (shouldn't happen — ftpWatts is required on the row — but cheap to guard).
    private fun tss(ride: CompletedRideEntity): Int {
        if (ride.ftpWatts <= 0) return 0
        val intensityFactor = ride.normalizedPowerWatts.toDouble() / ride.ftpWatts.toDouble()
        val hours = ride.durationSeconds / 3600.0
        return (100.0 * intensityFactor * intensityFactor * hours).roundToInt()
    }
}

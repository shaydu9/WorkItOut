package com.cycling.workitout.workout

import com.cycling.workitout.data.FtpTestKind
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import kotlin.math.roundToInt

object FtpEstimator {
    /**
     * Estimates FTP in watts from recorded data for an FTP-test workout.
     * Returns null if the test was too short / no valid samples in the measurement window.
     */
    fun estimate(
        kind: FtpTestKind,
        dataPoints: List<RecordedDataPoint>,
        workout: WorkoutDefinition
    ) : Int? {
        val (startSec, endSec) = measurementWindowSeconds(workout) ?: return null
        val window = dataPoints.filter {
            it.timeSeconds >= startSec && it.timeSeconds < endSec && it.actualPower > 0
        }
        if (window.isEmpty()) return null

        return when (kind) {
            FtpTestKind.RAMP -> {
                val best60 = bestRollingAverage(window, windowSeconds = 60) ?: return null
                (best60 * 0.75).roundToInt()
            }
            FtpTestKind.TWENTY_MIN -> {
                val avg = window.map { it.actualPower }.average()
                (avg * 0.95).roundToInt()
            }
        }
    }
    /**
     * Returns [startSec, endSec) — the time window inside which to evaluate.
     * Returns null if the workout has no measurement interval.
     */
    private fun measurementWindowSeconds(workout: WorkoutDefinition): Pair<Int, Int>? {
        var elapsed = 0
        var start = -1
        var end = -1
        for (interval in workout.intervals) {
            if (interval.ftpTestMeasurement) {
                if (start == -1) start = elapsed
                end = elapsed + interval.durationSeconds
            }
            elapsed += interval.durationSeconds
        }

        return if (start >= 0 && end > start) start to end else null
    }

    /**
     * Returns the highest [windowSeconds]-second rolling average power across [points].
     * Assumes 1Hz samples (one per workout second). Returns null if not enough samples.
     */
    private fun bestRollingAverage(points: List<RecordedDataPoint>, windowSeconds: Int): Double? {
        if (points.size < windowSeconds) return null
        var best = 0.0
        for (i in 0..(points.size - windowSeconds)) {
            val avg = points.subList(i, i + windowSeconds).sumOf { it.actualPower }
                .toDouble() / windowSeconds
            if (avg > best) best = avg
        }

        return if (best > 0) best else null
    }
}
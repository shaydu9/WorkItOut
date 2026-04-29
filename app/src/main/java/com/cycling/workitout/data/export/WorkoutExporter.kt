package com.cycling.workitout.data.export

import android.content.Context
import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.data.firestore.Ride
import com.cycling.workitout.ui.workout.CompactDataPoint
import com.cycling.workitout.workout.VirtualSpeedEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Writes .fit files to context.filesDir/workouts/ and exposes them via FileProvider for Strava upload.
object WorkoutExporter {

    private val FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val historyJson = Json { ignoreUnknownKeys = true }

    suspend fun exportToFit(
        context: Context,
        workout: WorkoutDefinition,
        startEpochMillis: Long,
        records: List<RecordedDataPoint>
    ): File = withContext(Dispatchers.IO) {
        val out = fitFileFor(context, startEpochMillis)
        out.parentFile?.mkdirs()
        FitFileWriter.write(
            outputFile = out,
            workout = workout,
            startEpochMillis = startEpochMillis,
            records = records
        )
    }

    fun fitFileFor(context: Context, startedAtMillis: Long): File {
        val dir = File(context.filesDir, "workouts")
        val name = FILENAME_FORMAT.format(Date(startedAtMillis)) + ".fit"
        return File(dir, name)
    }

    // Rebuilds a .fit from a stored history row — single-lap stub, recomputed speed, no per-interval analytics.
    suspend fun exportFromHistory(
        context: Context,
        ride: Ride,
        riderWeightKg: Int
    ): File = withContext(Dispatchers.IO) {
        val samples = historyJson.decodeFromString<List<CompactDataPoint>>(ride.dataPointsJson)

        val stubInterval = WorkoutIntervalDef(
            durationSeconds = ride.durationSeconds.coerceAtLeast(1),
            targetPowerPercentFtp = 0.65f,
            targetPowerWatts = ride.avgPowerWatts,
            name = ride.name,
            zone = PowerZone.Z2_ENDURANCE
        )
        val stubWorkout = WorkoutDefinition(
            id = "history-${ride.id}",
            name = ride.name,
            description = "Reconstructed from history",
            intervals = listOf(stubInterval)
        )

        val estimator = VirtualSpeedEstimator(riderWeightKg = riderWeightKg.toDouble())
        var cumulativeMeters = 0.0
        var lastTimeSec = 0
        val records = samples.map { p ->
            val dt = (p.t - lastTimeSec).coerceAtLeast(0)
            lastTimeSec = p.t
            val speedMps = estimator.speedMpsFor(p.p)
            cumulativeMeters += speedMps * dt
            RecordedDataPoint(
                timeSeconds = p.t,
                actualPower = p.p,
                targetPower = p.tp,
                heartRate = p.hr,
                cadence = p.c,
                epochMillis = ride.startedAtMillis + p.t * 1000L,
                speedMps = speedMps,
                distanceMeters = cumulativeMeters.toFloat()
            )
        }

        exportToFit(
            context = context,
            workout = stubWorkout,
            startEpochMillis = ride.startedAtMillis,
            records = records
        )
    }
}

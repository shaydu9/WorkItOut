package com.cycling.workitout.data.export

import android.content.Context
import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.data.database.CompletedRideEntity
import com.cycling.workitout.ui.workout.CompactDataPoint
import com.cycling.workitout.workout.VirtualSpeedEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small orchestrator around [FitFileWriter] that knows where to put files
 * on the Android filesystem.
 *
 * Files land in: `context.filesDir/workouts/{yyyyMMdd_HHmmss}.fit`
 *
 * That directory is exposed to share sheets / Strava upload via the
 * `workitout.fileprovider` FileProvider declared in the manifest.
 */
object WorkoutExporter {

    private val FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val historyJson = Json { ignoreUnknownKeys = true }

    /**
     * Write the workout to a .fit file in the app's private `workouts/` dir.
     * Safe to call from any thread; the encode runs on Dispatchers.IO.
     */
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

    /**
     * Compute the canonical .fit path for a ride that started at [startedAtMillis].
     * The file may or may not exist on disk — callers must check [File.exists].
     */
    fun fitFileFor(context: Context, startedAtMillis: Long): File {
        val dir = File(context.filesDir, "workouts")
        val name = FILENAME_FORMAT.format(Date(startedAtMillis)) + ".fit"
        return File(dir, name)
    }

    /**
     * Build a .fit from a stored history row when the original file is gone
     * (or never existed — e.g. rides recorded before the .fit feature shipped).
     *
     * The lap structure of the original workout is lost — old rows only store
     * the per-second sample blob, not the interval boundaries — so we synthesise
     * a single-interval [WorkoutDefinition] covering the whole ride. That's
     * enough for Strava to categorise the upload correctly; lap analytics are
     * forfeit for these regenerated files.
     *
     * Speed + distance are recomputed from power + the supplied weight via the
     * same [VirtualSpeedEstimator] used live, so the regenerated file shows up
     * as a proper Virtual Ride.
     */
    suspend fun exportFromHistory(
        context: Context,
        ride: CompletedRideEntity,
        riderWeightKg: Int
    ): File = withContext(Dispatchers.IO) {
        val samples = historyJson.decodeFromString<List<CompactDataPoint>>(ride.dataPointsJson)

        val stubInterval = WorkoutIntervalDef(
            durationSeconds = ride.durationSeconds.coerceAtLeast(1),
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

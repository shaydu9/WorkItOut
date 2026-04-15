package com.cycling.workitout.data.export

import android.content.Context
import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val dir = File(context.filesDir, "workouts").apply { mkdirs() }
        val name = FILENAME_FORMAT.format(Date(startEpochMillis)) + ".fit"
        val out = File(dir, name)
        FitFileWriter.write(
            outputFile = out,
            workout = workout,
            startEpochMillis = startEpochMillis,
            records = records
        )
    }
}

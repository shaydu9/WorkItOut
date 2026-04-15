package com.cycling.workitout.data.export

import com.cycling.workitout.data.RecordedDataPoint
import com.cycling.workitout.data.WorkoutDefinition
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.File as FitFile
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import timber.log.Timber
import java.io.File as JavaFile
import java.util.Date

/**
 * Writes a Garmin-compatible .fit activity file from a completed workout.
 *
 * The file layout follows Garmin's "Activity File" recipe:
 *
 *   FileId → DeviceInfo → Event(start) → [Record per second]* → [Lap per interval]*
 *                                                             → Event(stop) → Session → Activity
 *
 * Output is consumable by Strava, Garmin Connect, TrainingPeaks, etc.
 */
object FitFileWriter {

    /**
     * Write a .fit file for the given completed workout.
     *
     * @param outputFile absolute path to write to
     * @param workout the workout definition (intervals, name)
     * @param startEpochMillis wall-clock start of the workout
     * @param records per-second samples captured by [com.cycling.workitout.workout.WorkoutEngine]
     * @return the written file, for callers to hand off to a share sheet or upload client
     */
    fun write(
        outputFile: JavaFile,
        workout: WorkoutDefinition,
        startEpochMillis: Long,
        records: List<RecordedDataPoint>
    ): JavaFile {
        outputFile.parentFile?.mkdirs()

        val startDate = Date(startEpochMillis)
        val startTs = DateTime(startDate)

        val encoder = FileEncoder(outputFile, Fit.ProtocolVersion.V2_0)
        try {
            // 1. File ID — required first message. Marks this as an ACTIVITY file.
            val fileId = FileIdMesg().apply {
                type = FitFile.ACTIVITY
                manufacturer = Manufacturer.DEVELOPMENT
                product = 0
                serialNumber = (startEpochMillis and 0xFFFFFFFFL)
                timeCreated = startTs
            }
            encoder.write(fileId)

            // 2. Device info — "WorkItOut" app as the data source.
            val deviceInfo = DeviceInfoMesg().apply {
                timestamp = startTs
                manufacturer = Manufacturer.DEVELOPMENT
                product = 0
                productName = "WorkItOut"
            }
            encoder.write(deviceInfo)

            // 3. Start event.
            val startEvent = EventMesg().apply {
                timestamp = startTs
                event = Event.TIMER
                eventType = EventType.START
            }
            encoder.write(startEvent)

            // 4. Record messages — one per sample. Sorted by epoch just in case.
            val sorted = records.sortedBy { it.epochMillis }
            for (r in sorted) {
                val rec = RecordMesg().apply {
                    timestamp = DateTime(Date(r.epochMillis))
                    power = r.actualPower
                    heartRate = r.heartRate.toShort()
                    cadence = r.cadence.toShort()
                }
                encoder.write(rec)
            }

            // 5. Lap messages — one per workout interval.
            // We translate interval time offsets into wall-clock, then compute per-lap stats
            // from the records that fall inside each lap.
            val lapEndEpochs = mutableListOf<Long>()
            var cumulativeSec = 0
            for ((idx, interval) in workout.intervals.withIndex()) {
                val lapStartSec = cumulativeSec
                cumulativeSec += interval.durationSeconds
                val lapEndSec = cumulativeSec

                val lapStartMillis = startEpochMillis + lapStartSec * 1000L
                val lapEndMillis = startEpochMillis + lapEndSec * 1000L
                lapEndEpochs += lapEndMillis

                val lapRecords = sorted.filter {
                    it.epochMillis in lapStartMillis until lapEndMillis
                }

                val lap = LapMesg().apply {
                    messageIndex = idx
                    startTime = DateTime(Date(lapStartMillis))
                    timestamp = DateTime(Date(lapEndMillis))
                    totalElapsedTime = interval.durationSeconds.toFloat()
                    totalTimerTime = interval.durationSeconds.toFloat()
                    event = Event.LAP
                    eventType = EventType.STOP
                    sport = Sport.CYCLING
                    subSport = SubSport.INDOOR_CYCLING

                    if (lapRecords.isNotEmpty()) {
                        val powers = lapRecords.map { it.actualPower }.filter { it > 0 }
                        val hrs = lapRecords.map { it.heartRate }.filter { it > 0 }
                        val cads = lapRecords.map { it.cadence }.filter { it >= 0 }
                        if (powers.isNotEmpty()) {
                            avgPower = powers.average().toInt()
                            maxPower = powers.max()
                        }
                        if (hrs.isNotEmpty()) {
                            avgHeartRate = hrs.average().toInt().toShort()
                            maxHeartRate = hrs.max().toShort()
                        }
                        if (cads.isNotEmpty()) {
                            avgCadence = cads.average().toInt().toShort()
                            maxCadence = cads.max().toShort()
                        }
                    }
                }
                encoder.write(lap)
            }

            val endMillis = lapEndEpochs.lastOrNull()
                ?: (startEpochMillis + workout.totalDurationSeconds * 1000L)
            val endTs = DateTime(Date(endMillis))
            val totalElapsed = workout.totalDurationSeconds.toFloat()

            // 6. Stop event.
            val stopEvent = EventMesg().apply {
                timestamp = endTs
                event = Event.TIMER
                eventType = EventType.STOP_ALL
            }
            encoder.write(stopEvent)

            // 7. Session — overall summary stats across the full activity.
            val allPowers = sorted.map { it.actualPower }.filter { it > 0 }
            val allHrs = sorted.map { it.heartRate }.filter { it > 0 }
            val allCads = sorted.map { it.cadence }.filter { it >= 0 }

            val session = SessionMesg().apply {
                messageIndex = 0
                timestamp = endTs
                startTime = startTs
                totalElapsedTime = totalElapsed
                totalTimerTime = totalElapsed
                sport = Sport.CYCLING
                subSport = SubSport.INDOOR_CYCLING
                event = Event.SESSION
                eventType = EventType.STOP
                firstLapIndex = 0
                numLaps = workout.intervals.size
                if (allPowers.isNotEmpty()) {
                    avgPower = allPowers.average().toInt()
                    maxPower = allPowers.max()
                }
                if (allHrs.isNotEmpty()) {
                    avgHeartRate = allHrs.average().toInt().toShort()
                    maxHeartRate = allHrs.max().toShort()
                }
                if (allCads.isNotEmpty()) {
                    avgCadence = allCads.average().toInt().toShort()
                }
            }
            encoder.write(session)

            // 8. Activity — outermost container.
            val activity = ActivityMesg().apply {
                timestamp = endTs
                totalTimerTime = totalElapsed
                numSessions = 1
                type = com.garmin.fit.Activity.MANUAL
                event = Event.ACTIVITY
                eventType = EventType.STOP
                // Local timestamp is used by some importers to reconstruct the user's timezone.
                val tzOffsetSec = java.util.TimeZone.getDefault().getOffset(endMillis) / 1000L
                localTimestamp = endTs.timestamp + tzOffsetSec
            }
            encoder.write(activity)
        } finally {
            encoder.close()
        }

        Timber.i("Wrote .fit file: ${outputFile.absolutePath} (${outputFile.length()} bytes, ${records.size} records)")
        return outputFile
    }
}

package com.cycling.workitout.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cycling.workitout.data.firestore.Ride
import com.cycling.workitout.data.strava.HistoryStravaUploader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.workitout.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.ui.workout.CompactDataPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    viewModel: RideDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val ride by viewModel.ride.collectAsStateWithLifecycle()
    val dataPoints by viewModel.dataPoints.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val isStravaConnected by viewModel.isStravaConnected.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ride?.name ?: "Ride Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val r = ride
        if (r == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RideHeader(r)

                StravaSyncSection(
                    ride = r,
                    uploadState = uploadState,
                    isStravaConnected = isStravaConnected,
                    onUpload = viewModel::uploadToStrava,
                    onClearError = viewModel::clearUploadError
                )

                if (dataPoints.isNotEmpty()) {
                    SectionTitle("Power")
                    PowerGraph(
                        dataPoints = dataPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }

                if (dataPoints.isNotEmpty() && dataPoints.any { it.hr > 0 }) {
                    SectionTitle("Heart Rate")
                    HeartRateGraph(
                        dataPoints = dataPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }

                SectionTitle("Summary")
                StatsGrid(r)
            }
        }
    }
}

@Composable
private fun RideHeader(ride: Ride) {
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(ride.startedAtMillis))
    val durationMin = ride.durationSeconds / 60
    val durationSec = ride.durationSeconds % 60

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                ride.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                dateStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Duration: %d:%02d".format(durationMin, durationSec),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StravaSyncSection(
    ride: Ride,
    uploadState: HistoryStravaUploader.UploadState,
    isStravaConnected: Boolean,
    onUpload: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    // DB row is source of truth; transient Success counts as "done" too.
    val persistedActivityId = ride.stravaActivityId
    val transientActivityId = (uploadState as? HistoryStravaUploader.UploadState.Success)?.activityId
    val activityId = persistedActivityId ?: transientActivityId

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                activityId != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFFC4C02), // Strava orange
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Synced to Strava",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.strava.com/activities/$activityId")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("View on Strava")
                        }
                    }
                }
                !isStravaConnected -> {
                    Text(
                        "Connect Strava in Settings to sync this ride.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uploadState is HistoryStravaUploader.UploadState.Uploading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Uploading to Strava…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    Column {
                        Button(
                            onClick = onUpload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Upload to Strava")
                        }
                        if (uploadState is HistoryStravaUploader.UploadState.Failed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Upload failed: ${uploadState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onClearError) { Text("Dismiss") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(R.drawable.api_logo_pwrdby_strava_horiz_orange),
                contentDescription = "Powered by Strava",
                modifier = Modifier
                    .align(Alignment.End)
                    .height(20.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun StatsGrid(ride: Ride) {
    val intensityFactor = if (ride.ftpWatts > 0) {
        ride.normalizedPowerWatts.toFloat() / ride.ftpWatts
    } else 0f
    val durationMin = ride.durationSeconds / 60
    val tss = (intensityFactor * intensityFactor * durationMin / 60f * 100f).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailStatCard("Avg Power", "${ride.avgPowerWatts}W", Modifier.weight(1f))
            DetailStatCard("Max Power", "${ride.maxPowerWatts}W", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailStatCard("NP", "${ride.normalizedPowerWatts}W", Modifier.weight(1f))
            DetailStatCard("IF", "%.2f".format(intensityFactor), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailStatCard("TSS", "$tss", Modifier.weight(1f))
            DetailStatCard("FTP", "${ride.ftpWatts}W", Modifier.weight(1f))
        }
        if (ride.avgHeartRate > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailStatCard("Avg HR", "${ride.avgHeartRate}bpm", Modifier.weight(1f))
                DetailStatCard("Max HR", "${ride.maxHeartRate}bpm", Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailStatCard("Avg Cadence", "${ride.avgCadence}rpm", Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun PowerGraph(dataPoints: List<CompactDataPoint>, modifier: Modifier = Modifier) {
    val powerColor = Color(0xFF00BCD4)
    val targetColor = Color(0xFFFFC107).copy(alpha = 0.5f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (dataPoints.size < 2) return@Canvas
                val maxTime = dataPoints.last().t.toFloat().coerceAtLeast(1f)
                val maxPower = dataPoints.maxOf { maxOf(it.p, it.tp) }.toFloat().coerceAtLeast(1f) * 1.1f

                val targetPath = Path()
                dataPoints.forEachIndexed { i, dp ->
                    val x = (dp.t / maxTime) * size.width
                    val y = size.height - (dp.tp / maxPower) * size.height
                    if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                }
                drawPath(targetPath, targetColor, style = Stroke(width = 2.dp.toPx()))

                val powerPath = Path()
                dataPoints.forEachIndexed { i, dp ->
                    val x = (dp.t / maxTime) * size.width
                    val y = size.height - (dp.p / maxPower) * size.height
                    if (i == 0) powerPath.moveTo(x, y) else powerPath.lineTo(x, y)
                }
                drawPath(powerPath, powerColor, style = Stroke(width = 2.dp.toPx()))
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendDot(powerColor, "Power")
                LegendDot(targetColor, "Target")
            }
        }
    }
}

@Composable
private fun HeartRateGraph(dataPoints: List<CompactDataPoint>, modifier: Modifier = Modifier) {
    val hrColor = Color(0xFFFF5252)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val hrPoints = dataPoints.filter { it.hr > 0 }
                if (hrPoints.size < 2) return@Canvas
                val maxTime = dataPoints.last().t.toFloat().coerceAtLeast(1f)
                val minHr = hrPoints.minOf { it.hr }.toFloat() * 0.9f
                val maxHr = hrPoints.maxOf { it.hr }.toFloat() * 1.05f
                val hrRange = (maxHr - minHr).coerceAtLeast(1f)

                val path = Path()
                hrPoints.forEachIndexed { i, dp ->
                    val x = (dp.t / maxTime) * size.width
                    val y = size.height - ((dp.hr - minHr) / hrRange) * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, hrColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

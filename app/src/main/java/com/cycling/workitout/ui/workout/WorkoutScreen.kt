package com.cycling.workitout.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutState
import com.cycling.workitout.ui.components.PowerDataPoint
import com.cycling.workitout.ui.components.WorkoutInterval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onNavigateBack: () -> Unit
) {
    val progress by viewModel.workoutProgress.collectAsStateWithLifecycle()
    val metrics by viewModel.liveMetrics.collectAsStateWithLifecycle()
    val recordedData by viewModel.recordedData.collectAsStateWithLifecycle()

    // Keep screen on during workout
    val view = LocalView.current
    DisposableEffect(progress.workoutState) {
        if (progress.workoutState == WorkoutState.RUNNING) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    val zoneColor = Color(progress.currentZone.colorHex)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══════════════════════════════════════
            // TOP SECTION (~60%) — Metrics Panel
            // ═══════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(
                        zoneColor.copy(alpha = 0.05f)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Control bar
                ControlBar(
                    workoutState = progress.workoutState,
                    intervalName = progress.currentIntervalName,
                    onStart = viewModel::startWorkout,
                    onPause = viewModel::pauseWorkout,
                    onResume = viewModel::resumeWorkout,
                    onStop = viewModel::stopWorkout,
                    onBack = onNavigateBack
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (progress.workoutState) {
                    WorkoutState.NOT_STARTED -> {
                        NotStartedContent(
                            workoutName = progress.workoutName,
                            totalDuration = progress.totalDurationSeconds,
                            intervals = viewModel.workoutIntervals,
                            onStart = viewModel::startWorkout
                        )
                    }

                    WorkoutState.COMPLETED -> {
                        CompletedContent(
                            recordedData = recordedData.map {
                                PowerDataPoint(it.timeSeconds, it.actualPower)
                            },
                            totalDuration = progress.totalDurationSeconds,
                            onBack = onNavigateBack
                        )
                    }

                    else -> {
                        // RUNNING or PAUSED — show live metrics
                        LiveMetricsContent(
                            currentPower = metrics.power,
                            targetPower = progress.targetPowerWatts,
                            heartRate = metrics.heartRate,
                            cadence = metrics.cadence,
                            zone = progress.currentZone,
                            zoneColor = zoneColor,
                            intervalRemaining = progress.intervalRemainingSeconds,
                            intervalDuration = progress.intervalDurationSeconds,
                            totalElapsed = progress.totalElapsedSeconds,
                            totalDuration = progress.totalDurationSeconds,
                            isPaused = progress.workoutState == WorkoutState.PAUSED
                        )
                    }
                }
            }

            // Divider between sections
            HorizontalDivider(
                color = zoneColor.copy(alpha = 0.3f),
                thickness = 2.dp
            )

            // ═══════════════════════════════════════
            // BOTTOM SECTION (~40%) — Workout Graph
            // ═══════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                WorkoutProgressGraph(
                    intervals = viewModel.workoutIntervals,
                    currentTimeSeconds = progress.totalElapsedSeconds,
                    totalDurationSeconds = progress.totalDurationSeconds,
                    recordedPowerPoints = recordedData.map {
                        PowerDataPoint(it.timeSeconds, it.actualPower)
                    },
                    targetPower = progress.targetPowerWatts,
                    workoutState = progress.workoutState
                )
            }
        }
    }
}

@Composable
private fun ControlBar(
    workoutState: WorkoutState,
    intervalName: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (workoutState) {
            WorkoutState.NOT_STARTED -> {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "WORKOUT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            WorkoutState.RUNNING -> {
                IconButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = intervalName.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                }
            }

            WorkoutState.PAUSED -> {
                IconButton(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                }
            }

            WorkoutState.COMPLETED -> {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "WORKOUT COMPLETE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun LiveMetricsContent(
    currentPower: Int,
    targetPower: Int,
    heartRate: Int,
    cadence: Int,
    zone: PowerZone,
    zoneColor: Color,
    intervalRemaining: Int,
    intervalDuration: Int,
    totalElapsed: Int,
    totalDuration: Int,
    isPaused: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Current power — the hero number
        val powerDiff = if (targetPower > 0) currentPower - targetPower else 0
        val powerColor = when {
            targetPower == 0 -> MaterialTheme.colorScheme.primary
            kotlin.math.abs(powerDiff) <= targetPower * 0.10 -> Color(0xFF4CAF50) // Green - on target
            powerDiff > 0 -> Color(0xFFFF5252) // Red - too high
            else -> Color(0xFF2196F3) // Blue - too low
        }

        Text(
            text = "$currentPower",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = powerColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "W",
            fontSize = 20.sp,
            color = powerColor.copy(alpha = 0.7f),
            modifier = Modifier.offset(y = (-8).dp)
        )

        // Target power + zone
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Target: ${targetPower}W",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = zoneColor
            )
            Text(
                text = "  ·  ",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = zone.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = zoneColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // HR and Cadence row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricBox(
                value = "$heartRate",
                unit = "bpm",
                label = "Heart Rate",
                color = Color(0xFFFF5252)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            MetricBox(
                value = "$cadence",
                unit = "rpm",
                label = "Cadence",
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interval progress bar
        val intervalProgress = if (intervalDuration > 0) {
            1f - (intervalRemaining.toFloat() / intervalDuration.toFloat())
        } else 0f

        LinearProgressIndicator(
            progress = { intervalProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = zoneColor,
            trackColor = zoneColor.copy(alpha = 0.15f),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Dual timers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Interval",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(intervalRemaining),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (intervalRemaining <= 10) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface
                )
            }

            if (isPaused) {
                Text(
                    text = "PAUSED",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatTime(totalElapsed)} / ${formatTime(totalDuration)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MetricBox(
    value: String,
    unit: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = " $unit",
                fontSize = 14.sp,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.offset(y = (-4).dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotStartedContent(
    workoutName: String,
    totalDuration: Int,
    intervals: List<WorkoutInterval>,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = workoutName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${intervals.size} intervals · ${formatTime(totalDuration)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "START WORKOUT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompletedContent(
    recordedData: List<PowerDataPoint>,
    totalDuration: Int,
    onBack: () -> Unit
) {
    val avgPower = if (recordedData.isNotEmpty()) {
        recordedData.map { it.power }.average().toInt()
    } else 0

    val maxPower = recordedData.maxOfOrNull { it.power } ?: 0

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color(0xFFFFC107)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Workout Complete!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem("Duration", formatTime(totalDuration))
            SummaryItem("Avg Power", "${avgPower}W")
            SummaryItem("Max Power", "${maxPower}W")
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(onClick = onBack) {
            Text("Done")
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// BOTTOM SECTION — Workout Progress Graph
// ═══════════════════════════════════════════════════════════════

@Composable
fun WorkoutProgressGraph(
    intervals: List<WorkoutInterval>,
    currentTimeSeconds: Int,
    totalDurationSeconds: Int,
    recordedPowerPoints: List<PowerDataPoint>,
    targetPower: Int,
    workoutState: WorkoutState
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WORKOUT PROGRESS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (workoutState == WorkoutState.RUNNING || workoutState == WorkoutState.PAUSED) {
                Text(
                    text = "${formatTime(currentTimeSeconds)} / ${formatTime(totalDurationSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Workout structure as colored blocks with position indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            WorkoutBlocksGraph(
                intervals = intervals,
                currentTimeSeconds = currentTimeSeconds,
                totalDurationSeconds = totalDurationSeconds,
                recordedPowerPoints = recordedPowerPoints,
                workoutState = workoutState
            )
        }

        // Legend
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZoneLegendItem(Color(0xFF9E9E9E), "Z1")
            ZoneLegendItem(Color(0xFF2196F3), "Z2")
            ZoneLegendItem(Color(0xFF4CAF50), "Z3")
            ZoneLegendItem(Color(0xFFFFC107), "Z4")
            ZoneLegendItem(Color(0xFFFF9800), "Z5")
            ZoneLegendItem(Color(0xFFFF5252), "Z6")
        }
    }
}

@Composable
private fun WorkoutBlocksGraph(
    intervals: List<WorkoutInterval>,
    currentTimeSeconds: Int,
    totalDurationSeconds: Int,
    recordedPowerPoints: List<PowerDataPoint>,
    workoutState: WorkoutState
) {
    if (intervals.isEmpty() || totalDurationSeconds == 0) return

    val maxPower = intervals.maxOf { it.targetPower }.toFloat().coerceAtLeast(1f)

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalDuration = totalDurationSeconds.toFloat()

        // Draw interval blocks
        var cumulativeSeconds = 0f
        for (interval in intervals) {
            val blockStart = (cumulativeSeconds / totalDuration) * canvasWidth
            val blockWidth = (interval.durationSeconds.toFloat() / totalDuration) * canvasWidth
            val blockHeight = (interval.targetPower.toFloat() / maxPower) * canvasHeight * 0.85f

            drawRect(
                color = interval.color.copy(alpha = 0.4f),
                topLeft = androidx.compose.ui.geometry.Offset(blockStart, canvasHeight - blockHeight),
                size = androidx.compose.ui.geometry.Size(blockWidth, blockHeight)
            )

            // Block border
            drawRect(
                color = interval.color.copy(alpha = 0.7f),
                topLeft = androidx.compose.ui.geometry.Offset(blockStart, canvasHeight - blockHeight),
                size = androidx.compose.ui.geometry.Size(blockWidth, blockHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            cumulativeSeconds += interval.durationSeconds.toFloat()
        }

        // Draw actual power trace
        if (recordedPowerPoints.size >= 2) {
            val path = androidx.compose.ui.graphics.Path()
            var started = false

            for (point in recordedPowerPoints) {
                val x = (point.timeSeconds.toFloat() / totalDuration) * canvasWidth
                val normalizedPower = (point.power.toFloat() / maxPower).coerceIn(0f, 1f)
                val y = canvasHeight - (normalizedPower * canvasHeight * 0.85f)

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF00BCD4),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }

        // Draw current position indicator line
        if (workoutState == WorkoutState.RUNNING || workoutState == WorkoutState.PAUSED) {
            val posX = (currentTimeSeconds.toFloat() / totalDuration) * canvasWidth

            // Vertical line
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(posX, 0f),
                end = androidx.compose.ui.geometry.Offset(posX, canvasHeight),
                strokeWidth = 2.dp.toPx()
            )

            // Small triangle at top
            val trianglePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(posX, 0f)
                lineTo(posX - 5.dp.toPx(), -2.dp.toPx())
                lineTo(posX + 5.dp.toPx(), -2.dp.toPx())
                close()
            }
            drawPath(
                path = trianglePath,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ZoneLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

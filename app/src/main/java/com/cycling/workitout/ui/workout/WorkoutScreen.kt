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
    val ergEnabled by viewModel.ergEnabled.collectAsStateWithLifecycle()

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
            // TOP HALF (50%) — Workout Progress Graph
            // ═══════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
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
                    workoutState = progress.workoutState,
                    workoutName = progress.workoutName
                )
            }

            HorizontalDivider(
                color = zoneColor.copy(alpha = 0.3f),
                thickness = 2.dp
            )

            // ═══════════════════════════════════════
            // BOTTOM HALF (50%) — Stats grid + controls
            // ═══════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .background(zoneColor.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                ControlBar(
                    workoutState = progress.workoutState,
                    intervalName = progress.currentIntervalName,
                    onStart = viewModel::startWorkout,
                    onPause = viewModel::pauseWorkout,
                    onResume = viewModel::resumeWorkout,
                    onStop = viewModel::stopWorkout,
                    onBack = onNavigateBack
                )

                Spacer(Modifier.height(8.dp))

                StatsGrid(
                    threeSecPower = metrics.power,
                    targetPower = progress.targetPowerWatts,
                    intervalRemaining = progress.intervalRemainingSeconds,
                    cadence = metrics.cadence,
                    heartRate = metrics.heartRate,
                    zoneColor = zoneColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(Modifier.height(8.dp))

                ErgToggleRow(
                    ergEnabled = ergEnabled,
                    onErgChange = viewModel::setErgEnabled
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Stats grid (2×3) — the user-specified bottom-half stats
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StatsGrid(
    threeSecPower: Int,
    targetPower: Int,
    intervalRemaining: Int,
    cadence: Int,
    heartRate: Int,
    zoneColor: Color,
    modifier: Modifier = Modifier
) {
    val powerColor = when {
        targetPower == 0 -> MaterialTheme.colorScheme.primary
        kotlin.math.abs(threeSecPower - targetPower) <= targetPower * 0.10 -> Color(0xFF4CAF50)
        threeSecPower > targetPower -> Color(0xFFFF5252)
        else -> Color(0xFF2196F3)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: 3s avg power (hero) | Target power
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCell(
                label = "3s POWER",
                value = "$threeSecPower",
                unit = "W",
                color = powerColor,
                emphasized = true,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "TARGET",
                value = "$targetPower",
                unit = "W",
                color = zoneColor,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Interval remaining (full width)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f)
        ) {
            StatCell(
                label = "INTERVAL REMAINING",
                value = formatTime(intervalRemaining),
                unit = "",
                color = if (intervalRemaining <= 10) Color(0xFFFF9800)
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Row 3: Cadence | HR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCell(
                label = "CADENCE",
                value = "$cadence",
                unit = "rpm",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "HEART RATE",
                value = "$heartRate",
                unit = "bpm",
                color = Color(0xFFFF5252),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = if (emphasized) 56.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        fontSize = if (emphasized) 16.sp else 14.sp,
                        color = color.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = if (emphasized) 8.dp else 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErgToggleRow(
    ergEnabled: Boolean,
    onErgChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "ERG MODE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (ergEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (ergEnabled) "Trainer locks to target power"
                else "Free ride · timer continues",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = ergEnabled,
            onCheckedChange = onErgChange
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Control bar
// ═══════════════════════════════════════════════════════════════

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
                    text = "READY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onStart) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            WorkoutState.RUNNING -> {
                IconButton(onClick = onPause) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = intervalName.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            WorkoutState.PAUSED -> {
                IconButton(onClick = onResume) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
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

// ═══════════════════════════════════════════════════════════════
// Workout Progress Graph (top half)
// ═══════════════════════════════════════════════════════════════

@Composable
fun WorkoutProgressGraph(
    intervals: List<WorkoutInterval>,
    currentTimeSeconds: Int,
    totalDurationSeconds: Int,
    recordedPowerPoints: List<PowerDataPoint>,
    workoutState: WorkoutState,
    workoutName: String = ""
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = workoutName.ifBlank { "WORKOUT" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatTime(currentTimeSeconds)} / ${formatTime(totalDurationSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

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
            drawRect(
                color = interval.color.copy(alpha = 0.7f),
                topLeft = androidx.compose.ui.geometry.Offset(blockStart, canvasHeight - blockHeight),
                size = androidx.compose.ui.geometry.Size(blockWidth, blockHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
            cumulativeSeconds += interval.durationSeconds.toFloat()
        }

        // Actual power trace
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

        // Now-cursor
        if (workoutState == WorkoutState.RUNNING || workoutState == WorkoutState.PAUSED) {
            val posX = (currentTimeSeconds.toFloat() / totalDuration) * canvasWidth
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(posX, 0f),
                end = androidx.compose.ui.geometry.Offset(posX, canvasHeight),
                strokeWidth = 2.dp.toPx()
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

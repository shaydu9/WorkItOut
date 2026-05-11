package com.cycling.workitout.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.cycling.workitout.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.WorkoutProgress
import com.cycling.workitout.data.WorkoutState
import com.cycling.workitout.data.export.ExportState
import com.cycling.workitout.data.strava.StravaRepository
import com.cycling.workitout.ui.components.PowerDataPoint
import com.cycling.workitout.ui.components.WorkoutInterval
import com.cycling.workitout.ui.components.darken
import com.cycling.workitout.ui.components.lighten

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
    val ergRearming by viewModel.ergRearming.collectAsStateWithLifecycle()
    val displayAsPercent by viewModel.displayAsPercent.collectAsStateWithLifecycle()
    val currentFtp by viewModel.currentFtp.collectAsStateWithLifecycle()
    val intensityPercent by viewModel.intensityPercent.collectAsStateWithLifecycle()
    val startupState by viewModel.startupState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val stravaConnected by viewModel.stravaConnected.collectAsStateWithLifecycle()
    val stravaUploadState by viewModel.stravaUploadState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(progress.workoutState) {
        if (progress.workoutState == WorkoutState.COMPLETED) {
            viewModel.exportFitSilently(context)
            viewModel.saveRideToHistory()
        }
    }

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
        WorkoutScreenContent(
            progress = progress,
            metrics = metrics,
            recordedData = recordedData,
            ergEnabled = ergEnabled,
            ergRearming = ergRearming,
            displayAsPercent = displayAsPercent,
            currentFtp = currentFtp,
            intensityPercent = intensityPercent,
            onIntensityDelta = viewModel::adjustIntensity,
            startupState = startupState,
            exportState = exportState,
            stravaConnected = stravaConnected,
            stravaUploadState = stravaUploadState,
            workoutIntervals = viewModel.workoutIntervals,
            showEndDialog = showEndDialog,
            onShowEndDialog = { showEndDialog = it },
            onStart = viewModel::startWorkout,
            onPause = viewModel::pauseWorkout,
            onResume = viewModel::resumeWorkout,
            onStop = viewModel::stopWorkout,
            onBack = onNavigateBack,
            onUploadToStrava = viewModel::uploadExportedFitToStrava,
            onErgChange = viewModel::setErgEnabled,
            onDisplayChange = viewModel::setDisplayAsPercent,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun WorkoutScreenContent(
    progress: WorkoutProgress,
    metrics: com.cycling.workitout.data.LiveMetrics,
    recordedData: List<com.cycling.workitout.data.RecordedDataPoint>,
    ergEnabled: Boolean,
    ergRearming: Boolean,
    displayAsPercent: Boolean,
    currentFtp: Int,
    intensityPercent: Int,
    onIntensityDelta: (Int) -> Unit,
    startupState: WorkoutViewModel.StartupState,
    exportState: ExportState,
    stravaConnected: Boolean,
    stravaUploadState: StravaRepository.UploadState,
    workoutIntervals: List<WorkoutInterval>,
    showEndDialog: Boolean,
    onShowEndDialog: (Boolean) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onUploadToStrava: () -> Unit,
    onErgChange: (Boolean) -> Unit,
    onDisplayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoneColor = Color(progress.currentZone.colorHex)
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val isFreeRide = workoutIntervals.isEmpty() && progress.totalDurationSeconds == 0

    if (isTablet) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f)
        ) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                WorkoutProgressGraph(
                    intervals = workoutIntervals,
                    currentTimeSeconds = progress.totalElapsedSeconds,
                    totalDurationSeconds = progress.totalDurationSeconds,
                    recordedPowerPoints = recordedData.map {
                        PowerDataPoint(it.timeSeconds, it.actualPower)
                    },
                    workoutState = progress.workoutState,
                    workoutName = progress.workoutName,
                    ftpWatts = currentFtp
                )
            }

            VerticalDivider(
                color = zoneColor.copy(alpha = 0.3f),
                thickness = 2.dp
            )

            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .background(zoneColor.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                ControlBar(
                    workoutState = progress.workoutState,
                    intervalName = progress.currentIntervalName,
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onStopRequested = { onShowEndDialog(true) },
                    onBack = onBack,
                    exportState = exportState,
                    stravaConnected = stravaConnected,
                    stravaUploadState = stravaUploadState,
                    onUploadToStrava = onUploadToStrava
                )
                Spacer(Modifier.height(8.dp))
                StatsGrid(
                    threeSecPower = metrics.power,
                    targetPower = progress.targetPowerWatts * intensityPercent / 100,
                    intervalRemaining = progress.intervalRemainingSeconds,
                    cadence = metrics.cadence,
                    heartRate = metrics.heartRate,
                    zoneColor = zoneColor,
                    displayAsPercent = displayAsPercent,
                    currentFtp = currentFtp,
                    isFreeRide = isFreeRide,
                    elapsedSeconds = progress.totalElapsedSeconds,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                if (!isFreeRide) {
                    BottomControlsRow(
                        intensityPercent = intensityPercent,
                        onIntensityDelta = onIntensityDelta,
                        ergEnabled = ergEnabled,
                        ergRearming = ergRearming,
                        onErgChange = onErgChange,
                        displayAsPercent = displayAsPercent,
                        onDisplayChange = onDisplayChange
                    )
                }
            }
        }
        } // end CompositionLocalProvider
    } else {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f)
        ) {
        Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                WorkoutProgressGraph(
                    intervals = workoutIntervals,
                    currentTimeSeconds = progress.totalElapsedSeconds,
                    totalDurationSeconds = progress.totalDurationSeconds,
                    recordedPowerPoints = recordedData.map {
                        PowerDataPoint(it.timeSeconds, it.actualPower)
                    },
                    workoutState = progress.workoutState,
                    workoutName = progress.workoutName,
                    ftpWatts = currentFtp
                )
            }

            HorizontalDivider(
                color = zoneColor.copy(alpha = 0.3f),
                thickness = 2.dp
            )

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
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onStopRequested = { onShowEndDialog(true) },
                    onBack = onBack,
                    exportState = exportState,
                    stravaConnected = stravaConnected,
                    stravaUploadState = stravaUploadState,
                    onUploadToStrava = onUploadToStrava
                )

                Spacer(Modifier.height(8.dp))

                StatsGrid(
                    threeSecPower = metrics.power,
                    targetPower = progress.targetPowerWatts * intensityPercent / 100,
                    intervalRemaining = progress.intervalRemainingSeconds,
                    cadence = metrics.cadence,
                    heartRate = metrics.heartRate,
                    zoneColor = zoneColor,
                    displayAsPercent = displayAsPercent,
                    currentFtp = currentFtp,
                    isFreeRide = isFreeRide,
                    elapsedSeconds = progress.totalElapsedSeconds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(Modifier.height(8.dp))

                if (!isFreeRide) {
                    BottomControlsRow(
                        intensityPercent = intensityPercent,
                        onIntensityDelta = onIntensityDelta,
                        ergEnabled = ergEnabled,
                        ergRearming = ergRearming,
                        onErgChange = onErgChange,
                        displayAsPercent = displayAsPercent,
                        onDisplayChange = onDisplayChange
                    )
                }
            }
        }
        } // end CompositionLocalProvider
    }

    StartupOverlay(state = startupState)

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { onShowEndDialog(false) },
            title = { Text("End workout?") },
            text = {
                Text("Stopping now will end the session and save your progress. You can upload the workout to Strava after.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onShowEndDialog(false)
                        onStop()
                    }
                ) {
                    Text("End workout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowEndDialog(false) }) {
                    Text("Keep going")
                }
            }
        )
    }
}

@Composable
private fun StartupOverlay(
    state: WorkoutViewModel.StartupState,
    modifier: Modifier = Modifier
) {
    if (state is WorkoutViewModel.StartupState.Idle) return

    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        // Pin fontScale=1f so overlay text sizes are always as designed,
        // regardless of OS accessibility font scale setting.
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f)
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                WorkoutViewModel.StartupState.Waiting -> {
                    Text(
                        text = "START PEDALING",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Workout begins 5s after your first pedal stroke",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                is WorkoutViewModel.StartupState.Counting -> {
                    Text(
                        text = "${state.secondsLeft}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 220.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "GET READY",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                WorkoutViewModel.StartupState.Idle -> Unit
            }
        }
        } // end CompositionLocalProvider
    }
}

@Composable
private fun StatsGrid(
    threeSecPower: Int,
    targetPower: Int,
    intervalRemaining: Int,
    cadence: Int,
    heartRate: Int,
    zoneColor: Color,
    displayAsPercent: Boolean,
    currentFtp: Int,
    isFreeRide: Boolean = false,
    elapsedSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val percentOf = { watts: Int ->
        if (currentFtp <= 0) "--" else "${((watts.toDouble() / currentFtp) * 100).toInt()}"
    }
    val powerValueStr = if (displayAsPercent) percentOf(threeSecPower) else "$threeSecPower"
    val powerUnitStr = if (displayAsPercent) "%" else "W"
    val targetValueStr = if (displayAsPercent) percentOf(targetPower) else "$targetPower"
    val targetUnitStr = if (displayAsPercent) "%" else "W"
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
                value = powerValueStr,
                unit = powerUnitStr,
                color = powerColor,
                emphasized = true,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = if (isFreeRide) "ELAPSED" else "TARGET",
                value = if (isFreeRide) formatTime(elapsedSeconds) else targetValueStr,
                unit = if (isFreeRide) "" else targetUnitStr,
                color = zoneColor,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Interval remaining (full width). Hidden in free ride — no intervals.
        if (!isFreeRide) {
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
        // BoxWithConstraints so the value text scales with the cell's actual
        // height. On tall phones we hit the cap; on a Galaxy S9 or a split
        // foldable the short "INTERVAL REMAINING" row used to clip the 40sp
        // number — scaling prevents that without hand-tuning sizes per device.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Reserve ~30% of the cell for the label + padding; the rest for
            // the value glyph. Coerced to a sane min/max so it doesn't go tiny
            // on huge-unit cells or explode on tablets.
            val cellHeightValue = maxHeight.value
            val cap = if (emphasized) 56f else 40f
            val floor = if (emphasized) 22f else 16f
            val valueSp = (cellHeightValue * 0.42f).coerceIn(floor, cap).sp
            val unitSp = (cellHeightValue * 0.13f).coerceIn(10f, if (emphasized) 16f else 14f).sp

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = value,
                        fontSize = valueSp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            text = " $unit",
                            fontSize = unitSp,
                            color = color.copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = if (emphasized) 8.dp else 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) { content() }
    }
}

// Three separate pills: intensity, W/% display unit, ERG switch.
@Composable
private fun BottomControlsRow(
    intensityPercent: Int,
    onIntensityDelta: (Int) -> Unit,
    ergEnabled: Boolean,
    ergRearming: Boolean,
    onErgChange: (Boolean) -> Unit,
    displayAsPercent: Boolean,
    onDisplayChange: (Boolean) -> Unit
) {
    val step = WorkoutViewModel.INTENSITY_STEP
    val canDec = intensityPercent > WorkoutViewModel.MIN_INTENSITY
    val canInc = intensityPercent < WorkoutViewModel.MAX_INTENSITY
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ControlPill(modifier = Modifier.weight(1f)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { onIntensityDelta(-step) },
                        enabled = canDec,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease intensity", modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalIconButton(
                        onClick = { onIntensityDelta(step) },
                        enabled = canInc,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase intensity", modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = "$intensityPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        ControlPill(modifier = Modifier.weight(1f)) {
            com.cycling.workitout.ui.library.WattsPercentToggle(
                asPercent = displayAsPercent,
                onChange = onDisplayChange
            )
        }
        ControlPill(modifier = Modifier.weight(1f)) {
            Text(
                text = if (ergRearming) "ERG…" else "ERG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    ergRearming -> MaterialTheme.colorScheme.tertiary
                    ergEnabled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(end = 4.dp)
            )
            Switch(
                checked = ergEnabled,
                onCheckedChange = onErgChange,
                modifier = Modifier.scale(0.85f)
            )
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
    onStopRequested: () -> Unit,
    onBack: () -> Unit,
    exportState: ExportState = ExportState.Idle,
    stravaConnected: Boolean = false,
    stravaUploadState: StravaRepository.UploadState = StravaRepository.UploadState.Idle,
    onUploadToStrava: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                IconButton(onClick = onStopRequested) {
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
                IconButton(onClick = onStopRequested) {
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
                val isExporting = exportState is ExportState.InProgress
                val exportFailed = exportState is ExportState.Failed
                val isReady = exportState is ExportState.Ready
                val isUploading = stravaUploadState is StravaRepository.UploadState.Uploading
                val uploadSucceeded = stravaUploadState is StravaRepository.UploadState.Success
                val uploadFailed = stravaUploadState is StravaRepository.UploadState.Failed

                // Button is enabled only when: .fit file is ready, user is connected,
                // and we aren't already mid-upload or past a successful one.
                val enabled = isReady && stravaConnected && !isUploading && !uploadSucceeded

                Button(
                    onClick = onUploadToStrava,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uploadSucceeded) Color(0xFF4CAF50)
                        else Color(0xFFFC4C02) // Strava orange
                    )
                ) {
                    when {
                        isExporting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…", maxLines = 1)
                        }
                        exportFailed -> Text("Save failed", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        isUploading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Uploading…", maxLines = 1)
                        }
                        uploadSucceeded -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Uploaded!", maxLines = 1)
                        }
                        uploadFailed -> Text("Upload failed — retry", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        !stravaConnected -> Text("Connect Strava in Settings", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        else -> Text("Upload to Strava", maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
        // Strava brand attribution required by Strava API guidelines wherever
        // we offer to push data to Strava — only render in the post-workout state
        // since that's when the Upload-to-Strava action is shown.
        if (workoutState == WorkoutState.COMPLETED) {
            Spacer(Modifier.height(8.dp))
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
fun WorkoutProgressGraph(
    intervals: List<WorkoutInterval>,
    currentTimeSeconds: Int,
    totalDurationSeconds: Int,
    recordedPowerPoints: List<PowerDataPoint>,
    workoutState: WorkoutState,
    workoutName: String = "",
    ftpWatts: Int = 0
) {
    val density = LocalDensity.current
    // Pin font scale for graph labels — axis numbers and zone legend have intentional
    // fixed sizes as part of the data visualization design.
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f)
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
                text = if (totalDurationSeconds <= 0) formatTime(currentTimeSeconds)
                else "${formatTime(currentTimeSeconds)} / ${formatTime(totalDurationSeconds)}",
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
                workoutState = workoutState,
                ftpWatts = ftpWatts
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
    } // end CompositionLocalProvider
}

@Composable
private fun WorkoutBlocksGraph(
    intervals: List<WorkoutInterval>,
    currentTimeSeconds: Int,
    totalDurationSeconds: Int,
    recordedPowerPoints: List<PowerDataPoint>,
    workoutState: WorkoutState,
    ftpWatts: Int = 0
) {
    // Free ride: no target intervals — draw the actual-power trace alone, with a
    // fixed 0..1000W y-axis (labels every ftp/2) and color the line by power zone.
    if (intervals.isEmpty() || totalDurationSeconds == 0) {
        FreeRideTrace(currentTimeSeconds, recordedPowerPoints, workoutState, ftpWatts)
        return
    }

    // Y-axis is anchored at 2×FTP so FTP sits at the vertical midpoint. This keeps the
    // actual-power trace on-canvas when the rider scales intensity past 100% (the old
    // "stretch to max interval target" axis would push the trace off the top).
    // Falls back to the largest interval target if FTP isn't set yet.
    val maxPower = (
        if (ftpWatts > 0) (ftpWatts * 2).toFloat()
        else intervals.maxOf { it.targetPower }.toFloat()
    ).coerceAtLeast(1f)

    // Identify the in-progress interval — used to brighten it and dim the rest.
    val activeIntervalIndex: Int = remember(intervals, currentTimeSeconds, workoutState) {
        if (workoutState != WorkoutState.RUNNING && workoutState != WorkoutState.PAUSED) return@remember -1
        var acc = 0
        intervals.indexOfFirst {
            acc += it.durationSeconds
            currentTimeSeconds < acc
        }
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalDuration = totalDurationSeconds.toFloat()
        val cornerPx = 3.dp.toPx()

        var cumulativeSeconds = 0f
        intervals.forEachIndexed { i, interval ->
            val blockStart = (cumulativeSeconds / totalDuration) * canvasWidth
            val blockWidth = (interval.durationSeconds.toFloat() / totalDuration) * canvasWidth
            val blockHeight = (interval.targetPower.toFloat() / maxPower) * canvasHeight * 0.85f
            val topLeft = androidx.compose.ui.geometry.Offset(blockStart, canvasHeight - blockHeight)
            val blockSize = androidx.compose.ui.geometry.Size(blockWidth, blockHeight)

            val isActive = i == activeIntervalIndex
            // Dim non-active bars only when there IS an active one (i.e. we're riding).
            val dim = if (activeIntervalIndex < 0 || isActive) 1f else 0.40f

            val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    interval.color.lighten(0.45f).copy(alpha = 0.95f * dim),
                    interval.color.copy(alpha = 0.95f * dim),
                    interval.color.darken(0.15f).copy(alpha = 0.95f * dim),
                ),
                startY = topLeft.y,
                endY = topLeft.y + blockHeight
            )
            drawRoundRect(
                brush = gradient,
                topLeft = topLeft,
                size = blockSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
            )
            // Top-edge ribbon — a 2px highlight that gives the bar volume.
            drawLine(
                color = interval.color.lighten(0.6f).copy(alpha = 0.9f * dim),
                start = androidx.compose.ui.geometry.Offset(topLeft.x + 1f, topLeft.y + 1.5f),
                end = androidx.compose.ui.geometry.Offset(topLeft.x + blockWidth - 1f, topLeft.y + 1.5f),
                strokeWidth = 2f
            )

            if (isActive) {
                // Bright outline marks the live interval; matches the preview's selection look.
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = topLeft,
                    size = blockSize,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
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

// Free-ride graph: y-axis fixed 0..1000W with labels every ftp/2, x-axis = elapsed
// seconds (auto-extending). The trace is colored per-segment by the power zone of
// each sample so the rider sees zone transitions live.
@Composable
private fun FreeRideTrace(
    currentTimeSeconds: Int,
    recordedPowerPoints: List<PowerDataPoint>,
    workoutState: WorkoutState,
    ftpWatts: Int
) {
    val maxPower = 1000f
    val totalDuration = maxOf(currentTimeSeconds, 60).toFloat()
    // Step every ftp/2 so the FTP itself is one of the grid lines (200 → 100W steps).
    // Fall back to 100W if FTP is unset.
    val labelStep = (if (ftpWatts > 0) ftpWatts / 2 else 100).coerceAtLeast(50)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val ftpColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val density = LocalDensity.current
    // Use density-only (no font scale) so Canvas text stays at its designed size.
    val labelPx = 10f * density.density
    val leftPad = with(density) { 36.dp.toPx() }
    val topPad = with(density) { 4.dp.toPx() }
    val bottomPad = with(density) { 2.dp.toPx() }

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val plotLeft = leftPad
        val plotRight = canvasWidth
        val plotTop = topPad
        val plotBottom = canvasHeight - bottomPad
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop

        fun yFor(watts: Int): Float =
            plotBottom - ((watts.toFloat() / maxPower).coerceIn(0f, 1f) * plotH)
        fun xFor(seconds: Int): Float =
            plotLeft + (seconds.toFloat() / totalDuration) * plotW

        // Grid lines + watt labels on the left edge.
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt()
            )
            textSize = labelPx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        var watts = 0
        while (watts <= 1000) {
            val y = yFor(watts)
            val isFtp = ftpWatts > 0 && watts == ftpWatts
            drawLine(
                color = if (isFtp) ftpColor else gridColor,
                start = androidx.compose.ui.geometry.Offset(plotLeft, y),
                end = androidx.compose.ui.geometry.Offset(plotRight, y),
                strokeWidth = if (isFtp) 1.5.dp.toPx() else 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                watts.toString(),
                plotLeft - 4.dp.toPx(),
                y + labelPx / 3f,
                paint
            )
            watts += labelStep
        }

        // Zone-colored trace: each segment uses the zone color of its starting sample.
        if (recordedPowerPoints.size >= 2 && ftpWatts > 0) {
            val strokePx = 2.dp.toPx()
            for (i in 1 until recordedPowerPoints.size) {
                val a = recordedPowerPoints[i - 1]
                val b = recordedPowerPoints[i]
                val zone = com.cycling.workitout.data.PowerZone.fromAbsoluteWatts(a.power, ftpWatts)
                drawLine(
                    color = Color(zone.colorHex),
                    start = androidx.compose.ui.geometry.Offset(xFor(a.timeSeconds), yFor(a.power)),
                    end = androidx.compose.ui.geometry.Offset(xFor(b.timeSeconds), yFor(b.power)),
                    strokeWidth = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        } else if (recordedPowerPoints.size >= 2) {
            // No FTP yet — fall back to a single-color trace.
            for (i in 1 until recordedPowerPoints.size) {
                val a = recordedPowerPoints[i - 1]
                val b = recordedPowerPoints[i]
                drawLine(
                    color = Color(0xFF00BCD4),
                    start = androidx.compose.ui.geometry.Offset(xFor(a.timeSeconds), yFor(a.power)),
                    end = androidx.compose.ui.geometry.Offset(xFor(b.timeSeconds), yFor(b.power)),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        if (workoutState == WorkoutState.RUNNING || workoutState == WorkoutState.PAUSED) {
            val posX = xFor(currentTimeSeconds)
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(posX, plotTop),
                end = androidx.compose.ui.geometry.Offset(posX, plotBottom),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

package com.cycling.workitout.ui.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.scaledByIntensity
import com.cycling.workitout.ui.components.DevicePairingDialog
import com.cycling.workitout.ui.components.WorkoutPreviewChart
import com.cycling.workitout.ui.components.WorkoutRecoveryDialog
import com.cycling.workitout.ui.components.rememberBlePermissionState
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToInt

private val DURATION_OPTIONS = listOf(30, 45, 60, 75, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartWorkout: (WorkoutDefinition) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenLibrary: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ftp by viewModel.ftp.collectAsStateWithLifecycle()
    val trainerConnected by viewModel.isTrainerConnected.collectAsStateWithLifecycle()
    val hrConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val cadenceSensorConnected by viewModel.isCadenceSensorConnected.collectAsStateWithLifecycle()
    val trainerProvidesCadence by viewModel.trainerProvidesCadence.collectAsStateWithLifecycle()
    val displayAsPercent by viewModel.displayAsPercent.collectAsStateWithLifecycle()
    // Dialog State
    var pairingDialogDeviceType by remember { mutableStateOf<DeviceType?>(null) }
    val withBlePermission = rememberBlePermissionState()
    LaunchedEffect(Unit) {
        withBlePermission {
            viewModel.reconnectSavedDevices()
        }
    }

    val onTrainerTap = { withBlePermission { pairingDialogDeviceType = DeviceType.SMART_TRAINER } }
    val onCadenceTap = { withBlePermission { pairingDialogDeviceType = DeviceType.CADENCE_SENSOR } }
    val onHrTap = { withBlePermission { pairingDialogDeviceType = DeviceType.HEART_RATE_MONITOR } }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Today's Workout", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(
                            Icons.Default.CollectionsBookmark,
                            contentDescription = "Workout library"
                        )
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "Ride history")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        HomeScreenContent(
            state = state,
            ftp = ftp,
            trainerConnected = trainerConnected,
            cadenceConnected = cadenceSensorConnected || trainerProvidesCadence,
            hrConnected = hrConnected,
            displayAsPercent = displayAsPercent,
            onSetDuration = viewModel::setDuration,
            onSetDifficulty = viewModel::setDifficulty,
            onOpenCustomPrompt = viewModel::openCustomPrompt,
            onCloseCustomPrompt = viewModel::closeCustomPrompt,
            onSetCustomPromptText = viewModel::setCustomPromptText,
            onGenerateCustomWorkout = viewModel::generateCustomWorkout,
            onGenerateWorkout = viewModel::generateWorkout,
            onSetDisplayAsPercent = viewModel::setDisplayAsPercent,
            onSaveWorkoutToLibrary = viewModel::saveWorkoutToLibrary,
            onDismissPreview = viewModel::dismissPreview,
            onAdjustIntensity = viewModel::adjustIntensity,
            onRegenerate = viewModel::regenerateWorkout,
            onStartWorkout = onStartWorkout,
            onTrainerTap = onTrainerTap,
            onCadenceTap = onCadenceTap,
            onHrTap = onHrTap,
            modifier = Modifier.padding(padding)
        )

        pairingDialogDeviceType?.let { deviceType ->
            val devices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
            val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

            LaunchedEffect(deviceType) { viewModel.startScan() }

            DevicePairingDialog(
                deviceType = deviceType,
                devices = devices.filter { it.deviceType == deviceType },
                isScanning = isScanning,
                onConnect = viewModel::connectDevice,
                onDismiss = {
                    viewModel.stopScan()
                    pairingDialogDeviceType = null
                }
            )

            state.recoveryCheckpoint?.let { checkpoint ->
                WorkoutRecoveryDialog(
                    checkpoint = checkpoint,
                    onSave = viewModel::saveRecoverRide,
                    onDiscard = viewModel::dismissRecovery
                )
            }
        }
    }
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    ftp: Int,
    trainerConnected: Boolean,
    cadenceConnected: Boolean,
    hrConnected: Boolean,
    displayAsPercent: Boolean,
    onSetDuration: (Int) -> Unit,
    onSetDifficulty: (Difficulty) -> Unit,
    onOpenCustomPrompt: () -> Unit,
    onCloseCustomPrompt: () -> Unit,
    onSetCustomPromptText: (String) -> Unit,
    onGenerateCustomWorkout: () -> Unit,
    onGenerateWorkout: () -> Unit,
    onSetDisplayAsPercent: (Boolean) -> Unit,
    onSaveWorkoutToLibrary: () -> Unit,
    onDismissPreview: () -> Unit,
    onAdjustIntensity: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onStartWorkout: (WorkoutDefinition) -> Unit,
    onTrainerTap: () -> Unit,
    onCadenceTap: () -> Unit,
    onHrTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = (if (isTablet) Modifier.widthIn(max = 640.dp) else Modifier)
            .fillMaxSize()
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusChip(
                trainerConnected = trainerConnected,
                cadenceConnected = cadenceConnected,
                hrConnected = hrConnected,
                onTrainerTap = onTrainerTap,
                onCadenceTap = onCadenceTap,
                onHrTap = onHrTap
            )

            Text("FTP: ${ftp}W", style = MaterialTheme.typography.labelLarge)

            Text(
                "Duration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DURATION_OPTIONS.forEach { mins ->
                    FilterChip(
                        selected = state.durationMinutes == mins,
                        onClick = { onSetDuration(mins) },
                        label = { Text("${mins}m") }
                    )
                }
            }

            Text(
                "Difficulty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Difficulty.values().forEach { diff ->
                    FilterChip(
                        selected = state.difficulty == diff,
                        onClick = { onSetDifficulty(diff) },
                        label = { Text(diff.label) }
                    )
                }
            }
        }

        // Sticky bottom action buttons — always reachable
        HorizontalDivider()
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenCustomPrompt,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Describe your own workout", fontWeight = FontWeight.Medium)
            }

            OutlinedButton(
                onClick = {
                    onStartWorkout(
                        WorkoutDefinition(
                            id = "free-ride",
                            name = "Free Ride",
                            description = "",
                            intervals = emptyList(),
                            totalDurationSeconds = 0,
                            isFreeRide = true
                        )
                    )
                },
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Free ride", fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onGenerateWorkout,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Designing your workout…")
                } else {
                    Text("Generate Workout", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    } // end Box

    if (state.customPromptOpen) {
        CustomPromptDialog(
            text = state.customPromptText,
            onTextChange = onSetCustomPromptText,
            onDismiss = onCloseCustomPrompt,
            onSubmit = onGenerateCustomWorkout
        )
    }

    if (state.preview != null) {
        WorkoutPreviewSheet(
            workout = state.preview,
            intensityScale = state.intensityScale,
            isGenerating = state.isGenerating,
            displayAsPercent = displayAsPercent,
            onAdjustIntensity = onAdjustIntensity,
            onRegenerate = onRegenerate,
            onSave = onSaveWorkoutToLibrary,
            onStart = {
                // Apply intensity scale before handing off — the trainer/UI downstream
                // gets a self-contained workout with the user's adjustments baked in.
                val scaled = state.preview.scaledByIntensity(state.intensityScale)
                onDismissPreview()
                onStartWorkout(scaled)
            },
            onDismiss = onDismissPreview,
        )
    }
}

@Composable
private fun CustomPromptDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onTextChange(if (text.isBlank()) spoken else "$text $spoken")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Describe your workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tell me what you want to ride. Examples: \"45 minute sweet spot with 3 long efforts\" or \"hard hilly simulation, 1 hour\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("e.g. 60-minute threshold workout with 4x8min intervals") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your workout")
                            }
                            try {
                                voiceLauncher.launch(intent)
                            } catch (_: Exception) {
                                // No speech recognizer installed — silently ignore
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice input")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutPreviewSheet(
    workout: WorkoutDefinition,
    intensityScale: Float,
    isGenerating: Boolean,
    displayAsPercent: Boolean,
    onAdjustIntensity: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Local "saved" flag — flips to filled-heart on first tap. Resets implicitly when
    // a new workout is generated because the sheet is keyed on `workout.id`.
    var saved by remember(workout.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // Show intervals with the user's intensity adjustment baked in so the chart
        // reflects exactly what will be ridden.
        val displayWorkout = remember(workout, intensityScale) {
            workout.scaledByIntensity(intensityScale)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header — name on the left, regenerate button on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (!saved) {
                            onSave()
                            saved = true
                        }
                    },
                    enabled = !isGenerating,
                ) {
                    Icon(
                        if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (saved) "Saved to library" else "Save to library",
                        tint = if (saved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRegenerate,
                    enabled = !isGenerating,
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Cached,
                            contentDescription = "Regenerate workout",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Interactive bar chart — the centerpiece. Tap/drag to scrub.
            WorkoutPreviewChart(
                intervals = displayWorkout.intervals,
                displayAsPercent = displayAsPercent,
                modifier = Modifier.fillMaxWidth(),
            )

            // Description — capped to 3 lines so the sheet stays no-scroll.
            if (workout.description.isNotBlank()) {
                Text(
                    workout.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Intensity pill — −/+ steps of 5%, clamped 70%–130%.
            IntensityPill(
                scale = intensityScale,
                onAdjust = onAdjustIntensity,
            )

            // Sticky action row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Discard") }
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun IntensityPill(
    scale: Float,
    onAdjust: (Int) -> Unit,
) {
    val percent = (scale * 100f).roundToInt()
    val accent = when {
        percent > 100 -> MaterialTheme.colorScheme.tertiary
        percent < 100 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Intensity",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAdjust(-5) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease intensity")
                }
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onAdjust(5) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase intensity")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusChip(
    trainerConnected: Boolean,
    cadenceConnected: Boolean,
    hrConnected: Boolean,
    onTrainerTap: () -> Unit,
    onCadenceTap: () -> Unit,
    onHrTap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeviceStatusPill(
            connected = trainerConnected,
            icon = Icons.Default.FitnessCenter,
            label = "Trainer",
            onClick = onTrainerTap,
            modifier = Modifier.weight(1f)
        )
        DeviceStatusPill(
            connected = cadenceConnected,
            icon = Icons.Default.Cached,
            label = "Cadence",
            onClick = onCadenceTap,
            modifier = Modifier.weight(1f)
        )
        DeviceStatusPill(
            connected = hrConnected,
            icon = if (hrConnected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = "HR",
            onClick = onHrTap,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeviceStatusPill(
    connected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg =
        if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg =
        if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        onClick = onClick,
        modifier = modifier
    ) {
        // Icon-only: color (purple/red) + icon (barbell/sync/heart) convey state without text
        // that breaks at large OS font sizes inside a narrow fixed-width pill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = "$label — ${if (connected) "connected" else "not connected"}",
                tint = fg,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

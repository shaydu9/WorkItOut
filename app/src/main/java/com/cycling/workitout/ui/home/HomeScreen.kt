package com.cycling.workitout.ui.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import java.util.Locale

private val DURATION_OPTIONS = listOf(30, 45, 60, 75, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartWorkout: (WorkoutDefinition) -> Unit,
    onOpenSettings: () -> Unit,
    onRepairDevices: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ftp by viewModel.ftp.collectAsStateWithLifecycle()
    val trainerConnected by viewModel.isTrainerConnected.collectAsStateWithLifecycle()
    val hrConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()

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
                title = { Text("Today's Workout") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusChip(
                trainerConnected = trainerConnected || isDemoMode,
                hrConnected = hrConnected || isDemoMode,
                onRepair = onRepairDevices
            )

            Text("FTP: ${ftp}W", style = MaterialTheme.typography.labelLarge)

            Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DURATION_OPTIONS.forEach { mins ->
                    FilterChip(
                        selected = state.durationMinutes == mins,
                        onClick = { viewModel.setDuration(mins) },
                        label = { Text("${mins}m") }
                    )
                }
            }

            Text("Difficulty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Difficulty.values().forEach { diff ->
                    FilterChip(
                        selected = state.difficulty == diff,
                        onClick = { viewModel.setDifficulty(diff) },
                        label = { Text(diff.label) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { viewModel.openCustomPrompt() },
                enabled = !state.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Describe your own workout", fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = { viewModel.generateWorkout() },
                enabled = !state.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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

        if (state.customPromptOpen) {
            CustomPromptDialog(
                text = state.customPromptText,
                onTextChange = { viewModel.setCustomPromptText(it) },
                onDismiss = { viewModel.closeCustomPrompt() },
                onSubmit = { viewModel.generateCustomWorkout() }
            )
        }

        if (state.preview != null) {
            WorkoutPreviewSheet(
                workout = state.preview!!,
                onStart = {
                    val w = state.preview!!
                    viewModel.dismissPreview()
                    onStartWorkout(w)
                },
                onDismiss = { viewModel.dismissPreview() }
            )
        }
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
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(workout.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val totalMin = workout.totalDurationSeconds / 60
            Text(
                "${totalMin} min · ${workout.intervals.size} intervals",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (workout.description.isNotBlank()) {
                Text(workout.description, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(workout.intervals) { interval ->
                    IntervalRow(interval)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Discard") }
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f)
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
private fun IntervalRow(interval: WorkoutIntervalDef) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(interval.zone.colorHex).copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 28.dp)
                .background(Color(interval.zone.colorHex), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(interval.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(
                interval.zone.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val mins = interval.durationSeconds / 60
        val secs = interval.durationSeconds % 60
        Text(
            "%d:%02d".format(mins, secs),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            "${interval.targetPowerWatts}W",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ConnectionStatusChip(
    trainerConnected: Boolean,
    hrConnected: Boolean,
    onRepair: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeviceStatusPill(
            connected = trainerConnected,
            icon = Icons.Default.FitnessCenter,
            connectedLabel = "Trainer",
            disconnectedLabel = "Trainer",
            onClick = onRepair,
            modifier = Modifier.weight(1f)
        )
        DeviceStatusPill(
            connected = hrConnected,
            icon = if (hrConnected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            connectedLabel = "HR",
            disconnectedLabel = "HR",
            onClick = onRepair,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeviceStatusPill(
    connected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    connectedLabel: String,
    disconnectedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val label = if (connected) "$connectedLabel connected" else "$disconnectedLabel — tap"
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

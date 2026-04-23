package com.cycling.workitout.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = SettingsViewModel(),
    onNavigateBack: () -> Unit,
    onRepairDevices: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val powerSmoothingSeconds by viewModel.powerSmoothingSeconds.collectAsStateWithLifecycle()
    val ftp by viewModel.ftp.collectAsStateWithLifecycle()
    val weightKg by viewModel.weightKg.collectAsStateWithLifecycle()
    val maxHeartRate by viewModel.maxHeartRate.collectAsStateWithLifecycle()
    val stravaConnected by viewModel.stravaConnected.collectAsStateWithLifecycle()
    val stravaAthleteName by viewModel.stravaAthleteName.collectAsStateWithLifecycle()
    val autoUploadToStrava by viewModel.autoUploadToStravaOnFinish.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPowerSmoothingDialog by remember { mutableStateOf(false) }
    var showFtpDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showMaxHrDialog by remember { mutableStateOf(false) }
    var showDisconnectStravaDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader("Training")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.FitnessCenter,
                    title = "FTP",
                    subtitle = "${ftp}W",
                    onClick = { showFtpDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.MonitorWeight,
                    title = "Weight",
                    subtitle = "${weightKg} kg",
                    onClick = { showWeightDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Favorite,
                    title = "Max Heart Rate",
                    subtitle = "${maxHeartRate} bpm",
                    onClick = { showMaxHrDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.ShowChart,
                    title = "Power Averaging",
                    subtitle = "${powerSmoothingSeconds}s average",
                    onClick = { showPowerSmoothingDialog = true }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Devices")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Bluetooth,
                    title = "Re-pair devices",
                    subtitle = "Run the pairing flow again",
                    onClick = {
                        viewModel.resetFirstRun()
                        onRepairDevices()
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Integrations")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.CloudUpload,
                    title = "Strava",
                    subtitle = if (stravaConnected) {
                        "Connected${stravaAthleteName?.let { " as $it" } ?: ""}"
                    } else {
                        "Not connected — tap to sign in"
                    },
                    iconTint = Color(0xFFFC4C02), // Strava orange
                    onClick = {
                        if (stravaConnected) showDisconnectStravaDialog = true
                        else viewModel.connectStrava(context)
                    }
                )
            }
            if (stravaConnected) {
                item {
                    SettingsToggleItem(
                        icon = Icons.Default.CloudSync,
                        title = "Auto-upload to Strava",
                        subtitle = "Send rides to Strava as soon as you finish",
                        iconTint = Color(0xFFFC4C02),
                        checked = autoUploadToStrava,
                        onCheckedChange = { viewModel.setAutoUploadToStravaOnFinish(it) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Display")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Brightness4,
                    title = "Theme",
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System default"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("About")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "WorkItOut",
                    subtitle = "Version 1.0.0",
                    onClick = { }
                )
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = themeMode,
                onDismiss = { showThemeDialog = false },
                onThemeSelected = { mode ->
                    viewModel.setThemeMode(mode)
                    showThemeDialog = false
                }
            )
        }

        if (showPowerSmoothingDialog) {
            PowerSmoothingDialog(
                currentSeconds = powerSmoothingSeconds,
                onDismiss = { showPowerSmoothingDialog = false },
                onSecondsSelected = { seconds ->
                    viewModel.setPowerSmoothingSeconds(seconds)
                    showPowerSmoothingDialog = false
                }
            )
        }

        if (showFtpDialog) {
            FtpDialog(
                currentFtp = ftp,
                onDismiss = { showFtpDialog = false },
                onFtpSelected = { watts ->
                    viewModel.setFtp(watts)
                    showFtpDialog = false
                }
            )
        }

        if (showWeightDialog) {
            NumericValueDialog(
                title = "Body weight",
                description = "Used with your power output to compute virtual speed and distance for Strava.",
                currentValue = weightKg,
                unit = "kg",
                range = 30..200,
                onDismiss = { showWeightDialog = false },
                onValueSelected = { kg ->
                    viewModel.setWeightKg(kg)
                    showWeightDialog = false
                }
            )
        }

        if (showMaxHrDialog) {
            NumericValueDialog(
                title = "Max Heart Rate",
                description = "Your maximum heart rate. Used to compute %HRmax training zones.",
                currentValue = maxHeartRate,
                unit = "bpm",
                range = 120..230,
                onDismiss = { showMaxHrDialog = false },
                onValueSelected = { bpm ->
                    viewModel.setMaxHeartRate(bpm)
                    showMaxHrDialog = false
                }
            )
        }

        if (showDisconnectStravaDialog) {
            AlertDialog(
                onDismissRequest = { showDisconnectStravaDialog = false },
                title = { Text("Disconnect Strava?") },
                text = {
                    Text("You'll need to sign in again to upload future workouts. Previously uploaded rides stay on Strava.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.disconnectStrava()
                            showDisconnectStravaDialog = false
                        }
                    ) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectStravaDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column {
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == mode,
                            onClick = { onThemeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.SYSTEM -> "System default"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun PowerSmoothingDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSecondsSelected: (Int) -> Unit
) {
    val intervals = listOf(1, 3, 5, 10, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Power Averaging") },
        text = {
            Column {
                intervals.forEach { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSeconds == seconds,
                            onClick = { onSecondsSelected(seconds) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${seconds}s average", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NumericValueDialog(
    title: String,
    description: String,
    currentValue: Int,
    unit: String,
    range: IntRange,
    onDismiss: () -> Unit,
    onValueSelected: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("$title ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (!valid) Text("Enter a value between ${range.first} and ${range.last}")
                    },
                    isError = !valid
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onValueSelected) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FtpDialog(
    currentFtp: Int,
    onDismiss: () -> Unit,
    onFtpSelected: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentFtp.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 50..600

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Functional Threshold Power") },
        text = {
            Column {
                Text(
                    "Your sustainable 1-hour power output. Used to scale AI-generated workouts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("FTP (watts)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (!valid) Text("Enter a value between 50 and 600")
                    },
                    isError = !valid
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onFtpSelected) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

package com.cycling.workitout.ui.firstrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.ui.components.rememberBlePermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunPairingScreen(
    viewModel: FirstRunPairingViewModel,
    onPairingComplete: () -> Unit
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val devices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val trainerConnected by viewModel.isTrainerConnected.collectAsStateWithLifecycle()
    val cadenceSensorConnected by viewModel.isCadenceSensorConnected.collectAsStateWithLifecycle()
    val hrConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val ftp by viewModel.ftp.collectAsStateWithLifecycle()
    val weightKg by viewModel.weightKg.collectAsStateWithLifecycle()
    val maxHeartRate by viewModel.maxHeartRate.collectAsStateWithLifecycle()

    var permissionDenied by remember { mutableStateOf(false) }
    val runWithBlePermissions = rememberBlePermissionState(onDenied = { permissionDenied = true })

    LaunchedEffect(trainerConnected, step) {
        if (step == PairingStep.TRAINER && trainerConnected) viewModel.stopScan()
    }
    LaunchedEffect(cadenceSensorConnected, step) {
        if (step == PairingStep.CADENCE && cadenceSensorConnected) viewModel.stopScan()
    }
    LaunchedEffect(hrConnected, step) {
        if (step == PairingStep.HEART_RATE && hrConnected) viewModel.stopScan()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Get Started") }) }
    ) { padding ->
        FirstRunPairingScreenContent(
            step = step,
            devices = devices,
            isScanning = isScanning,
            trainerConnected = trainerConnected,
            cadenceSensorConnected = cadenceSensorConnected,
            hrConnected = hrConnected,
            ftp = ftp,
            weightKg = weightKg,
            maxHeartRate = maxHeartRate,
            permissionDenied = permissionDenied,
            onScan = { runWithBlePermissions { viewModel.startScan() } },
            onStopScan = viewModel::stopScan,
            onDeviceClick = { device -> runWithBlePermissions { viewModel.connectDevice(device) } },
            onNextStep = viewModel::nextStep,
            onFtpChange = viewModel::setFtp,
            onWeightChange = viewModel::setWeightKg,
            onMaxHrChange = viewModel::setMaxHeartRate,
            onFinish = { viewModel.completeFirstRun(onPairingComplete) },
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun FirstRunPairingScreenContent(
    step: PairingStep,
    devices: List<BleDevice>,
    isScanning: Boolean,
    trainerConnected: Boolean,
    cadenceSensorConnected: Boolean,
    hrConnected: Boolean,
    ftp: Int,
    weightKg: Int,
    maxHeartRate: Int,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (BleDevice) -> Unit,
    onNextStep: () -> Unit,
    onFtpChange: (Int) -> Unit,
    onWeightChange: (Int) -> Unit,
    onMaxHrChange: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val connected = when (step) {
        PairingStep.TRAINER -> trainerConnected
        PairingStep.CADENCE -> cadenceSensorConnected
        PairingStep.HEART_RATE -> hrConnected
        else -> false
    }
    val profileValid = ftp in 50..600 && weightKg in 30..200 && maxHeartRate in 120..230

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = (if (isTablet) Modifier.widthIn(max = 560.dp) else Modifier)
                .fillMaxSize()
                .imePadding()
        ) {
            // Step indicator — always visible above the scroll
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                StepIndicator(currentStep = step)
            }

            // Scrollable body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                val stepLabel = when (step) {
                    PairingStep.TRAINER -> "Trainer · Step 1 of 5"
                    PairingStep.CADENCE -> "Cadence · Step 2 of 5"
                    PairingStep.HEART_RATE -> "Heart Rate · Step 3 of 5"
                    PairingStep.PROFILE -> "Your Profile · Step 4 of 5"
                    PairingStep.READY -> "All Set · Step 5 of 5"
                }
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                when (step) {
                    PairingStep.TRAINER -> PairingStepBody(
                        title = "Pair your Smart Trainer",
                        subtitle = "We'll use the trainer for power, cadence and ERG control. You can skip this if you don't have one yet.",
                        connected = trainerConnected,
                        deviceTypeFilter = DeviceType.SMART_TRAINER,
                        devices = devices,
                        isScanning = isScanning,
                        permissionDenied = permissionDenied,
                        onScan = onScan,
                        onStopScan = onStopScan,
                        onDeviceClick = onDeviceClick
                    )
                    PairingStep.CADENCE -> PairingStepBody(
                        title = "Pair a Cadence Sensor",
                        subtitle = "Your trainer doesn't seem to broadcast cadence. Pair a separate BLE cadence sensor for accurate RPM, or skip and we'll go without.",
                        connected = cadenceSensorConnected,
                        deviceTypeFilter = DeviceType.CADENCE_SENSOR,
                        devices = devices,
                        isScanning = isScanning,
                        permissionDenied = permissionDenied,
                        onScan = onScan,
                        onStopScan = onStopScan,
                        onDeviceClick = onDeviceClick
                    )
                    PairingStep.HEART_RATE -> PairingStepBody(
                        title = "Pair your Heart Rate Monitor",
                        subtitle = "Optional, but recommended for accurate training.",
                        connected = hrConnected,
                        deviceTypeFilter = DeviceType.HEART_RATE_MONITOR,
                        devices = devices,
                        isScanning = isScanning,
                        permissionDenied = permissionDenied,
                        onScan = onScan,
                        onStopScan = onStopScan,
                        onDeviceClick = onDeviceClick
                    )
                    PairingStep.PROFILE -> ProfileStepBody(
                        ftp = ftp,
                        weightKg = weightKg,
                        maxHeartRate = maxHeartRate,
                        onFtpChange = onFtpChange,
                        onWeightChange = onWeightChange,
                        onMaxHrChange = onMaxHrChange
                    )
                    PairingStep.READY -> ReadyStepBody()
                }
                Spacer(Modifier.height(16.dp))
            }

            // Sticky bottom bar — always reachable regardless of font size
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (step) {
                    PairingStep.TRAINER, PairingStep.CADENCE, PairingStep.HEART_RATE -> {
                        if (connected) {
                            Button(onClick = onNextStep, modifier = Modifier.fillMaxWidth()) {
                                Text("Continue")
                            }
                        } else {
                            Button(
                                onClick = { if (isScanning) onStopScan() else onScan() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isScanning) "Stop scan" else "Scan for devices")
                            }
                            TextButton(onClick = onNextStep, modifier = Modifier.fillMaxWidth()) {
                                Text("Skip for now")
                            }
                        }
                    }
                    PairingStep.PROFILE -> {
                        Button(
                            onClick = onNextStep,
                            enabled = profileValid,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue")
                        }
                    }
                    PairingStep.READY -> {
                        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                            Text("Let's go")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: PairingStep) {
    data class StepEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val step: PairingStep)
    val steps = listOf(
        StepEntry(Icons.Default.FitnessCenter, "Trainer", PairingStep.TRAINER),
        StepEntry(Icons.Default.Cached, "Cadence", PairingStep.CADENCE),
        StepEntry(Icons.Default.FavoriteBorder, "HR", PairingStep.HEART_RATE),
        StepEntry(Icons.Default.Person, "You", PairingStep.PROFILE),
        StepEntry(Icons.Default.Check, "Done", PairingStep.READY)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { idx, entry ->
            val icon = entry.icon
            val label = entry.label
            val active = currentStep.ordinal >= idx
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Body-only: informational content + device list. Action buttons live in the sticky bar.
@Composable
private fun PairingStepBody(
    title: String,
    subtitle: String,
    connected: Boolean,
    deviceTypeFilter: DeviceType,
    devices: List<BleDevice>,
    isScanning: Boolean,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (BleDevice) -> Unit
) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))

    if (connected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Connected", fontWeight = FontWeight.Medium)
        }
    } else {
        if (permissionDenied) {
            Text(
                "Bluetooth permission is required to scan for sensors. Grant it in Settings → Apps → WorkItOut → Permissions, then tap Scan again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }
        val filtered = devices.filter { it.deviceType == deviceTypeFilter }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filtered.forEach { device ->
                DeviceRow(device = device, onClick = { onDeviceClick(device) })
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (device.deviceType) {
                DeviceType.SMART_TRAINER -> Icons.Default.FitnessCenter
                DeviceType.HEART_RATE_MONITOR -> Icons.Default.FavoriteBorder
                DeviceType.CADENCE_SENSOR -> Icons.Default.Cached
                else -> Icons.Default.Bluetooth
            }
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Medium)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${device.rssi} dB", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// Body-only — no Continue button
@Composable
private fun ProfileStepBody(
    ftp: Int,
    weightKg: Int,
    maxHeartRate: Int,
    onFtpChange: (Int) -> Unit,
    onWeightChange: (Int) -> Unit,
    onMaxHrChange: (Int) -> Unit
) {
    Text("Tell us about you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "FTP scales AI-generated workouts. Weight powers Strava's virtual speed + distance. Max HR unlocks heart-rate training zones. You can edit these anytime in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    NumericField(value = ftp, label = "FTP (watts)", onChange = onFtpChange, maxDigits = 3)
    Spacer(Modifier.height(12.dp))
    NumericField(value = weightKg, label = "Body weight (kg)", onChange = onWeightChange, maxDigits = 3)
    Spacer(Modifier.height(12.dp))
    NumericField(value = maxHeartRate, label = "Max heart rate (bpm)", onChange = onMaxHrChange, maxDigits = 3)
}

@Composable
private fun NumericField(
    value: Int,
    label: String,
    onChange: (Int) -> Unit,
    maxDigits: Int
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() }.take(maxDigits)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

// Body-only — no "Let's go" button
@Composable
private fun ReadyStepBody() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("You're all set!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Next, pick a workout on the home screen and let's ride.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

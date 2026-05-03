package com.cycling.workitout.ui.firstrun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType

// API 31+ wants BLUETOOTH_SCAN/CONNECT; older Androids piggyback on FINE_LOCATION for scans.
private val REQUIRED_BLE_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

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
    val hrConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val ftp by viewModel.ftp.collectAsStateWithLifecycle()
    val weightKg by viewModel.weightKg.collectAsStateWithLifecycle()
    val maxHeartRate by viewModel.maxHeartRate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    // Lets one launcher serve both scan buttons — stash the action and run it once permissions land.
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = REQUIRED_BLE_PERMISSIONS.all { result[it] == true }
        if (allGranted) {
            permissionDenied = false
            pendingPermissionAction?.invoke()
        } else {
            permissionDenied = true
        }
        pendingPermissionAction = null
    }

    // Run the action immediately if permissions are already held, otherwise prompt first.
    val runWithBlePermissions: (() -> Unit) -> Unit = { action ->
        val allGranted = REQUIRED_BLE_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(REQUIRED_BLE_PERMISSIONS)
        }
    }

    // Auto-advance when the currently-pairing device connects.
    LaunchedEffect(trainerConnected, step) {
        if (step == PairingStep.TRAINER && trainerConnected) {
            viewModel.stopScan()
        }
    }
    LaunchedEffect(hrConnected, step) {
        if (step == PairingStep.HEART_RATE && hrConnected) {
            viewModel.stopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Get Started") })
        }
    ) { padding ->
        FirstRunPairingScreenContent(
            step = step,
            devices = devices,
            isScanning = isScanning,
            trainerConnected = trainerConnected,
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        StepIndicator(currentStep = step)
        Spacer(Modifier.height(24.dp))

        when (step) {
            PairingStep.TRAINER -> PairingStepContent(
                title = "Pair your Smart Trainer",
                subtitle = "We'll use the trainer for power, cadence and ERG control. You can skip this if you don't have one yet.",
                connected = trainerConnected,
                deviceTypeFilter = DeviceType.SMART_TRAINER,
                devices = devices,
                isScanning = isScanning,
                permissionDenied = permissionDenied,
                onScan = onScan,
                onStopScan = onStopScan,
                onDeviceClick = onDeviceClick,
                onNext = onNextStep,
                allowSkip = true
            )

            PairingStep.HEART_RATE -> PairingStepContent(
                title = "Pair your Heart Rate Monitor",
                subtitle = "Optional, but recommended for accurate training.",
                connected = hrConnected,
                deviceTypeFilter = DeviceType.HEART_RATE_MONITOR,
                devices = devices,
                isScanning = isScanning,
                permissionDenied = permissionDenied,
                onScan = onScan,
                onStopScan = onStopScan,
                onDeviceClick = onDeviceClick,
                onNext = onNextStep,
                allowSkip = true
            )

            PairingStep.PROFILE -> ProfileStepContent(
                ftp = ftp,
                weightKg = weightKg,
                maxHeartRate = maxHeartRate,
                onFtpChange = onFtpChange,
                onWeightChange = onWeightChange,
                onMaxHrChange = onMaxHrChange,
                onNext = onNextStep
            )

            PairingStep.READY -> ReadyStepContent(onFinish = onFinish)
        }
    }
}

@Composable
private fun StepIndicator(currentStep: PairingStep) {
    val steps = listOf(
        "Trainer" to PairingStep.TRAINER,
        "HR" to PairingStep.HEART_RATE,
        "You" to PairingStep.PROFILE,
        "Done" to PairingStep.READY
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { idx, (label, s) ->
            val active = currentStep.ordinal >= idx
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${idx + 1}", color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PairingStepContent(
    title: String,
    subtitle: String,
    connected: Boolean,
    deviceTypeFilter: DeviceType,
    devices: List<BleDevice>,
    isScanning: Boolean,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (BleDevice) -> Unit,
    onNext: () -> Unit,
    allowSkip: Boolean = false
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
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
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
        if (permissionDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Bluetooth permission is required to scan for sensors. Grant it in Settings → Apps → WorkItOut → Permissions, then tap Scan again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(16.dp))

        val filtered = devices.filter { it.deviceType == deviceTypeFilter }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.address }) { device ->
                DeviceRow(device = device, onClick = { onDeviceClick(device) })
            }
        }

        if (allowSkip) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
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

@Composable
private fun ProfileStepContent(
    ftp: Int,
    weightKg: Int,
    maxHeartRate: Int,
    onFtpChange: (Int) -> Unit,
    onWeightChange: (Int) -> Unit,
    onMaxHrChange: (Int) -> Unit,
    onNext: () -> Unit
) {
    Text("Tell us about you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "FTP scales AI-generated workouts. Weight powers Strava's virtual speed + distance. Max HR unlocks heart-rate training zones. You can edit these anytime in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    NumericField(
        value = ftp,
        label = "FTP (watts)",
        onChange = onFtpChange,
        maxDigits = 3
    )
    Spacer(Modifier.height(12.dp))
    NumericField(
        value = weightKg,
        label = "Body weight (kg)",
        onChange = onWeightChange,
        maxDigits = 3
    )
    Spacer(Modifier.height(12.dp))
    NumericField(
        value = maxHeartRate,
        label = "Max heart rate (bpm)",
        onChange = onMaxHrChange,
        maxDigits = 3
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onNext,
        enabled = ftp in 50..600 && weightKg in 30..200 && maxHeartRate in 120..230,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
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

@Composable
private fun ReadyStepContent(onFinish: () -> Unit) {
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
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Let's go")
        }
    }
}

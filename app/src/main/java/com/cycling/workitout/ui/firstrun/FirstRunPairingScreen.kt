package com.cycling.workitout.ui.firstrun

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    onScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onDeviceClick = { viewModel.connectDevice(it) },
                    onNext = { viewModel.nextStep() },
                    allowSkip = true
                )

                PairingStep.HEART_RATE -> PairingStepContent(
                    title = "Pair your Heart Rate Monitor",
                    subtitle = "Optional, but recommended for accurate training.",
                    connected = hrConnected,
                    deviceTypeFilter = DeviceType.HEART_RATE_MONITOR,
                    devices = devices,
                    isScanning = isScanning,
                    onScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onDeviceClick = { viewModel.connectDevice(it) },
                    onNext = { viewModel.nextStep() },
                    allowSkip = true
                )

                PairingStep.FTP -> FtpStepContent(
                    ftp = ftp,
                    onFtpChange = { viewModel.setFtp(it) },
                    onNext = { viewModel.nextStep() }
                )

                PairingStep.READY -> ReadyStepContent(
                    onFinish = { viewModel.completeFirstRun(onPairingComplete) }
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: PairingStep) {
    val steps = listOf(
        "Trainer" to PairingStep.TRAINER,
        "HR" to PairingStep.HEART_RATE,
        "FTP" to PairingStep.FTP,
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
private fun FtpStepContent(
    ftp: Int,
    onFtpChange: (Int) -> Unit,
    onNext: () -> Unit
) {
    Text("What's your FTP?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Your Functional Threshold Power is used to scale AI-generated workouts. Don't know it? 200W is a reasonable starting point — you can change it later in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    var text by remember(ftp) { mutableStateOf(ftp.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.let { onFtpChange(it) }
        },
        label = { Text("FTP (watts)") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onNext,
        enabled = ftp in 50..600,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
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

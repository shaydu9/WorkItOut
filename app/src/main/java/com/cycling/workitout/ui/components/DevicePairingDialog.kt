package com.cycling.workitout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType

@Composable
fun DevicePairingDialog(
    deviceType: DeviceType,
    devices: List<BleDevice>,
    isScanning: Boolean,
    onConnect: (BleDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (deviceType) {
        DeviceType.HEART_RATE_MONITOR -> "Connect Heart Rate Monitor"
        DeviceType.SMART_TRAINER -> "Connect Smart Trainer"
        else -> "Connect Device"
    }

    AppAlertDialog(
        onDismiss = onDismiss,
        title = title,
    ) {
        Column {
            if (isScanning) {
                Row(
                    verticalAlignment = CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning...", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (devices.isEmpty() && !isScanning) {
                Text("No devices found", style = MaterialTheme.typography.bodySmall)
            }
            devices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = CenterVertically
                ) {
                    Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            onConnect(device)
                            onDismiss()
                        }
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
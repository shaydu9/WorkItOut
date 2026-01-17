package com.cycling.workitout.ui.connection

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.R
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToLiveData: () -> Unit
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isHeartRateConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val isPowerMeterConnected by viewModel.isPowerMeterConnected.collectAsStateWithLifecycle()
    
    // Request Bluetooth permissions
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connect_devices)) },
                actions = {
                    IconButton(
                        onClick = onNavigateToLiveData,
                        enabled = isHeartRateConnected || isPowerMeterConnected
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Go to Live Data")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Connection status cards
            ConnectionStatusCard(
                title = "Heart Rate Monitor",
                isConnected = isHeartRateConnected,
                onDisconnect = { viewModel.disconnectHeartRate() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ConnectionStatusCard(
                title = "Power Meter",
                isConnected = isPowerMeterConnected,
                onDisconnect = { viewModel.disconnectPowerMeter() }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Scan button
            Button(
                onClick = {
                    if (isScanning) {
                        viewModel.stopScan()
                    } else {
                        if (permissionsState.allPermissionsGranted) {
                            viewModel.startScan()
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.isBluetoothEnabled() && permissionsState.allPermissionsGranted
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) 
                        stringResource(R.string.stop_scan) 
                    else 
                        stringResource(R.string.scan_devices)
                )
            }
            
            if (!viewModel.isBluetoothEnabled()) {
                Text(
                    text = stringResource(R.string.bluetooth_not_enabled),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            if (!permissionsState.allPermissionsGranted) {
                Text(
                    text = stringResource(R.string.permissions_required),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scanning indicator
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.scanning))
                }
            }
            
            // Device list
            if (discoveredDevices.isEmpty() && !isScanning) {
                Text(
                    text = stringResource(R.string.no_devices_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveredDevices) { device ->
                    DeviceCard(
                        device = device,
                        onConnect = { viewModel.connectDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    title: String,
    isConnected: Boolean,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isConnected) 
                        stringResource(R.string.connected) 
                    else 
                        stringResource(R.string.disconnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isConnected) {
                IconButton(onClick = onDisconnect) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.disconnect)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BleDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (device.deviceType) {
                        DeviceType.HEART_RATE_MONITOR -> Icons.Default.Favorite
                        DeviceType.POWER_METER -> Icons.Default.Speed
                        DeviceType.SMART_TRAINER -> Icons.Default.DirectionsBike
                        DeviceType.UNKNOWN -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = device.deviceType.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

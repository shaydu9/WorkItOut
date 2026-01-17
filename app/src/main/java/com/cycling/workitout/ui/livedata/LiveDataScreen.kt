package com.cycling.workitout.ui.livedata

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.R
import com.cycling.workitout.data.LiveMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDataScreen(
    viewModel: LiveDataViewModel,
    onNavigateBack: () -> Unit
) {
    val liveMetrics by viewModel.liveMetrics.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_data)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status indicator
            if (!liveMetrics.isHeartRateConnected && !liveMetrics.isPowerMeterConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "No devices connected. Please connect devices first.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Heart Rate
            MetricCard(
                title = stringResource(R.string.heart_rate),
                value = liveMetrics.heartRate.toString(),
                unit = stringResource(R.string.bpm),
                icon = Icons.Default.Favorite,
                isConnected = liveMetrics.isHeartRateConnected,
                color = MaterialTheme.colorScheme.error
            )
            
            // Power
            MetricCard(
                title = stringResource(R.string.power),
                value = liveMetrics.power.toString(),
                unit = stringResource(R.string.watts),
                icon = Icons.Default.Speed,
                isConnected = liveMetrics.isPowerMeterConnected,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Cadence
            MetricCard(
                title = stringResource(R.string.cadence),
                value = liveMetrics.cadence.toString(),
                unit = stringResource(R.string.rpm),
                icon = Icons.Default.Settings,
                isConnected = liveMetrics.isPowerMeterConnected,
                color = MaterialTheme.colorScheme.tertiary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Info text
            Text(
                text = "Real-time data from your connected devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    isConnected: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                color.copy(alpha = 0.1f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isConnected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isConnected) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isConnected) value else "--",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) color else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = unit,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isConnected) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            if (!isConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

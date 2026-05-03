package com.cycling.workitout.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.firestore.Ride
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onRideClick: (String) -> Unit
) {
    val rides by viewModel.rides.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        HistoryScreenContent(
            rides = rides,
            onRideClick = onRideClick,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun HistoryScreenContent(
    rides: List<Ride>,
    onRideClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rides.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No rides yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Completed workouts will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(rides, key = { it.id }) { ride ->
                RideCard(ride = ride, onClick = { onRideClick(ride.id) })
            }
        }
    }
}

@Composable
private fun RideCard(ride: Ride, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(ride.startedAtMillis))
    val durationMin = ride.durationSeconds / 60
    val intensityFactor = if (ride.ftpWatts > 0) {
        ride.normalizedPowerWatts.toFloat() / ride.ftpWatts
    } else 0f
    val tss = Math.round(intensityFactor * intensityFactor * durationMin / 60f * 100f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        ride.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (ride.stravaActivityId != null) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Synced to Strava",
                            tint = Color(0xFFFC4C02), // Strava orange
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    "${durationMin}min",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("Avg", "${ride.avgPowerWatts}W")
                StatChip("NP", "${ride.normalizedPowerWatts}W")
                StatChip("TSS", "$tss")
                if (ride.avgHeartRate > 0) {
                    StatChip("HR", "${ride.avgHeartRate}bpm")
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

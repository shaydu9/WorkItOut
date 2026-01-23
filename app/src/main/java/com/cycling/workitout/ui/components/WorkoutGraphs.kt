package com.cycling.workitout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/**
 * Workout interval for structured training
 */
data class WorkoutInterval(
    val durationSeconds: Int,
    val targetPower: Int,
    val name: String,
    val color: Color
)

/**
 * Power data point for real-time graph
 */
data class PowerDataPoint(
    val timeSeconds: Int,
    val power: Int
)

/**
 * Displays workout structure graph (planned intervals) using Vico
 */
@Composable
fun WorkoutStructureGraph(
    intervals: List<WorkoutInterval>,
    currentTimeSeconds: Int,
    modifier: Modifier = Modifier
) {
    val totalDuration = intervals.sumOf { it.durationSeconds }
    
    Column(modifier = modifier) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WORKOUT STRUCTURE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${formatTime(currentTimeSeconds)} / ${formatTime(totalDuration)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Prepare data for Vico column chart
        val modelProducer = remember { CartesianChartModelProducer() }
        
        LaunchedEffect(intervals) {
            val yValues = intervals.map { it.targetPower as Number }
            
            modelProducer.runTransaction {
                columnSeries { series(yValues) }
            }
        }
        
        // Create column components with interval colors
        val columnComponents = intervals.map { interval ->
            rememberLineComponent(
                color = interval.color,
                thickness = 16.dp
            )
        }
        
        // Chart
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer.ColumnProvider.series(
                        columnComponents
                    )
                ),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis()
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surface)
        )
        
        // Current interval info
        val currentInterval = remember(currentTimeSeconds) {
            var cumulative = 0
            intervals.find { interval ->
                cumulative += interval.durationSeconds
                currentTimeSeconds < cumulative
            }
        }
        
        currentInterval?.let { interval ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current: ${interval.name}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Target: ${interval.targetPower}W",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = interval.color
                )
            }
        }
    }
}

/**
 * Displays real-time power graph with actual vs target using Vico
 */
@Composable
fun RealTimePowerGraph(
    powerDataPoints: List<PowerDataPoint>,
    targetPower: Int,
    currentTimeSeconds: Int,
    modifier: Modifier = Modifier
) {
    val timeWindow = 120 // Show last 2 minutes
    
    Column(modifier = modifier) {
        // Title and current values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "POWER OUTPUT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            val currentPower = powerDataPoints.lastOrNull()?.power ?: 0
            val variance = currentPower - targetPower
            val varianceColor = when {
                variance > 20 -> Color(0xFFFF5252) // Too high
                variance < -20 -> Color(0xFF2196F3) // Too low
                else -> Color(0xFF4CAF50) // Good
            }
            
            Text(
                text = "$currentPower W (${if (variance > 0) "+" else ""}$variance)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = varianceColor,
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Prepare data for Vico line chart
        val modelProducer = remember { CartesianChartModelProducer() }
        
        LaunchedEffect(powerDataPoints, currentTimeSeconds) {
            val visiblePoints = powerDataPoints.filter { 
                it.timeSeconds >= (currentTimeSeconds - timeWindow).coerceAtLeast(0) && 
                it.timeSeconds <= currentTimeSeconds 
            }
            
            if (visiblePoints.isNotEmpty()) {
                val actualPowers = visiblePoints.map { it.power as Number }
                val targetPowers = visiblePoints.map { targetPower as Number }
                
                modelProducer.runTransaction {
                    lineSeries {
                        series(actualPowers) // Actual power
                        series(targetPowers) // Target power
                    }
                }
            }
        }
        
        // Chart with default line styles (Vico will handle styling)
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis()
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.surface)
        )
        
        // Legend
        val cyan = Color(0xFF00BCD4)
        val gray = Color.Gray
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(color = cyan, label = "Actual")
                LegendItem(color = gray.copy(alpha = 0.5f), label = "Target")
            }
            
            Text(
                text = formatTime((currentTimeSeconds - timeWindow).coerceAtLeast(0)) + 
                      " - " + formatTime(currentTimeSeconds),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp, 3.dp)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format seconds to MM:SS
 */
private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

/**
 * Generate demo workout intervals
 */
fun generateDemoWorkout(): List<WorkoutInterval> {
    return listOf(
        WorkoutInterval(120, 150, "Warmup", Color(0xFF4CAF50)),      // Green - Recovery
        WorkoutInterval(180, 220, "Endurance", Color(0xFF2196F3)),   // Blue - Endurance
        WorkoutInterval(60, 300, "Threshold", Color(0xFFFFC107)),    // Yellow - Threshold
        WorkoutInterval(30, 380, "Sprint", Color(0xFFFF5252)),       // Red - VO2 Max
        WorkoutInterval(90, 150, "Recovery", Color(0xFF4CAF50)),     // Green - Recovery
        WorkoutInterval(180, 220, "Endurance", Color(0xFF2196F3)),   // Blue - Endurance
        WorkoutInterval(60, 300, "Threshold", Color(0xFFFFC107)),    // Yellow - Threshold
        WorkoutInterval(30, 380, "Sprint", Color(0xFFFF5252)),       // Red - VO2 Max
        WorkoutInterval(120, 150, "Cooldown", Color(0xFF4CAF50))     // Green - Recovery
    )
}

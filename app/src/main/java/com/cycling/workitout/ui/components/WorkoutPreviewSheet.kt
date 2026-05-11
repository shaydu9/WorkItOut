package com.cycling.workitout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.scaledByIntensity
import kotlin.math.roundToInt

/**
 * @param onRegenerate null = hide the regenerate button (library workouts aren't AI-generated).
 * @param onSave null = hide the save-to-library heart button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPreviewSheet(
    workout: WorkoutDefinition,
    intensityScale: Float,
    isGenerating: Boolean,
    displayAsPercent: Boolean,
    onAdjustIntensity: (Int) -> Unit,
    onRegenerate: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var saved by remember(workout.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val displayWorkout = remember(workout, intensityScale) {
            workout.scaledByIntensity(intensityScale)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                if (onSave != null) {
                    IconButton(
                        onClick = {
                            if (!saved) {
                                onSave()
                                saved = true
                            }
                        },
                        enabled = !isGenerating,
                    ) {
                        Icon(
                            if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (saved) "Saved to library" else "Save to library",
                            tint = if (saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onRegenerate != null) {
                    IconButton(
                        onClick = onRegenerate,
                        enabled = !isGenerating,
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Cached,
                                contentDescription = "Regenerate workout",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            WorkoutPreviewChart(
                intervals = displayWorkout.intervals,
                displayAsPercent = displayAsPercent,
                modifier = Modifier.fillMaxWidth(),
            )

            if (workout.description.isNotBlank()) {
                Text(
                    workout.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IntensityPill(
                scale = intensityScale,
                onAdjust = onAdjustIntensity,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Close") }
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
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
fun IntensityPill(
    scale: Float,
    onAdjust: (Int) -> Unit,
) {
    val percent = (scale * 100f).roundToInt()
    val accent = when {
        percent > 100 -> MaterialTheme.colorScheme.tertiary
        percent < 100 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Intensity",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAdjust(-5) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease intensity")
                }
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { onAdjust(5) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase intensity")
                }
            }
        }
    }
}

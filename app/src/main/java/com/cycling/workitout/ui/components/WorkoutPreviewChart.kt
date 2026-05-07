package com.cycling.workitout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycling.workitout.data.WorkoutIntervalDef
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Interactive workout preview chart.
 *
 * Each interval renders as a duration-proportional bar, colored by zone, with a
 * vertical gradient + top highlight for depth. Tap or drag anywhere over the chart
 * to scrub through intervals; the selected one is emphasized and a tooltip floats
 * above showing time, target and duration.
 */
@Composable
fun WorkoutPreviewChart(
    intervals: List<WorkoutIntervalDef>,
    displayAsPercent: Boolean,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
) {
    if (intervals.isEmpty()) return

    var selectedIndex by remember(intervals) { mutableStateOf(0) }

    val cumulativeStartSec = remember(intervals) {
        val out = IntArray(intervals.size)
        var acc = 0
        intervals.forEachIndexed { i, iv ->
            out[i] = acc
            acc += iv.durationSeconds
        }
        out
    }

    // 0 → max(150% FTP, max bar): 100% always reads as "at threshold" visually.
    val maxPercent = remember(intervals) {
        max(1.5f, intervals.maxOf { it.targetPowerPercentFtp })
    }

    Column(modifier = modifier.fillMaxWidth()) {
    // Tooltip lives in its own slot above the chart so it never overlaps the bars.
    // Width-clamped horizontal offset keeps it inside the chart bounds.
    val tooltipSlotHeight = 76.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }

        val sel = remember(intervals, widthPx) {
            buildBarRects(intervals, widthPx, with(density) { chartHeight.toPx() }, maxPercent)
        }.getOrNull(selectedIndex)
        val ivSel = intervals.getOrNull(selectedIndex)

        Box(modifier = Modifier
            .fillMaxWidth()
            .height(tooltipSlotHeight)) {
            if (sel != null && ivSel != null) {
                val tooltipMaxWidthDp = 200.dp
                val tooltipMaxWidthPx = with(density) { tooltipMaxWidthDp.toPx() }
                val barCenterPx = sel.leftPx + sel.widthPx / 2f
                val rawLeftPx = barCenterPx - tooltipMaxWidthPx / 2f
                val clampedLeftPx = rawLeftPx.coerceIn(4f, max(4f, widthPx - tooltipMaxWidthPx - 4f))
                val offsetXDp = with(density) { clampedLeftPx.toDp() }

                WorkoutTooltip(
                    interval = ivSel,
                    startTimeSeconds = cumulativeStartSec[selectedIndex],
                    displayAsPercent = displayAsPercent,
                    modifier = Modifier
                        .offset(x = offsetXDp, y = 0.dp)
                        .widthIn(max = tooltipMaxWidthDp)
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { chartHeight.toPx() }

        // One pass per (intervals, width) — touch handler can binary-search this.
        val barRects = remember(intervals, widthPx, heightPx, maxPercent) {
            buildBarRects(intervals, widthPx, heightPx, maxPercent)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(intervals, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        selectedIndex = indexAtX(barRects, down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { change ->
                                selectedIndex = indexAtX(barRects, change.position.x)
                                if (change.positionChange() != Offset.Zero) change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            // Faint dashed reference line at 100% FTP.
            val ftpY = heightPx * (1f - 1f / maxPercent)
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(0f, ftpY),
                end = Offset(widthPx, ftpY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )

            barRects.forEachIndexed { i, rect ->
                val zone = intervals[i].zone
                val base = Color(zone.colorHex)
                val isSelected = i == selectedIndex
                val dim = if (isSelected) 1f else 0.55f

                val brush = Brush.verticalGradient(
                    colors = listOf(
                        base.lighten(0.45f).copy(alpha = 0.95f * dim),
                        base.copy(alpha = 0.95f * dim),
                        base.darken(0.15f).copy(alpha = 0.95f * dim),
                    ),
                    startY = rect.topPx,
                    endY = rect.bottomPx
                )

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(rect.leftPx + 0.5f, rect.topPx),
                    size = Size((rect.widthPx - 1f).coerceAtLeast(1f), rect.heightPx),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Bright top edge — 2px ribbon that catches the eye.
                drawLine(
                    color = base.lighten(0.6f).copy(alpha = 0.9f * dim),
                    start = Offset(rect.leftPx + 1f, rect.topPx + 1.5f),
                    end = Offset(rect.leftPx + rect.widthPx - 1f, rect.topPx + 1.5f),
                    strokeWidth = 2f
                )

                if (isSelected) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(rect.leftPx + 0.5f, rect.topPx),
                        size = Size((rect.widthPx - 1f).coerceAtLeast(1f), rect.heightPx),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 1.5f)
                    )
                }
            }

            // Vertical scrub indicator on the selected bar.
            barRects.getOrNull(selectedIndex)?.let { sel ->
                val cx = sel.leftPx + sel.widthPx / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, heightPx),
                    strokeWidth = 1f
                )
            }
        }
    }
    } // outer Column
}

@Composable
private fun WorkoutTooltip(
    interval: WorkoutIntervalDef,
    startTimeSeconds: Int,
    displayAsPercent: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = Color(interval.zone.colorHex)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        // IntrinsicSize.Min lets the colored stripe fill the column height without
        // forcing the row to expand to its parent's max height.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = interval.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                TooltipRow("Time", formatHms(startTimeSeconds))
                TooltipRow(
                    label = "Target",
                    value = if (displayAsPercent)
                        "${(interval.targetPowerPercentFtp * 100f).roundToInt()}% (${interval.targetPowerWatts}W)"
                    else
                        "${interval.targetPowerWatts}W (${(interval.targetPowerPercentFtp * 100f).roundToInt()}%)"
                )
                TooltipRow("Duration", formatMs(interval.durationSeconds))
            }
        }
    }
}

@Composable
private fun TooltipRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class BarRect(
    val leftPx: Float,
    val widthPx: Float,
    val topPx: Float,
    val heightPx: Float,
) {
    val bottomPx: Float get() = topPx + heightPx
}

private fun buildBarRects(
    intervals: List<WorkoutIntervalDef>,
    widthPx: Float,
    heightPx: Float,
    maxPercent: Float,
): List<BarRect> {
    val total = intervals.sumOf { it.durationSeconds }.coerceAtLeast(1)
    var x = 0f
    return intervals.map { iv ->
        val w = widthPx * iv.durationSeconds.toFloat() / total.toFloat()
        val h = heightPx * (iv.targetPowerPercentFtp / maxPercent).coerceIn(0.04f, 1f)
        val top = heightPx - h
        val rect = BarRect(leftPx = x, widthPx = w, topPx = top, heightPx = h)
        x += w
        rect
    }
}

private fun indexAtX(rects: List<BarRect>, x: Float): Int {
    if (rects.isEmpty()) return -1
    if (x <= rects.first().leftPx) return 0
    if (x >= rects.last().leftPx + rects.last().widthPx) return rects.lastIndex
    var lo = 0
    var hi = rects.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val r = rects[mid]
        when {
            x < r.leftPx -> hi = mid - 1
            x > r.leftPx + r.widthPx -> lo = mid + 1
            else -> return mid
        }
    }
    return lo.coerceAtMost(rects.lastIndex)
}

private fun formatMs(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatHms(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

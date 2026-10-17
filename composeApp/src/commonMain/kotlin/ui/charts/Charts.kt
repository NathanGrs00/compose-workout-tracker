package ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A point on a line chart: [x] is a timestamp (ms), [tooltip] is shown on hover/tap. */
data class ChartPoint(val x: Long, val y: Double, val tooltip: String)

/** A bar: [label] under the axis, [tooltip] shown on hover/tap. */
data class BarDatum(val label: String, val value: Double, val tooltip: String)

private data class ChartColors(
    val series: Color,
    val grid: Color,
    val axisText: Color,
    val tooltipBackground: Color,
    val tooltipText: Color,
    val surface: Color,
)

@Composable
private fun chartColors() = ChartColors(
    series = MaterialTheme.colorScheme.primary,
    grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    axisText = MaterialTheme.colorScheme.onSurfaceVariant,
    tooltipBackground = MaterialTheme.colorScheme.inverseSurface,
    tooltipText = MaterialTheme.colorScheme.inverseOnSurface,
    surface = MaterialTheme.colorScheme.surfaceContainerLow,
)

/** "Nice" axis ticks (multiples of 1, 2, 2.5 or 5 x 10^n) covering [min]..[max]. */
fun niceTicks(min: Double, max: Double, targetCount: Int = 4): List<Double> {
    if (max <= min) {
        val base = if (max == 0.0) 1.0 else abs(max) * 0.1
        return niceTicks(min - base, max + base, targetCount)
    }
    val rawStep = (max - min) / targetCount
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep }
    val start = floor(min / step) * step
    val end = ceil(max / step) * step
    val ticks = mutableListOf<Double>()
    var tick = start
    while (tick <= end + step / 2) {
        ticks += tick
        tick += step
    }
    return ticks
}

/** Tracks the pointer (mouse hover or touch) as an x position, or null when it leaves. */
private fun Modifier.trackPointer(onMove: (Float?) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.firstOrNull()?.position
            when (event.type) {
                PointerEventType.Exit -> onMove(null)
                else -> if (position != null) onMove(position.x)
            }
        }
    }
}

/**
 * Line chart over time with a y axis, sparse date labels, and a crosshair + tooltip on hover/tap.
 * The y axis doesn't start at zero, so changes in strength are visible.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    yLabel: (Double) -> String,
    xLabel: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.axisText)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = colors.tooltipText)
    var pointerX by remember(points) { mutableStateOf<Float?>(null) }

    Canvas(modifier.fillMaxWidth().height(height).trackPointer { pointerX = it }) {
        if (points.isEmpty()) return@Canvas
        val sorted = points.sortedBy { it.x }
        val minY = sorted.minOf { it.y }
        val maxY = sorted.maxOf { it.y }
        val pad = (maxY - minY).takeIf { it > 0 }?.times(0.1) ?: 0.0
        val ticks = niceTicks((minY - pad).coerceAtLeast(if (minY >= 0) 0.0 else minY - pad), maxY + pad)
        val yMin = ticks.first()
        val yMax = ticks.last()

        val yLabelWidth = ticks.maxOf { measurer.measure(yLabel(it), labelStyle).size.width }.toFloat()
        val left = yLabelWidth + 8.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val minX = sorted.first().x
        val maxX = sorted.last().x
        fun xOf(x: Long) = if (maxX == minX) (left + right) / 2 else left + (right - left) * (x - minX).toFloat() / (maxX - minX)
        fun yOf(y: Double) = bottom - (bottom - top) * ((y - yMin) / (yMax - yMin)).toFloat()

        // Grid + y labels
        ticks.forEach { tick ->
            val y = yOf(tick)
            drawLine(colors.grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(yLabel(tick), labelStyle)
            drawText(layout, topLeft = Offset(left - 8.dp.toPx() - layout.size.width, y - layout.size.height / 2))
        }

        // X labels: first, middle, last (skipping ones that would overlap)
        val labelIndexes = listOf(0, sorted.size / 2, sorted.size - 1).distinct()
        var lastLabelEnd = Float.NEGATIVE_INFINITY
        labelIndexes.forEach { index ->
            val layout = measurer.measure(xLabel(sorted[index].x), labelStyle)
            val x = (xOf(sorted[index].x) - layout.size.width / 2).coerceIn(left - 4.dp.toPx(), size.width - layout.size.width)
            if (x > lastLabelEnd + 8.dp.toPx()) {
                drawText(layout, topLeft = Offset(x, bottom + 6.dp.toPx()))
                lastLabelEnd = x + layout.size.width
            }
        }

        // Line
        val path = Path()
        sorted.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(xOf(p.x), yOf(p.y)) else path.lineTo(xOf(p.x), yOf(p.y))
        }
        drawPath(path, colors.series, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Markers (with a surface ring so overlapping dots stay readable)
        val showAllDots = sorted.size <= 40
        sorted.forEach { p ->
            if (showAllDots) {
                drawCircle(colors.surface, radius = 5.dp.toPx(), center = Offset(xOf(p.x), yOf(p.y)))
                drawCircle(colors.series, radius = 3.5.dp.toPx(), center = Offset(xOf(p.x), yOf(p.y)))
            }
        }

        // Hover / tap: nearest point
        pointerX?.let { px ->
            val nearest = sorted.minBy { abs(xOf(it.x) - px) }
            val cx = xOf(nearest.x)
            val cy = yOf(nearest.y)
            drawLine(
                colors.axisText.copy(alpha = 0.5f), Offset(cx, top), Offset(cx, bottom),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawCircle(colors.surface, radius = 7.dp.toPx(), center = Offset(cx, cy))
            drawCircle(colors.series, radius = 5.dp.toPx(), center = Offset(cx, cy))
            drawTooltip(measurer, nearest.tooltip, tooltipStyle, colors, anchor = Offset(cx, cy), bounds = Rect(0f, 0f, size.width, size.height))
        }
    }
}

/** Vertical bar chart starting at zero, with hover/tap tooltips. */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.axisText)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = colors.tooltipText)
    var pointerX by remember(bars) { mutableStateOf<Float?>(null) }

    Canvas(modifier.fillMaxWidth().height(height).trackPointer { pointerX = it }) {
        if (bars.isEmpty()) return@Canvas
        val ticks = niceTicks(0.0, bars.maxOf { it.value }.coerceAtLeast(1.0))
        val yMax = ticks.last()
        val yLabelWidth = ticks.maxOf { measurer.measure(yLabel(it), labelStyle).size.width }.toFloat()
        val left = yLabelWidth + 8.dp.toPx()
        val right = size.width
        val top = 12.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun yOf(v: Double) = bottom - (bottom - top) * (v / yMax).toFloat()

        ticks.forEach { tick ->
            val y = yOf(tick)
            drawLine(colors.grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(yLabel(tick), labelStyle)
            drawText(layout, topLeft = Offset(left - 8.dp.toPx() - layout.size.width, y - layout.size.height / 2))
        }

        val slot = (right - left) / bars.size
        val gap = 2.dp.toPx().coerceAtLeast(slot * 0.25f)
        val barWidth = (slot - gap).coerceAtLeast(1f)
        val radius = 4.dp.toPx().coerceAtMost(barWidth / 2)
        val hovered = pointerX?.let { px -> ((px - left) / slot).toInt().takeIf { it in bars.indices } }

        // Labels: as many as fit without overlapping, always including the last one.
        val maxLabelWidth = bars.maxOf { measurer.measure(it.label, labelStyle).size.width } + 8.dp.toPx()
        val every = ceil(maxLabelWidth / slot).toInt().coerceAtLeast(1)

        bars.forEachIndexed { i, bar ->
            val x = left + slot * i + gap / 2
            val y = yOf(bar.value)
            if (bar.value > 0) {
                val color = if (hovered == null || hovered == i) colors.series else colors.series.copy(alpha = 0.45f)
                drawTopRoundedBar(color, Rect(x, y, x + barWidth, bottom), radius)
            }
            if ((bars.size - 1 - i) % every == 0) {
                val layout = measurer.measure(bar.label, labelStyle)
                val lx = (x + barWidth / 2 - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, bottom + 6.dp.toPx()))
            }
        }
        drawLine(colors.axisText.copy(alpha = 0.6f), Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

        hovered?.let { i ->
            val bar = bars[i]
            val cx = left + slot * i + gap / 2 + barWidth / 2
            drawTooltip(measurer, bar.tooltip, tooltipStyle, colors, anchor = Offset(cx, yOf(bar.value)), bounds = Rect(0f, 0f, size.width, size.height))
        }
    }
}

private fun DrawScope.drawTopRoundedBar(color: Color, rect: Rect, radius: Float) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = rect,
                topLeft = CornerRadius(radius),
                topRight = CornerRadius(radius),
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, color)
}

private fun DrawScope.drawTooltip(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    colors: ChartColors,
    anchor: Offset,
    bounds: Rect,
) {
    val layout = measurer.measure(text, style)
    val padH = 8.dp.toPx()
    val padV = 6.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val x = (anchor.x - w / 2).coerceIn(bounds.left, (bounds.right - w).coerceAtLeast(bounds.left))
    val above = anchor.y - h - 10.dp.toPx()
    val y = if (above >= bounds.top) above else anchor.y + 10.dp.toPx()
    drawRoundRect(colors.tooltipBackground, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx()))
    drawText(layout, topLeft = Offset(x + padH, y + padV))
}

/** Horizontal bars as plain rows: label, bar, value. Good for ranked categories (e.g. muscle groups). */
@Composable
fun HorizontalBars(
    items: List<Pair<String, Double>>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 110.dp,
) {
    val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(labelWidth), maxLines = 1)
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp))) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRoundRect(track, cornerRadius = CornerRadius(4.dp.toPx()))
                        drawRoundRect(
                            barColor,
                            size = Size((size.width * (value / max).toFloat()).coerceAtLeast(4.dp.toPx()), size.height),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                }
                Text(
                    valueLabel(value),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

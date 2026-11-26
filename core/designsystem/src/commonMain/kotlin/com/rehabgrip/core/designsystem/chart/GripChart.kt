package com.rehabgrip.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.progress.Dodge
import kotlin.math.ceil
import kotlin.math.max

/** One day of a hand's line. [fx] is 0..1 along the axis. */
data class GripPointUi(val fx: Float, val kg: Float, val dateLabel: String)

/**
 * One hand of one grip. Right = solid line and filled circle with white letters; left = dashed line (55 %),
 * tinted circle with a coloured border and dark letters. [gripIndex]/[gripCount] drive the horizontal dodge.
 */
data class GripLineUi(
    val code: String,
    val gripName: String,
    val handName: String,
    val right: Boolean,
    val base: Color,
    val tint: Color,
    val dark: Color,
    val points: List<GripPointUi>,
    val gripIndex: Int,
    val gripCount: Int,
)

data class AxisTickUi(val fx: Float, val label: String)

/** A drawn point: where it is, and whether it carries the lettered marker. */
internal class PlacedPoint(val line: GripLineUi, val point: GripPointUi, val x: Float, val y: Float, val marker: Boolean)

private class Frame(val left: Float, val right: Float, val top: Float, val bottom: Float, val yMin: Float, val yMax: Float, val radius: Float) {
    fun y(v: Float) = bottom - (v - yMin) / (yMax - yMin) * (bottom - top)
}

private fun niceMax(value: Float) = max(10f, ceil(value / 10f) * 10f)

private fun frameOf(w: Float, h: Float, d: Float, fontScale: Float, lines: List<GripLineUi>): Frame {
    val kgs = lines.flatMap { l -> l.points.map { it.kg } }
    val yMax = niceMax((kgs.maxOrNull() ?: 10f) * 1.1f)
    val yMin = max(0f, (kgs.minOrNull() ?: 0f) * 0.8f).let { (it / 10).toInt() * 10f }
    val radius = 12f * d * fontScale.coerceIn(1f, 1.4f)
    return Frame(left = 40f * d, right = w - 8f * d, top = radius + 20f * d, bottom = h - 24f * d, yMin = yMin, yMax = if (yMax <= yMin) yMin + 10f else yMax, radius = radius)
}

/** Where every point goes: a day with a single point is dodged; longer lines keep their x and carry one marker. */
private fun place(frame: Frame, d: Float, lines: List<GripLineUi>): List<PlacedPoint> {
    val minX = frame.left + frame.radius + 2f * d
    val maxX = frame.right - frame.radius - 2f * d
    fun baseX(fx: Float) = minX + fx * (maxX - minX)
    val out = ArrayList<PlacedPoint>()
    lines.filter { it.points.size == 1 }.groupBy { it.points[0].fx }.forEach { (fx, group) ->
        val count = group.maxOf { it.gripCount }
        val gripDp = if (count > 1) ((maxX - minX) / d - 2 * Dodge.HAND_DP).div(count - 1).coerceIn(10f, Dodge.GRIP_DP) else Dodge.GRIP_DP
        val offsets = group.map { Dodge.offsetDp(it.right, it.gripIndex, it.gripCount, gripDp = gripDp) * d }
        val xs = Dodge.place(baseX(fx), offsets, minX, maxX)
        group.forEachIndexed { i, l -> out += PlacedPoint(l, l.points[0], xs[i], frame.y(l.points[0].kg), marker = true) }
    }
    lines.filter { it.points.size > 1 }.forEach { l ->
        val m = Dodge.markerIndex(l.gripIndex, l.points.size)
        l.points.forEachIndexed { i, p -> out += PlacedPoint(l, p, baseX(p.fx), frame.y(p.kg), marker = i == m) }
    }
    return out
}

/**
 * Evolution chart: lettered markers (right filled, left tinted), kg above each marker, unique date ticks and
 * tap on any point. Right line solid, left line dashed at 55 %.
 */
@Composable
fun GripChart(
    lines: List<GripLineUi>,
    ticks: List<AxisTickUi>,
    modifier: Modifier = Modifier,
    onPointTap: (GripLineUi, GripPointUi) -> Unit = { _, _ -> },
) {
    val measurer = rememberTextMeasurer()
    val fontScale = LocalDensity.current.fontScale
    Canvas(
        modifier.pointerInput(lines, fontScale) {
            detectTapGestures { tap ->
                val d = density
                val placed = place(frameOf(size.width.toFloat(), size.height.toFloat(), d, fontScale, lines), d, lines)
                // Markers first (they sit on top), then the plain dots; the nearest one inside a finger-sized reach.
                val reach = 22f * d
                val hit = placed.filter { (it.x - tap.x) * (it.x - tap.x) + (it.y - tap.y) * (it.y - tap.y) <= reach * reach }
                    .minWithOrNull(compareBy<PlacedPoint> { !it.marker }.thenBy { (it.x - tap.x) * (it.x - tap.x) + (it.y - tap.y) * (it.y - tap.y) })
                hit?.let { onPointTap(it.line, it.point) }
            }
        },
    ) {
        val d = density
        val f = frameOf(size.width, size.height, d, fontScale, lines)
        drawGrid(measurer, f)
        val placed = place(f, d, lines)
        val minX = f.left + f.radius + 2f * d
        val maxX = f.right - f.radius - 2f * d
        ticks.forEach { t ->
            val layout = measurer.measure(t.label, axisStyle)
            val tx = (minX + t.fx * (maxX - minX) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, f.bottom + 4f * d))
        }
        lines.filter { it.points.size > 1 }.forEach { l ->
            val path = Path()
            placed.filter { it.line === l }.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(
                path, if (l.right) l.base else l.base.copy(alpha = 0.55f),
                style = Stroke(2.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (l.right) null else PathEffect.dashPathEffect(floatArrayOf(6f * d, 5f * d))),
            )
        }
        placed.filter { !it.marker }.forEach { p -> drawDot(p) }
        placed.filter { it.marker }.forEach { p -> drawMarker(measurer, p, f) }
    }
}

private fun DrawScope.drawDot(p: PlacedPoint) {
    val c = Offset(p.x, p.y)
    val l = p.line
    if (l.right) drawCircle(l.base, 4f * density, c)
    else {
        drawCircle(l.tint, 4f * density, c)
        drawCircle(l.base, 4f * density, c, style = Stroke(1.5f * density))
    }
}

private fun DrawScope.drawMarker(measurer: TextMeasurer, p: PlacedPoint, f: Frame) {
    val l = p.line
    val c = Offset(p.x, p.y)
    if (l.right) drawCircle(l.base, f.radius, c)
    else {
        drawCircle(l.tint, f.radius, c)
        drawCircle(l.base, f.radius, c, style = Stroke(2f * density))
    }
    val style = TextStyle(
        color = if (l.right) Color.White else l.dark,
        fontSize = if (l.code.length >= 3) 9.sp else 11.sp, fontWeight = FontWeight.Medium,
    )
    val text = measurer.measure(l.code, style)
    drawText(text, topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f))
    val value = measurer.measure(formatKg(p.point.kg.toDouble()), axisStyle)
    val vy = (c.y - f.radius - value.size.height - 1f * density).coerceAtLeast(0f)
    drawText(value, topLeft = Offset((c.x - value.size.width / 2f).coerceIn(0f, size.width - value.size.width), vy))
}

private fun DrawScope.drawGrid(measurer: TextMeasurer, f: Frame) {
    val step = if (f.yMax - f.yMin > 50f) 20f else 10f
    var v = f.yMin
    while (v <= f.yMax + 0.01f) {
        val y = f.y(v)
        drawLine(RgColors.Outline, Offset(f.left, y), Offset(f.right, y), 1f)
        val layout = measurer.measure(v.toInt().toString(), axisStyle)
        drawText(layout, topLeft = Offset(f.left - layout.size.width - 6f, y - layout.size.height / 2f))
        v += step
    }
}

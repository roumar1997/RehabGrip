package com.rehabgrip.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.progress.PlacedLabel
import com.rehabgrip.core.domain.progress.YScale
import kotlin.math.max

private val LABEL_STYLE = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
private const val AXIS_WIDTH_DP = 34
private const val AXIS_BELOW_DP = 24f

/**
 * Evolution chart. Right hand = solid line, filled dot and filled kilo pill under the dot; left hand = dashed line,
 * tinted dot and hollow pill above the dot. The y axis stays on the left while the plot scrolls sideways (starting at
 * the last days) when the days do not fit with legible kilos. Grid every 5 kg, numbers every 10.
 * [ticks] gives the date ticks for a maximum count (it depends on the width). Tap a point or its pill for details.
 */
@Composable
fun GripChart(
    lines: List<GripLineUi>,
    ticks: (maxTicks: Int) -> List<AxisTickUi>,
    modifier: Modifier = Modifier,
    labelMode: ChartLabelMode = ChartLabelMode.ALL,
    lettered: Boolean = false,
    onPointTap: (GripLineUi, GripPointUi) -> Unit = { _, _ -> },
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val d = density.density
    val fontScale = density.fontScale
    val metrics = remember(d, fontScale, lettered, measurer) {
        val textH = measurer.measure("0", LABEL_STYLE).size.height
        ChartMetrics(d, textH + 6f * d, 7f * d, { measurer.measure(it, LABEL_STYLE).size.width.toFloat() }, lettered, 12f * d * fontScale.coerceIn(1f, 1.4f))
    }
    val scale = remember(lines) { scaleOf(lines) }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier) {
        val heightPx = constraints.maxHeight.toFloat()
        val viewportPx = constraints.maxWidth - AXIS_WIDTH_DP * d
        val axisBelow = AXIS_BELOW_DP * d
        val built = remember(lines, metrics, viewportPx, heightPx, labelMode) { build(lines, metrics, viewportPx, heightPx, axisBelow, labelMode) }
        LaunchedEffect(scroll.maxValue, lines) { scroll.scrollTo(scroll.maxValue) }
        Row(Modifier.fillMaxSize()) {
            Canvas(Modifier.width(AXIS_WIDTH_DP.dp).fillMaxHeight()) { drawAxisNumbers(measurer, built.frame, scale) }
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                Canvas(
                    Modifier.width(with(density) { built.contentPx.toDp() }).height(with(density) { heightPx.toDp() }).pointerInput(built) {
                        detectTapGestures { tap -> hit(built, d, tap)?.let { (l, p) -> onPointTap(l, p) } }
                    },
                ) {
                    val f = built.frame
                    drawGridLines(f, scale)
                    drawTicks(measurer, f, ticks, d)
                    lines.filter { it.points.size > 1 }.forEach { l -> drawSeries(l, built.placed.filter { it.line === l }) }
                    built.placed.filter { !it.marker }.forEach { drawDot(it) }
                    built.placed.filter { it.marker }.forEach { drawMarker(measurer, it, f) }
                    built.labels.labels.forEach { drawPill(measurer, it, built.placed[it.keys.first()].line) }
                }
            }
        }
    }
}

/** Pills first (they are on top), then markers, then plain dots; nearest inside a finger-sized reach. */
private fun hit(b: Built, d: Float, tap: Offset): Pair<GripLineUi, GripPointUi>? {
    b.labels.labels.firstOrNull { it.contains(tap.x, tap.y) }?.let { val p = b.placed[it.keys.first()]; return p.line to p.point }
    val reach = 22f * d
    fun dist(p: PlacedPoint) = (p.x - tap.x) * (p.x - tap.x) + (p.y - tap.y) * (p.y - tap.y)
    val best = b.placed.filter { dist(it) <= reach * reach }.minWithOrNull(compareBy<PlacedPoint> { !it.marker }.thenBy { dist(it) })
    return best?.let { it.line to it.point }
}

private fun DrawScope.drawSeries(l: GripLineUi, pts: List<PlacedPoint>) {
    val path = Path()
    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    drawPath(
        path, if (l.right) l.base else l.base.copy(alpha = 0.55f),
        style = Stroke(2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (l.right) null else PathEffect.dashPathEffect(floatArrayOf(6f * density, 5f * density))),
    )
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
    val style = TextStyle(color = if (l.right) l.onBase else l.dark, fontSize = if (l.code.length >= 3) 9.sp else 11.sp, fontWeight = FontWeight.Medium)
    val text = measurer.measure(l.code, style)
    drawText(text, topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f))
}

/** Right = filled pill with white number; left (and fused «32 · 32») = tinted pill, coloured border, dark number. */
private fun DrawScope.drawPill(measurer: TextMeasurer, label: PlacedLabel, line: GripLineUi) {
    val topLeft = Offset(label.cx - label.width / 2f, label.top)
    val size = Size(label.width, label.height)
    val corner = CornerRadius(label.height / 2f)
    val filled = label.right && !label.fused
    if (filled) drawRoundRect(line.base, topLeft, size, corner)
    else {
        drawRoundRect(line.tint, topLeft, size, corner)
        drawRoundRect(line.base, topLeft, size, corner, style = Stroke(1.5f * density))
    }
    val text = measurer.measure(label.text, LABEL_STYLE.copy(color = if (filled) line.onBase else line.dark))
    drawText(text, topLeft = Offset(label.cx - text.size.width / 2f, label.top + (label.height - text.size.height) / 2f))
}

private fun DrawScope.drawGridLines(f: Frame, scale: YScale) {
    scale.grid().forEach { g ->
        drawLine(
            if (g.labelled) RgColors.Outline else RgColors.Outline.copy(alpha = 0.45f),
            Offset(f.left, f.y(g.value)), Offset(f.right, f.y(g.value)), if (g.labelled) 1f else 0.7f * density,
        )
    }
}

private fun DrawScope.drawAxisNumbers(measurer: TextMeasurer, f: Frame, scale: YScale) {
    scale.grid().filter { it.labelled }.forEach { g ->
        val layout = measurer.measure(g.value.toInt().toString(), axisStyle)
        drawText(layout, topLeft = Offset(size.width - layout.size.width - 6f * density, f.y(g.value) - layout.size.height / 2f))
    }
}

/** Date ticks under the plot: as many as fit without touching, never repeated. */
private fun DrawScope.drawTicks(measurer: TextMeasurer, f: Frame, ticks: (Int) -> List<AxisTickUi>, d: Float) {
    val labelW = measurer.measure("00/00", axisStyle).size.width + 26f * d
    val n = max(2, ((f.maxX - f.minX) / labelW).toInt())
    ticks(n).forEach { t ->
        val layout = measurer.measure(t.label, axisStyle)
        val tx = (f.x(t.fx) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(tx, size.height - AXIS_BELOW_DP * d + 6f * d))
    }
}

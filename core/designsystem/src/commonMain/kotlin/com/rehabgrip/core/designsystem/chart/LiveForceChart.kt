package com.rehabgrip.core.designsystem.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.signal.ForceRingBuffer
import com.rehabgrip.core.domain.signal.niceScale
import com.rehabgrip.core.domain.workout.TargetZone
import kotlin.math.max
import kotlin.time.TimeSource

/**
 * Sample sink of a live graph. Samples go into a primitive ring buffer; [tick] is a
 * snapshot state that is read ONLY while drawing, so a new sample invalidates the
 * canvas and never recomposes the screen. [frameMs] is the animation clock.
 */
class LiveForceTrace(capacity: Int = ForceRingBuffer.DEFAULT_CAPACITY) {
    val buffer = ForceRingBuffer(capacity)
    private val mark = TimeSource.Monotonic.markNow()

    /** Bumped on every sample; read only inside the draw phase. */
    var tick by mutableLongStateOf(0L)
        private set

    /** Time of the last animation frame; read only inside the draw phase. */
    var frameMs by mutableLongStateOf(0L)
        internal set

    fun nowMs(): Long = mark.elapsedNow().inWholeMilliseconds

    fun add(kg: Double) {
        buffer.add(nowMs(), kg.toFloat())
        tick = buffer.version
    }

    fun clear() {
        buffer.clear()
        tick = buffer.version
    }
}

private const val RENDER_LAG_MS = 150L
private const val MAX_POINTS_PER_PX = 0.5f

private class PeakLabel {
    var key = Int.MIN_VALUE
    var layout: TextLayoutResult? = null
}

/**
 * Real-time force graph: the time window slides continuously with the frame clock
 * (60 fps) and the line is interpolated between samples, so it does not step at the
 * 10 Hz of the scale. Paths, brushes and labels are cached; nothing is allocated per frame.
 */
@Composable
fun LiveForceChart(
    trace: LiveForceTrace,
    modifier: Modifier = Modifier,
    windowMs: Long = 10_000,
    zone: TargetZone? = null,
) {
    val measurer = rememberTextMeasurer()
    LaunchedEffect(trace) {
        while (true) withFrameNanos { trace.frameMs = trace.nowMs() }
    }
    val peakStyle = remember { TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
    Box(
        modifier.fillMaxSize().drawWithCache {
            val left = (40f * density)
            val bottom = size.height - (22f * density)
            val top = (30f * density)
            val right = size.width - (8f * density)
            val line = Path()
            val area = Path()
            val lineStroke = Stroke((3f * density), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val areaBrush = Brush.verticalGradient(
                listOf(RgColors.Primary.copy(alpha = 0.28f), RgColors.Primary.copy(alpha = 0.03f)), top, bottom,
            )
            val peakDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            val xLabels = listOf("-${windowMs / 1000} s", "-${windowMs / 2000} s", "ahora").map { measurer.measure(it, axisStyle) }
            val yLabels = HashMap<Int, TextLayoutResult>()
            val peakLabel = PeakLabel()
            val maxPoints = max(8, (size.width * MAX_POINTS_PER_PX).toInt())
            val zoneTop = (zone?.maxKg ?: zone?.minKg ?: 0.0).toFloat()
            val buf = trace.buffer
            var holdMax = 20f // grows at once, shrinks only when far too big: no jumps from noise

            onDrawBehind {
                trace.tick // invalidates only the drawing when a sample arrives
                val end = trace.frameMs - RENDER_LAG_MS
                val start = end - windowMs
                val first = buf.firstIndexAtOrAfter(start)
                var last = buf.firstIndexAtOrAfter(end + 1) - 1
                if (last < first - 1) last = first - 1

                var visibleMax = 0f
                for (i in first..last) visibleMax = max(visibleMax, buf.valueAt(i))
                val needed = max(max(20f, zoneTop * 1.25f), visibleMax * 1.15f)
                if (needed > holdMax || needed < holdMax * 0.5f) holdMax = needed
                val scale = niceScale(holdMax.toDouble(), 3)
                val yMax = scale.max.toFloat()
                fun x(t: Long) = left + (t - start).toFloat() / windowMs * (right - left)
                fun y(v: Float) = bottom - v / yMax * (bottom - top)

                for (i in 0 until scale.tickCount) {
                    val value = scale.tick(i).toInt()
                    val yy = y(value.toFloat())
                    drawLine(RgColors.Outline, Offset(left, yy), Offset(right, yy), 1f)
                    val layout = yLabels.getOrPut(value) { measurer.measure(value.toString(), axisStyle) }
                    drawText(layout, topLeft = Offset(left - layout.size.width - 6f, yy - layout.size.height / 2f))
                }
                if (zone != null) drawZone(zone, left, right, top, bottom, ::y)
                val xs = floatArrayOf(left, (left + right) / 2f, right)
                for (k in 0..2) {
                    val l = xLabels[k]
                    val tx = (xs[k] - l.size.width / 2f).coerceIn(0f, size.width - l.size.width)
                    drawText(l, topLeft = Offset(tx, bottom + (4f * density)))
                }
                if (buf.size == 0 || end < buf.timeAt(0) || last < first) return@onDrawBehind

                val stride = max(1, (last - first + 1) / maxPoints)
                val x0 = x(max(start, buf.timeAt(0)))
                val y0 = y(buf.interpolate(max(start, buf.timeAt(0))))
                line.rewind()
                area.rewind()
                line.moveTo(x0, y0)
                area.moveTo(x0, bottom)
                area.lineTo(x0, y0)
                var peak = 0f
                var peakT = 0L
                var i = first
                while (i <= last) {
                    val v = buf.valueAt(i)
                    val t = buf.timeAt(i)
                    if (v > peak) { peak = v; peakT = t }
                    val px = x(t)
                    val py = y(v)
                    line.lineTo(px, py)
                    area.lineTo(px, py)
                    i += stride
                }
                val xEnd = x(end)
                val yEnd = y(buf.interpolate(end))
                line.lineTo(xEnd, yEnd)
                area.lineTo(xEnd, yEnd)
                area.lineTo(xEnd, bottom)
                area.close()
                drawPath(area, areaBrush)
                drawPath(line, RgColors.Primary, style = lineStroke)

                if (peak > 0.5f) {
                    val at = Offset(x(peakT), y(peak))
                    drawLine(RgColors.Primary.copy(alpha = 0.6f), at, Offset(at.x, bottom), (1.5f * density), pathEffect = peakDash)
                    drawCircle(Color.White, (7f * density), at)
                    drawCircle(RgColors.Primary, (5f * density), at)
                    val key = (peak * 10f).toInt()
                    if (peakLabel.key != key) {
                        peakLabel.key = key
                        peakLabel.layout = measurer.measure("${formatKg(peak.toDouble())} kg", peakStyle)
                    }
                    val layout = peakLabel.layout!!
                    val pad = (6f * density)
                    val w = layout.size.width + pad * 2
                    val h = layout.size.height + pad
                    val bx = (at.x - w / 2).coerceIn(0f, size.width - w)
                    val by = (at.y - h - (10f * density)).coerceAtLeast(0f)
                    drawRoundRect(RgColors.PrimaryDark, Offset(bx, by), Size(w, h), CornerRadius((8f * density)))
                    drawText(layout, topLeft = Offset(bx + pad, by + pad / 2))
                }
            }
        },
    )
}

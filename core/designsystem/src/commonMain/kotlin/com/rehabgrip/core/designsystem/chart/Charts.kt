package com.rehabgrip.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.ForceSample
import com.rehabgrip.core.domain.workout.TargetZone
import kotlin.math.ceil
import kotlin.math.max

/** One line of a [LineChart]. x in 0..1 (time fraction), y in kg. */
data class ChartSeries(val color: Color, val points: List<Pair<Float, Float>>, val dashed: Boolean = false)

internal val axisStyle = TextStyle(color = RgColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

private fun niceMax(value: Float, step: Float = 10f) = max(step, ceil(value / step) * step)

/** Multi-series progress chart with y grid, x labels and dots. */
@Composable
fun LineChart(series: List<ChartSeries>, xLabels: List<String>, modifier: Modifier = Modifier, referenceY: Float? = null) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val all = series.flatMap { it.points }
        val yMax = niceMax(max(all.maxOfOrNull { it.second } ?: 10f, referenceY ?: 0f) * 1.1f)
        val yMin = max(0f, minOf(all.minOfOrNull { it.second } ?: 0f, referenceY ?: Float.MAX_VALUE) * 0.8f).let { (it / 10).toInt() * 10f }
        val left = 40.dp2px(this)
        val bottom = size.height - 22.dp2px(this)
        val top = 8.dp2px(this)
        val right = size.width - 8.dp2px(this)
        fun x(f: Float) = left + f * (right - left)
        fun y(v: Float) = bottom - (v - yMin) / (yMax - yMin) * (bottom - top)

        drawYGrid(measurer, yMin, yMax, left, right, ::y)
        xLabels.forEachIndexed { i, label ->
            val f = if (xLabels.size == 1) 0f else i / (xLabels.size - 1f)
            val layout = measurer.measure(label, axisStyle)
            val tx = (x(f) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, bottom + 4.dp2px(this)))
        }
        referenceY?.let { r ->
            drawLine(RgColors.Navy.copy(alpha = 0.55f), Offset(left, y(r)), Offset(right, y(r)), 2.dp2px(this), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }
        series.forEach { s ->
            if (s.points.isEmpty()) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, (fx, v) ->
                if (i == 0) path.moveTo(x(fx), y(v)) else path.lineTo(x(fx), y(v))
            }
            drawPath(
                path, s.color,
                style = Stroke(3.dp2px(this), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(16f, 12f)) else null),
            )
            s.points.forEach { (fx, v) ->
                drawCircle(Color.White, 5.dp2px(this), Offset(x(fx), y(v)))
                drawCircle(s.color, 3.5f.dp2px(this), Offset(x(fx), y(v)))
            }
        }
    }
}

/** Target band (green) between min and max, with dashed limit lines. */
internal fun DrawScope.drawZone(zone: TargetZone, left: Float, right: Float, top: Float, bottom: Float, y: (Float) -> Float) {
    val yTop = zone.maxKg?.let { y(it.toFloat()) } ?: top
    val yBottom = zone.minKg?.let { y(it.toFloat()) } ?: bottom
    drawRect(RgColors.Success.copy(alpha = 0.14f), Offset(left, yTop), Size(right - left, yBottom - yTop))
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    zone.maxKg?.let { drawLine(RgColors.Danger, Offset(left, yTop), Offset(right, yTop), 2.5f.dp2px(this), pathEffect = dash) }
    zone.minKg?.let { drawLine(RgColors.Warning, Offset(left, yBottom), Offset(right, yBottom), 2.5f.dp2px(this), pathEffect = dash) }
}

private fun DrawScope.drawYGrid(
    measurer: TextMeasurer,
    yMin: Float,
    yMax: Float,
    left: Float,
    right: Float,
    y: (Float) -> Float,
    step: Float = if (yMax - yMin > 50f) 20f else 10f,
) {
    var v = yMin
    while (v <= yMax + 0.01f) {
        val yy = y(v)
        drawLine(RgColors.Outline, Offset(left, yy), Offset(right, yy), 1f)
        val layout = measurer.measure(v.toInt().toString(), axisStyle)
        drawText(layout, topLeft = Offset(left - layout.size.width - 6f, yy - layout.size.height / 2f))
        v += step
    }
}

internal fun Int.dp2px(scope: DrawScope) = this * scope.density
internal fun Float.dp2px(scope: DrawScope) = this * scope.density

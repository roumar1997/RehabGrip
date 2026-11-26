package com.rehabgrip.core.designsystem.chart

import androidx.compose.ui.graphics.Color
import com.rehabgrip.core.domain.progress.ChartLabels
import com.rehabgrip.core.domain.progress.ChartScroll
import com.rehabgrip.core.domain.progress.KgText
import com.rehabgrip.core.domain.progress.LabelLayout
import com.rehabgrip.core.domain.progress.LabelPoint
import com.rehabgrip.core.domain.progress.LabelSpec
import com.rehabgrip.core.domain.progress.YScale

/** One day of a hand's line. [fx] is 0..1 along the axis. */
data class GripPointUi(val fx: Float, val kg: Float, val dateLabel: String)

/**
 * One hand of one grip. Right = solid line, filled dot and filled kilo pill; left = dashed line (55 %),
 * tinted dot with a coloured border and hollow pill.
 */
data class GripLineUi(
    val code: String,
    val gripName: String,
    val handName: String,
    val right: Boolean,
    val base: Color,
    val tint: Color,
    val dark: Color,
    val onBase: Color,
    val points: List<GripPointUi>,
)

data class AxisTickUi(val fx: Float, val label: String)

/** ALL: every point carries its kilos (what fits); ENDS: only the first and last point of each line. */
enum class ChartLabelMode { ALL, ENDS }

/** A drawn point. */
internal class PlacedPoint(val line: GripLineUi, val point: GripPointUi, val x: Float, val y: Float, val order: Int)

internal class Frame(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val scale: YScale,
    val inset: Float,
) {
    fun y(v: Float) = bottom - (v - scale.min) / (scale.max - scale.min) * (bottom - top)
    val minX get() = left + inset
    val maxX get() = right - inset
    fun x(fx: Float) = minX + fx * (maxX - minX)
}

/** Everything the canvas needs, computed once per size: frame, points, labels and the content width. */
internal class Built(val frame: Frame, val placed: List<PlacedPoint>, val labels: LabelLayout, val contentPx: Float, val scrolls: Boolean)

internal class ChartMetrics(val d: Float, val labelH: Float, val labelPad: Float, val textWidth: (String) -> Float) {
    val dot get() = 4f * d
    val gap get() = 3f * d
    val vertical get() = dot + gap + labelH + 2f * d
}

internal fun scaleOf(lines: List<GripLineUi>): YScale {
    val kgs = lines.flatMap { l -> l.points.map { it.kg } }
    return YScale.of(kgs.minOrNull() ?: 0f, kgs.maxOrNull() ?: 10f)
}

internal fun frameOf(widthPx: Float, heightPx: Float, m: ChartMetrics, scale: YScale, axisBelow: Float): Frame =
    Frame(0f, widthPx, m.vertical, heightPx - axisBelow - m.vertical, scale, m.dot + 2f * m.d)

internal fun pillWidth(m: ChartMetrics, text: String) = maxOf(m.labelH, m.textWidth(text) + 2 * m.labelPad)

internal fun build(lines: List<GripLineUi>, m: ChartMetrics, viewportPx: Float, heightPx: Float, axisBelow: Float, mode: ChartLabelMode): Built {
    val scale = scaleOf(lines)
    val inset = m.dot + 2f * m.d
    val widest = lines.flatMap { l -> l.points.map { pillWidth(m, KgText.of(it.kg)) } }.maxOrNull() ?: m.labelH
    val scroll = ChartScroll.width(viewportPx, inset, lines.flatMap { l -> l.points.map { it.fx } }, widest + 8f * m.d)
    val frame = frameOf(scroll.contentPx, heightPx, m, scale, axisBelow)
    val placed = lines.flatMap { l -> l.points.mapIndexed { i, p -> PlacedPoint(l, p, frame.x(p.fx), frame.y(p.kg), i) } }
    val points = placed.mapIndexed { i, p ->
        val ends = p.order == 0 || p.order == p.line.points.size - 1
        LabelPoint(i, lines.indexOf(p.line), 0, p.point.fx, p.line.right, p.x, p.y, p.point.kg, m.dot, p.order, p.line.points.size, wanted = mode == ChartLabelMode.ALL || ends)
    }
    val spec = LabelSpec(m.labelH, m.gap, 5f * m.d, m.labelPad, 0f, scroll.contentPx, unlimited = scroll.scrolls && mode == ChartLabelMode.ALL, textWidth = m.textWidth)
    val layout = ChartLabels.layout(points, spec)
    val moved = placed.mapIndexed { i, p -> PlacedPoint(p.line, p.point, layout.xs.getValue(i), p.y, p.order) }
    return Built(frame, moved, layout, scroll.contentPx, scroll.scrolls)
}

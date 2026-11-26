package com.rehabgrip.core.domain.progress

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** «31,5» keeps its decimal; «32,0» is just «32». */
object KgText {
    fun of(kg: Float): String {
        val tenths = round(kg * 10).toLong()
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${abs(tenths % 10)}"
    }
}

/** y scale of an evolution chart: from the lower ten to the upper ten, grid every 5 kg, numbers every 10. */
class YScale(val min: Float, val max: Float) {
    class GridLine(val value: Float, val labelled: Boolean)

    fun grid(): List<GridLine> {
        val out = ArrayList<GridLine>()
        var v = min
        while (v <= max + 0.01f) {
            out += GridLine(v, labelled = v.toInt() % 10 == 0)
            v += STEP
        }
        return out
    }

    companion object {
        const val STEP = 5f
        const val MIN_RANGE = 20f

        fun of(minKg: Float, maxKg: Float): YScale {
            val lo = max(0f, floor(minKg / 10f) * 10f)
            var hi = ceil(maxKg / 10f) * 10f
            if (hi - lo < MIN_RANGE) hi = lo + MIN_RANGE
            return YScale(lo, hi)
        }
    }
}

/** A point of a chart as the label placer sees it (all sizes in pixels, y grows downwards). */
class LabelPoint(
    val key: Int,
    val lineId: Int,
    val groupId: Int,
    val dayKey: Float,
    val right: Boolean,
    val x: Float,
    val y: Float,
    val kg: Float,
    val radius: Float,
    val order: Int,
    val count: Int,
    val wanted: Boolean,
)

/** A pill with the kilos; [keys] has two entries when two nearly equal points were fused into «32 · 32». */
class PlacedLabel(
    val keys: List<Int>,
    val text: String,
    val cx: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val above: Boolean,
    val right: Boolean,
    val fused: Boolean,
) {
    fun contains(px: Float, py: Float) = px >= cx - width / 2 && px <= cx + width / 2 && py >= top && py <= top + height
    internal fun overlaps(o: PlacedLabel, pad: Float) =
        cx - width / 2 < o.cx + o.width / 2 + pad && o.cx - o.width / 2 < cx + width / 2 + pad &&
            top < o.top + o.height + pad && o.top < top + height + pad
}

class LabelSpec(
    val height: Float,
    val gap: Float,
    val nudge: Float,
    val padX: Float,
    val minX: Float,
    val maxX: Float,
    val unlimited: Boolean,
    val textWidth: (String) -> Float,
)

/** Where the labels go and the final x of every point (nearly equal points are pushed apart). */
class LabelLayout(val xs: Map<Int, Float>, val labels: List<PlacedLabel>)

/**
 * Kilo labels of the evolution chart. The hand with the higher value of a day puts its label above and the other
 * below, so two hands never collide; nearly equal values are pushed apart and, if still colliding, fused.
 * When space is short, the first and last point of a line always keep their label, then the maximum, then the rest.
 */
object ChartLabels {
    private const val OVERLAP_PAD = 2f

    fun layout(points: List<LabelPoint>, spec: LabelSpec): LabelLayout {
        val xs = points.associate { it.key to it.x }.toMutableMap()
        val pairs = points.groupBy { it.groupId to it.dayKey }.values.mapNotNull { g ->
            val r = g.firstOrNull { it.right }
            val l = g.firstOrNull { !it.right }
            if (r != null && l != null) r to l else null
        }
        pairs.forEach { (r, l) ->
            if (abs(r.y - l.y) < spec.height && abs(r.x - l.x) < 2 * spec.nudge) {
                val mid = (r.x + l.x) / 2f
                xs[r.key] = mid - spec.nudge
                xs[l.key] = mid + spec.nudge
            }
        }
        val partner = HashMap<Int, LabelPoint>()
        pairs.forEach { (r, l) -> partner[r.key] = l; partner[l.key] = r }
        val above = points.associate { p ->
            val o = partner[p.key]
            p.key to when {
                o == null -> !p.right
                p.kg != o.kg -> p.kg > o.kg
                else -> !p.right
            }
        }

        fun box(p: LabelPoint, up: Boolean, x: Float = xs.getValue(p.key), text: String = KgText.of(p.kg), radius: Float = p.radius): PlacedLabel {
            val w = max(spec.height, spec.textWidth(text) + 2 * spec.padX)
            val top = if (up) p.y - radius - spec.gap - spec.height else p.y + radius + spec.gap
            val cx = if (spec.maxX - spec.minX >= w) x.coerceIn(spec.minX + w / 2, spec.maxX - w / 2) else x
            return PlacedLabel(listOf(p.key), text, cx, max(0f, top), w, spec.height, up, p.right, false)
        }

        val maxOfLine = points.groupBy { it.lineId }.mapValues { (_, l) -> l.maxOf { it.kg } }
        fun rank(p: LabelPoint) = when {
            p.order == 0 || p.order == p.count - 1 -> 0
            p.kg >= maxOfLine.getValue(p.lineId) -> 1
            else -> 2
        }

        class Item(val label: PlacedLabel, val rank: Int)
        val items = ArrayList<Item>()
        val fusedKeys = HashSet<Int>()
        pairs.forEach { (r, l) ->
            if (!r.wanted || !l.wanted) return@forEach
            if (!box(r, above.getValue(r.key)).overlaps(box(l, above.getValue(l.key)), 1f)) return@forEach
            val text = "${KgText.of(r.kg)} · ${KgText.of(l.kg)}"
            val top = if (r.y <= l.y) r else l
            val cx = (xs.getValue(r.key) + xs.getValue(l.key)) / 2f
            val b = box(top, true, x = cx, text = text, radius = max(r.radius, l.radius))
            items += Item(PlacedLabel(listOf(r.key, l.key), text, b.cx, b.top, b.width, b.height, true, false, true), min(rank(r), rank(l)))
            fusedKeys += r.key
            fusedKeys += l.key
        }
        points.filter { it.wanted && it.key !in fusedKeys }.forEach { p -> items += Item(box(p, above.getValue(p.key)), rank(p)) }
        val accepted = ArrayList<PlacedLabel>()
        items.sortedBy { it.rank }.forEach { item ->
            if (spec.unlimited || item.rank == 0 || accepted.none { it.overlaps(item.label, OVERLAP_PAD) }) accepted += item.label
        }
        return LabelLayout(xs, accepted)
    }
}

/** Horizontal scroll of an evolution chart: when the days do not fit with legible labels, the plot grows. */
object ChartScroll {
    const val MAX_FACTOR = 8f

    class Result(val contentPx: Float, val scrolls: Boolean)

    /**
     * [fxs] are the 0..1 positions of the days; each needs [minPerDayPx] (label width + 8 dp) between neighbours;
     * [inset] is the room kept at both ends. The width is capped at [MAX_FACTOR] viewports.
     */
    fun width(viewportPx: Float, inset: Float, fxs: List<Float>, minPerDayPx: Float): Result {
        val days = fxs.distinct().sorted()
        val minGap = days.zipWithNext { a, b -> b - a }.minOrNull() ?: return Result(viewportPx, false)
        val needed = minPerDayPx / minGap
        if (needed <= viewportPx - 2 * inset) return Result(viewportPx, false)
        return Result(min(needed + 2 * inset, viewportPx * MAX_FACTOR), true)
    }
}

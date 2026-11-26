package com.rehabgrip.core.domain.progress

import com.rehabgrip.core.domain.model.RecordDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** How the grips of Evolution are drawn: all in one chart, or one chart per grip. */
enum class ChartMode { OVERLAID, SEPARATE }

object ChartModeRules {
    const val MAX_OVERLAID = 8
    const val OVERLAID_FULL = "Máximo 8 en superpuestas; usa Separadas"

    /** Overlaid for one or two grips, separate from three on (the user can change it). */
    fun default(selectedCount: Int) = if (selectedCount <= 2) ChartMode.OVERLAID else ChartMode.SEPARATE

    /** May one more grip be ticked? Separate charts have no limit. */
    fun canAdd(mode: ChartMode, selectedCount: Int) = mode == ChartMode.SEPARATE || selectedCount < MAX_OVERLAID

    fun canSwitchTo(mode: ChartMode, selectedCount: Int) = mode == ChartMode.SEPARATE || selectedCount <= MAX_OVERLAID
}

/** x axis of an evolution chart: from the first record shown up to today. */
class DayAxis(val first: LocalDate, val last: LocalDate) {
    val spanDays: Int get() = last.toEpochDays() - first.toEpochDays()

    /** 0 at the first record, 1 at today; with a single day everything sits at 0 (the right side stays free). */
    fun fraction(d: LocalDate): Float = if (spanDays <= 0) 0f else (d.toEpochDays() - first.toEpochDays()).toFloat() / spanDays

    /** Unique tick dates with their x fraction. */
    fun ticks(): List<Pair<Float, LocalDate>> = AxisTicks.days(first, spanDays).map { fraction(it) to it }

    companion object {
        fun build(dates: List<LocalDate>, nowMillis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): DayAxis? {
            if (dates.isEmpty()) return null
            val today = RecordDate.day(nowMillis, tz)
            return DayAxis(dates.min(), maxOf(today, dates.max()))
        }
    }
}

/** Placement of the points of one day so right and left hands and different grips do not sit on the same vertical. */
object Dodge {
    const val HAND_DP = 14f
    const val GRIP_DP = 56f

    /** Right a bit to the left of the date, left a bit to the right, plus a step per grip; centred on the date. */
    fun offsetDp(right: Boolean, gripIndex: Int, gripCount: Int, handDp: Float = HAND_DP, gripDp: Float = GRIP_DP): Float =
        (if (right) -handDp else handDp) + (gripIndex - (gripCount - 1) / 2f) * gripDp

    /** Absolute x of each offset around [base], the whole group shifted so it stays inside [min]..[max]. */
    fun place(base: Float, offsets: List<Float>, min: Float, max: Float): List<Float> {
        if (offsets.isEmpty()) return emptyList()
        val lo = base + offsets.min()
        val hi = base + offsets.max()
        val shift = when {
            hi - lo > max - min -> (min + max) / 2f - (lo + hi) / 2f
            lo < min -> min - lo
            hi > max -> max - hi
            else -> 0f
        }
        return offsets.map { (base + it + shift).coerceIn(min, max) }
    }

    /** Which point of a line carries the lettered marker: each grip a different one so they do not cover each other. */
    fun markerIndex(gripIndex: Int, pointCount: Int): Int = if (pointCount <= 0) 0 else gripIndex.mod(pointCount)
}

package com.rehabgrip.core.domain.progress

import com.rehabgrip.core.domain.model.RecordDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** x axis of an evolution chart: from the first record shown up to today. */
class DayAxis(val first: LocalDate, val last: LocalDate) {
    val spanDays: Int get() = last.toEpochDays() - first.toEpochDays()

    /** 0 at the first record, 1 at today; with a single day everything sits at 0 (the right side stays free). */
    fun fraction(d: LocalDate): Float = if (spanDays <= 0) 0f else (d.toEpochDays() - first.toEpochDays()).toFloat() / spanDays

    /** Unique tick dates with their x fraction. */
    fun ticks(max: Int = 4): List<Pair<Float, LocalDate>> = AxisTicks.days(first, spanDays, max.coerceAtLeast(2)).map { fraction(it) to it }

    companion object {
        fun build(dates: List<LocalDate>, nowMillis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): DayAxis? {
            if (dates.isEmpty()) return null
            val today = RecordDate.day(nowMillis, tz)
            return DayAxis(dates.min(), maxOf(today, dates.max()))
        }
    }
}

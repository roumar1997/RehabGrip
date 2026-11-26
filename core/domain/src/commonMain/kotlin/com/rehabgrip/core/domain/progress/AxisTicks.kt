package com.rehabgrip.core.domain.progress

import kotlinx.datetime.LocalDate
import kotlin.math.round

/** Date ticks of an evolution chart: unique, evenly spread, at most [max]; a single day gives a single tick. */
object AxisTicks {
    fun days(first: LocalDate, spanDays: Int, max: Int = 4): List<LocalDate> {
        if (spanDays <= 0) return listOf(first)
        val n = minOf(max, spanDays + 1)
        return (0 until n).map { i -> LocalDate.fromEpochDays(first.toEpochDays() + round(spanDays.toDouble() * i / (n - 1)).toInt()) }.distinct()
    }
}

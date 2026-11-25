package com.rehabgrip.core.domain.progress

import com.rehabgrip.core.domain.model.Measurement
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Evolution of one discomfort zone: highest level noted each day. */
data class DiscomfortSeries(val zone: String, val points: List<DayPoint>) {
    val first: Int? get() = points.firstOrNull()?.kg?.toInt()
    val last: Int? get() = points.lastOrNull()?.kg?.toInt()
}

/** Groups discomforts by zone (case-insensitive) across all exercises. */
class DiscomfortCalculator(private val timeZone: TimeZone = TimeZone.currentSystemDefault()) {

    fun series(all: List<Measurement>, range: ProgressRange, nowMillis: Long): List<DiscomfortSeries> {
        val from = range.days?.let { nowMillis - it * ProgressCalculator.DAY_MS } ?: Long.MIN_VALUE
        val notes = all.filter { it.epochMillis in from..nowMillis }
            .flatMap { m -> m.discomforts.map { d -> Triple(d.zone.trim(), m.epochMillis, d.level) } }
        return notes.groupBy { it.first.lowercase() }.map { (_, list) ->
            val points = list.groupBy { Instant.fromEpochMilliseconds(it.second).toLocalDateTime(timeZone).date }
                .map { (date, day) -> DayPoint(date, day.maxOf { it.third }.toDouble()) }
                .sortedBy { it.date }
            DiscomfortSeries(list.maxBy { it.second }.first, points)
        }.sortedByDescending { it.points.last().date }
    }

    /** Zones used most recently first, for quick re-adding. */
    fun recentZones(all: List<Measurement>, limit: Int = 6): List<String> =
        all.sortedByDescending { it.epochMillis }
            .flatMap { m -> m.discomforts.map { it.zone.trim() } }
            .distinctBy { it.lowercase() }
            .take(limit)
}

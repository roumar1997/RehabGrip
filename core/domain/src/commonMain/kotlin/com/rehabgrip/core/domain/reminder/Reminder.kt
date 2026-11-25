package com.rehabgrip.core.domain.reminder

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Weekly training reminder created by the user. */
data class Reminder(
    val id: Long = 0,
    val days: Set<DayOfWeek>,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "invalid time $hour:$minute" }
    }

    /** Next trigger strictly after [fromMillis], or null when no day is selected. */
    fun nextTriggerMillis(fromMillis: Long, zone: TimeZone): Long? {
        if (days.isEmpty()) return null
        val now = Instant.fromEpochMilliseconds(fromMillis).toLocalDateTime(zone)
        for (offset in 0..7) {
            val date = now.date.plus(offset, DateTimeUnit.DAY)
            if (date.dayOfWeek !in days) continue
            val at = date.atTime(LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()
            if (at > fromMillis) return at
        }
        return null
    }

    companion object {
        /** Days encoded as "1,3,5" (ISO numbers) for storage. */
        fun encodeDays(days: Set<DayOfWeek>) = days.map { it.ordinal + 1 }.sorted().joinToString(",")

        fun decodeDays(text: String): Set<DayOfWeek> =
            text.split(',').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }.map { DayOfWeek.entries[it - 1] }.toSet()
    }
}

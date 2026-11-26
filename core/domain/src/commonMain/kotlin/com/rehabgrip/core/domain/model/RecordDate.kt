package com.rehabgrip.core.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The editable date of a record (its `epochMillis`): the day it was trained, which may be
 * a day before it was typed. Never in the future.
 */
object RecordDate {
    private const val FUTURE_SLACK_MS = 60_000L

    fun isFuture(epochMillis: Long, nowMillis: Long) = epochMillis > nowMillis + FUTURE_SLACK_MS

    /** [at] (null = now) kept inside the past: a future value becomes [nowMillis]. */
    fun resolve(at: Long?, nowMillis: Long): Long = (at ?: nowMillis).coerceAtMost(nowMillis)

    /** Same time of day, one calendar day before. */
    fun yesterday(nowMillis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): Long {
        val dt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)
        val day = dt.date.minus(1, DateTimeUnit.DAY)
        return withTimeOf(day, nowMillis, tz)
    }

    /** The picked calendar day (UTC midnight millis, as Material date pickers return) at the current time of day. */
    fun onPickedDay(utcMidnightMillis: Long, nowMillis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): Long {
        val day = Instant.fromEpochMilliseconds(utcMidnightMillis).toLocalDateTime(TimeZone.UTC).date
        return resolve(withTimeOf(day, nowMillis, tz), nowMillis)
    }

    fun day(epochMillis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz).date

    private fun withTimeOf(day: LocalDate, timeSource: Long, tz: TimeZone): Long {
        val start = day.atStartOfDayIn(tz).toEpochMilliseconds()
        val t = Instant.fromEpochMilliseconds(timeSource).toLocalDateTime(tz).time
        return start + (t.hour * 3600L + t.minute * 60L + t.second) * 1000L
    }
}

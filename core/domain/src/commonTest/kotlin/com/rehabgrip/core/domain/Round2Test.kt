package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.DiscomfortZones
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Finger
import com.rehabgrip.core.domain.progress.DiscomfortCalculator
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressCalculator.Companion.DAY_MS
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.reminder.Reminder
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderTest {
    private val utc = TimeZone.UTC

    @Test
    fun nextTriggerSkipsToSelectedDay() {
        // 2026-11-25 is a Wednesday.
        val now = LocalDateTime(2026, 11, 25, 20, 0).toInstant(utc).toEpochMilliseconds()
        val r = Reminder(days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), hour = 19, minute = 30)
        val next = r.nextTriggerMillis(now, utc)!!
        assertEquals(LocalDateTime(2026, 11, 27, 19, 30).toInstant(utc).toEpochMilliseconds(), next)
        assertNull(r.copy(days = emptySet()).nextTriggerMillis(now, utc))
    }

    @Test
    fun daysRoundTrip() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
        assertEquals("1,7", Reminder.encodeDays(days))
        assertEquals(days, Reminder.decodeDays("1,7"))
    }
}

class SymmetryTest {
    @Test
    fun injuredLeftComparedAgainstRight() {
        val now = 50 * DAY_MS
        fun m(day: Int, hand: Hand, kg: Double, d: List<Discomfort> = emptyList()) = Measurement(
            exercise = Exercise.OPEN_HAND, hand = hand, kg = kg, epochMillis = now - day * DAY_MS,
            source = MeasurementSource.MANUAL, discomforts = d,
        )
        val data = listOf(
            m(10, Hand.LEFT, 20.0, listOf(Discomfort("Muñeca izda", 4), Discomfort("Polea A2 anular izda", 2))),
            m(10, Hand.RIGHT, 40.0),
            m(0, Hand.LEFT, 30.0, listOf(Discomfort("muñeca izda", 1))),
            m(0, Hand.RIGHT, 40.0),
        )
        val s = ProgressCalculator(TimeZone.UTC).summarize(data, Exercise.OPEN_HAND, ProgressRange.ALL, now)
        assertEquals(75.0, s.symmetryPercent(Hand.LEFT)!!, 0.01)
        assertEquals(50.0, s.firstSymmetryPercent(Hand.LEFT)!!, 0.01)
        assertEquals(25.0, s.symmetryTrend(Hand.LEFT)!!, 0.01)

        val series = DiscomfortCalculator(TimeZone.UTC).series(data, ProgressRange.ALL, now)
        val wrist = series.first { it.zone.lowercase() == "muñeca izda" }
        assertEquals(4, wrist.first)
        assertEquals(1, wrist.last)
        assertEquals(listOf("muñeca izda", "Polea A2 anular izda"), DiscomfortCalculator().recentZones(data))
    }

    @Test
    fun pulleysPerFinger() {
        assertEquals(5, DiscomfortZones.finger(Hand.RIGHT, Finger.RING).size)
        assertEquals("Polea A2 anular dcha", DiscomfortZones.finger(Hand.RIGHT, Finger.RING)[2])
        assertEquals(3, DiscomfortZones.finger(Hand.LEFT, Finger.THUMB).size)
        assertFailsWith<IllegalArgumentException> { Discomfort("x", 11) }
    }
}

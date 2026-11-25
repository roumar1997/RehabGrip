package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.export.CsvExporter
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.ForceSample
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressCalculator.Companion.DAY_MS
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.session.AttemptTracker
import com.rehabgrip.core.domain.usecase.ManualKgParser
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttemptTrackerTest {
    @Test
    fun splitsTwoPullsAndFindsBest() {
        val t = AttemptTracker(thresholdKg = 2.0)
        listOf(0.0, 5.0, 20.0, 31.0, 10.0, 0.5, 0.0, 8.0, 35.5, 12.0, 1.0)
            .forEachIndexed { i, kg -> t.add(ForceSample(i * 100L, kg)) }
        assertEquals(2, t.attempts.size)
        assertEquals(31.0, t.attempts[0].peakKg)
        assertEquals(35.5, t.attempts[1].peakKg)
        assertEquals(2, t.bestAttemptIndex())
        assertEquals(35.5, t.sessionPeakKg)
    }

    @Test
    fun openAttemptIsVisibleWhilePulling() {
        val t = AttemptTracker()
        t.add(ForceSample(0, 10.0))
        assertTrue(t.isPulling)
        assertEquals(1, t.attempts.size)
    }
}

class ProgressCalculatorTest {
    private val utc = TimeZone.UTC
    private val now = 100 * DAY_MS

    private fun m(day: Int, hand: Hand, kg: Double) =
        Measurement(exercise = Exercise.HALF_CRIMP, hand = hand, kg = kg,
            epochMillis = now - day * DAY_MS, source = MeasurementSource.MANUAL)

    @Test
    fun improvementUsesAvailablePeriodAndDailyMax() {
        val data = listOf(
            m(4, Hand.RIGHT, 20.0), m(4, Hand.RIGHT, 18.0), m(0, Hand.RIGHT, 25.0),
            m(4, Hand.LEFT, 40.0), m(0, Hand.LEFT, 40.0),
        )
        val s = ProgressCalculator(utc).summarize(data, Exercise.HALF_CRIMP, ProgressRange.ALL, now)
        assertEquals(5, s.spanDays)
        assertEquals(25.0, s.right.improvementPercent!!, 0.001)
        assertEquals(0.0, s.left.improvementPercent!!, 0.001)
        assertEquals(62.5, s.rightVsLeftPercent!!, 0.001)
    }

    @Test
    fun rangeFiltersOldData() {
        val data = listOf(m(30, Hand.RIGHT, 10.0), m(3, Hand.RIGHT, 20.0))
        val s = ProgressCalculator(utc).summarize(data, Exercise.HALF_CRIMP, ProgressRange.WEEK, now)
        assertEquals(1, s.right.points.size)
        assertNull(s.right.improvementPercent)
    }
}

class CsvAndParserTest {
    @Test
    fun csvHasHeaderAndDecimalComma() {
        val csv = CsvExporter(TimeZone.UTC).export(
            listOf(Measurement(1, Exercise.DYNAMOMETER, Hand.LEFT, 42.56, 0, MeasurementSource.MANUAL, "a;b")),
        )
        val lines = csv.trim().lines()
        assertEquals("fecha;hora;ejercicio;mano;kg;origen;nota", lines[0])
        assertEquals("1970-01-01;00:00;DYNAMOMETER;LEFT;42,6;MANUAL;\"a;b\"", lines[1])
    }

    @Test
    fun manualParserAcceptsComma() {
        assertEquals(32.5, ManualKgParser.parse(" 32,5 "))
        assertNull(ManualKgParser.parse("abc"))
        assertNull(ManualKgParser.parse("0"))
    }
}

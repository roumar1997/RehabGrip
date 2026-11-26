package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.model.RecordDate
import com.rehabgrip.core.domain.port.MeasurementRepository
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.usecase.UpdateMeasurement
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordDateTest {
    private val utc = TimeZone.UTC
    private val day = 86_400_000L
    private val now = 100 * day + 12 * 3_600_000L // noon of day 100
    private fun m(id: Long, kg: Double, at: Long) =
        Measurement(id, Exercise.HALF_CRIMP, Hand.RIGHT, kg, at, MeasurementSource.MANUAL)

    @Test fun yesterdayKeepsTheTimeOfDay() {
        assertEquals(now - day, RecordDate.yesterday(now, utc))
    }

    @Test fun pickedDayTakesTheCurrentTimeAndNeverTheFuture() {
        assertEquals(95 * day + 12 * 3_600_000L, RecordDate.onPickedDay(95 * day, now, utc))
        assertEquals(now, RecordDate.onPickedDay(105 * day, now, utc))
        assertEquals(now, RecordDate.resolve(now + 5 * day, now))
        assertTrue(RecordDate.isFuture(now + day, now))
        assertFalse(RecordDate.isFuture(now + 1_000, now))
    }

    @Test fun aPastDatedRecordFallsInTheRightRange() {
        val calc = ProgressCalculator(utc)
        val old = m(1, 30.0, now - 20 * day) // typed today, trained 20 days ago
        val week = calc.summarize(listOf(old), Exercise.HALF_CRIMP, ProgressRange.WEEK, now)
        val fourWeeks = calc.summarize(listOf(old), Exercise.HALF_CRIMP, ProgressRange.FOUR_WEEKS, now)
        assertTrue(week.right.points.isEmpty())
        assertEquals(1, fourWeeks.right.points.size)
    }

    @Test fun editingTheDateMovesThePoint() = runTest {
        val calc = ProgressCalculator(utc)
        var stored = m(1, 30.0, now)
        val repo = object : MeasurementRepository {
            override fun observeAll() = flowOf(listOf(stored))
            override suspend fun add(measurement: Measurement) = 1L
            override suspend fun delete(id: Long) = Unit
            override suspend fun update(measurement: Measurement) { stored = measurement }
            override suspend fun replaceAll(measurements: List<Measurement>) = Unit
        }
        val before = calc.summarize(listOf(stored), Exercise.HALF_CRIMP, ProgressRange.ALL, now).right.points.single().date
        UpdateMeasurement(repo)(stored.copy(epochMillis = now - 10 * day), now).getOrThrow()
        val after = calc.summarize(listOf(stored), Exercise.HALF_CRIMP, ProgressRange.ALL, now).right.points.single().date
        assertEquals(10, before.toEpochDays() - after.toEpochDays())
    }

    @Test fun futureDatesAreRejected() = runTest {
        val repo = object : MeasurementRepository {
            override fun observeAll() = flowOf(emptyList<Measurement>())
            override suspend fun add(measurement: Measurement) = 1L
            override suspend fun delete(id: Long) = Unit
            override suspend fun update(measurement: Measurement) = error("must not be stored")
            override suspend fun replaceAll(measurements: List<Measurement>) = Unit
        }
        assertTrue(UpdateMeasurement(repo)(m(1, 30.0, now + 3 * day), now).isFailure)
    }
}

package com.rehabgrip.core.domain.progress

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Range selector of the Progress screen. [days] null = everything recorded. */
enum class ProgressRange(val days: Int?) { WEEK(7), FOUR_WEEKS(28), TWELVE_WEEKS(84), ALL(null) }

data class DayPoint(val date: LocalDate, val kg: Double)

data class HandProgress(
    val hand: Hand,
    val points: List<DayPoint>,
    val firstKg: Double?,
    val lastKg: Double?,
    val bestKg: Double?,
) {
    /** % change from the first to the latest day in range; null with fewer than two days. */
    val improvementPercent: Double?
        get() = if (points.size < 2 || firstKg == null || lastKg == null || firstKg == 0.0) null
        else (lastKg - firstKg) / firstKg * 100.0
}

data class ProgressSummary(
    val exercise: Exercise,
    /** Library grip name when the summary is per grip; null for a plain exercise. */
    val title: String? = null,
    val right: HandProgress,
    val left: HandProgress,
    /** Days actually covered by the data in range (the period the user has been training). */
    val spanDays: Int,
) {
    /** Latest right value as % of latest left value (100 % = symmetry). */
    val rightVsLeftPercent: Double? get() = symmetryPercent(Hand.RIGHT)

    fun of(hand: Hand) = if (hand == Hand.RIGHT) right else left

    /** Latest [focus] value as % of the latest value of the other hand. */
    fun symmetryPercent(focus: Hand): Double? = ratio(of(focus).lastKg, of(focus.other).lastKg)

    /** Symmetry on the first day both hands were measured (start of the trend). */
    fun firstSymmetryPercent(focus: Hand): Double? {
        val other = of(focus.other).points.associateBy { it.date }
        val first = of(focus).points.firstOrNull { it.date in other } ?: return null
        return ratio(first.kg, other.getValue(first.date).kg)
    }

    /** Change in symmetry points since the start (positive = closing the gap). */
    fun symmetryTrend(focus: Hand): Double? {
        val now = symmetryPercent(focus) ?: return null
        val first = firstSymmetryPercent(focus) ?: return null
        return now - first
    }

    private fun ratio(a: Double?, b: Double?): Double? =
        if (a == null || b == null || b == 0.0) null else a / b * 100.0
}

/**
 * Builds the evolution of one exercise. Each day keeps the best value per hand.
 * The range adapts to the data: with 5 days of history, "all" spans 5 days.
 */
class ProgressCalculator(private val timeZone: TimeZone = TimeZone.currentSystemDefault()) {

    fun summarize(
        all: List<Measurement>,
        exercise: Exercise,
        range: ProgressRange,
        nowMillis: Long,
    ): ProgressSummary {
        return build(all, exercise, null, range, nowMillis) { it.exercise == exercise }
    }

    /** Evolution of one library grip (its own evaluations, sessions and, for predefined ones, legacy records). */
    fun summarizeGrip(all: List<Measurement>, grip: GripSpec, range: ProgressRange, nowMillis: Long): ProgressSummary =
        build(all, grip.builtIn ?: Exercise.CUSTOM, grip.label, range, nowMillis) { grip.matches(it) }

    private fun build(
        all: List<Measurement>,
        exercise: Exercise,
        title: String?,
        range: ProgressRange,
        nowMillis: Long,
        belongs: (Measurement) -> Boolean,
    ): ProgressSummary {
        val fromMillis = range.days?.let { nowMillis - it * DAY_MS } ?: Long.MIN_VALUE
        val inRange = all.filter { belongs(it) && it.epochMillis in fromMillis..nowMillis }
        val span = if (inRange.isEmpty()) 0 else {
            val first = inRange.minOf { it.epochMillis }
            ((nowMillis - first) / DAY_MS).toInt() + 1
        }
        return ProgressSummary(
            exercise = exercise,
            title = title,
            right = handProgress(inRange, Hand.RIGHT),
            left = handProgress(inRange, Hand.LEFT),
            spanDays = span,
        )
    }

    private fun handProgress(list: List<Measurement>, hand: Hand): HandProgress {
        val byDay = list.filter { it.hand == hand }
            .groupBy { Instant.fromEpochMilliseconds(it.epochMillis).toLocalDateTime(timeZone).date }
        val points = byDay.map { (date, day) -> DayPoint(date, day.maxOf { it.kg }) }.sortedBy { it.date }
        return HandProgress(
            hand = hand,
            points = points,
            firstKg = points.firstOrNull()?.kg,
            lastKg = points.lastOrNull()?.kg,
            bestKg = points.maxOfOrNull { it.kg },
        )
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

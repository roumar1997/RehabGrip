package com.rehabgrip.core.data

import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import kotlin.random.Random

/**
 * Demo history (~3 weeks, sessions every 2-3 days): the left hand is stable,
 * the injured right hand recovers from ~55 % to ~80 % of the left.
 * Only offered from Profile in debug builds.
 */
object SampleData {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val leftBase = mapOf(
        Exercise.HALF_CRIMP to 41.0,
        Exercise.THREE_FINGER to 33.0,
        Exercise.OPEN_HAND to 36.0,
        Exercise.DYNAMOMETER to 48.0,
    )
    private val sessionDays = listOf(21, 19, 16, 14, 12, 9, 7, 5, 2, 0)

    fun generate(nowMillis: Long, random: Random = Random(7)): List<Measurement> =
        sessionDays.flatMapIndexed { i, daysAgo ->
            val progress = i.toDouble() / (sessionDays.size - 1)
            val at = nowMillis - daysAgo * DAY_MS - (2 * 60 * 60 * 1000L)
            leftBase.flatMap { (exercise, left) ->
                val rightRatio = 0.55 + 0.25 * progress
                listOf(
                    m(exercise, Hand.LEFT, left * (0.98 + random.nextDouble() * 0.05), at),
                    m(exercise, Hand.RIGHT, left * rightRatio * (0.97 + random.nextDouble() * 0.05), at + 60_000, progress),
                )
            }
        }

    private fun m(exercise: Exercise, hand: Hand, kg: Double, at: Long, progress: Double = 0.0) = Measurement(
        exercise = exercise,
        hand = hand,
        kg = kotlin.math.round(kg * 10) / 10.0,
        epochMillis = at,
        source = if (exercise.usesScale) MeasurementSource.SCALE else MeasurementSource.MANUAL,
        discomforts = if (hand == Hand.RIGHT && exercise == Exercise.HALF_CRIMP) sampleDiscomforts(progress) else emptyList(),
    )

    /** Wrist scar settling down and an A2 pulley that stops bothering. */
    private fun sampleDiscomforts(progress: Double): List<Discomfort> = listOfNotNull(
        Discomfort("Cicatriz dcha", (5 - 4 * progress).toInt().coerceIn(0, 10)),
        Discomfort("Polea A2 anular dcha", (3 - 3 * progress).toInt()).takeIf { progress < 0.9 },
    )
}

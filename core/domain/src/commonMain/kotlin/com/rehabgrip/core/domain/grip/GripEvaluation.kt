package com.rehabgrip.core.domain.grip

import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.WorkoutSet

/** One evaluation of a grip for one hand: max, plus min and average of the hold when known. */
data class GripEvaluation(
    val id: Long = 0,
    val gripId: Long,
    val hand: Hand,
    val maxKg: Double,
    val minKg: Double? = null,
    val avgKg: Double? = null,
    val epochMillis: Long,
)

/** Max of a hand for a grip and when it was set. */
data class GripRecord(val kg: Double, val epochMillis: Long, val fromEvaluation: Boolean)

/** Profile row: one grip with the record of each hand. */
data class GripRecordRow(val grip: GripSpec, val right: GripRecord?, val left: GripRecord?) {
    fun of(hand: Hand) = if (hand == Hand.RIGHT) right else left
}

/**
 * Where the «Umbral basado en registro» base comes from. The latest evaluation of that
 * grip and hand wins; for the predefined grips older plain measurements still count.
 */
object GripRecords {
    fun record(evaluations: List<GripEvaluation>, measurements: List<Measurement>, grip: GripSpec, hand: Hand): GripRecord? {
        evaluations.filter { it.gripId == grip.id && it.hand == hand }.maxByOrNull { it.epochMillis }
            ?.let { return GripRecord(it.maxKg, it.epochMillis, fromEvaluation = true) }
        return measurements.filter { it.hand == hand && grip.matches(it) }
            .maxByOrNull { it.epochMillis }?.let { GripRecord(it.kg, it.epochMillis, fromEvaluation = false) }
    }

    fun record(
        evaluations: List<GripEvaluation>,
        measurements: List<Measurement>,
        grip: Grip,
        library: List<GripSpec>,
        hand: Hand,
    ): GripRecord? {
        val spec = when (grip) {
            is Grip.Library -> library.firstOrNull { it.id == grip.grip.id } ?: grip.grip
            is Grip.Standard -> library.firstOrNull { it.builtIn == grip.exercise } ?: return null
        }
        return record(evaluations, measurements, spec, hand)
    }

    fun rows(evaluations: List<GripEvaluation>, measurements: List<Measurement>, library: List<GripSpec>): List<GripRecordRow> =
        library.map { g -> GripRecordRow(g, record(evaluations, measurements, g, Hand.RIGHT), record(evaluations, measurements, g, Hand.LEFT)) }

    /** Grips of sets with a «% del registro» target for which some of [hands] has no base yet. */
    fun missingBases(sets: List<WorkoutSet>, hands: List<Hand>, recordKg: (Hand, Grip) -> Double?): List<GripSpec> =
        sets.filter { it.target is SetTarget.PercentOfRecord }
            .mapNotNull { s -> (s.grip as? Grip.Library)?.grip?.takeIf { hands.any { h -> recordKg(h, s.grip) == null } } }
            .distinctBy { it.id }
}

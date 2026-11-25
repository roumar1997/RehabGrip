package com.rehabgrip.core.domain.grip

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.workout.GripSpec

/** Best value of each hand for one thing (a library grip or the hand dynamometer). */
data class MaxRow(val label: String, val grip: GripSpec?, val right: GripRecord?, val left: GripRecord?)

/** Everything one friend pulled, by grip and hand. */
data class FriendMaxes(val person: String, val rows: List<MaxRow>)

/**
 * «Mis máximos» and «Registros de amigos»: the best (not the latest) kg per grip and hand.
 * Own measurements include evaluations and meter saves; friends never mix with them.
 */
object MaxRecords {
    private fun best(list: List<Measurement>, hand: Hand): GripRecord? =
        list.filter { it.hand == hand }.maxByOrNull { it.kg }?.let { GripRecord(it.kg, it.epochMillis, fromEvaluation = false) }

    private fun rows(measurements: List<Measurement>, library: List<GripSpec>): List<MaxRow> {
        val perGrip = library.map { g ->
            val own = measurements.filter { g.matches(it) }
            MaxRow(g.label, g, best(own, Hand.RIGHT), best(own, Hand.LEFT))
        }
        val dyn = measurements.filter { it.exercise == Exercise.DYNAMOMETER }
        return perGrip + MaxRow("Dinamómetro de mano", null, best(dyn, Hand.RIGHT), best(dyn, Hand.LEFT))
    }

    /** [measurements] may contain friends' rows: they are ignored here. */
    fun mine(measurements: List<Measurement>, library: List<GripSpec>): List<MaxRow> =
        rows(measurements.filter { it.isMine }, library)

    /** One entry per friend (most recently seen first); rows without any value are dropped. */
    fun friends(measurements: List<Measurement>, library: List<GripSpec>): List<FriendMaxes> =
        measurements.filter { !it.isMine }.groupBy { it.person.trim() }
            .entries.sortedByDescending { e -> e.value.maxOf { it.epochMillis } }
            .map { (person, list) -> FriendMaxes(person, rows(list, library).filter { it.right != null || it.left != null }) }

    /** Names used before, most recent first, for the «¿Quién?» suggestions. */
    fun recentPeople(measurements: List<Measurement>, limit: Int = 6): List<String> =
        measurements.filter { !it.isMine }.sortedByDescending { it.epochMillis }
            .map { it.person.trim() }.distinct().take(limit)
}

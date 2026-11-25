package com.rehabgrip.core.domain.workout

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand

/** Target band in kg shown on the live graph. */
data class TargetZone(val minKg: Double?, val maxKg: Double?) {
    init {
        if (minKg != null && maxKg != null) require(minKg < maxKg) { "min must be below max" }
    }

    fun statusOf(kg: Double): ZoneStatus = when {
        maxKg != null && kg > maxKg -> ZoneStatus.ABOVE
        minKg != null && kg < minKg -> ZoneStatus.BELOW
        else -> ZoneStatus.IN
    }
}

enum class ZoneStatus { BELOW, IN, ABOVE }

/** One hand only, both one after the other, or alternating pulls (FREZ). */
enum class HandMode { SINGLE, BOTH_SEQUENTIAL, ALTERNATING }

/** What the set aims for. */
sealed interface SetTarget {
    /** Pull as hard as possible; no band. */
    data object Peak : SetTarget

    data class Kg(val minKg: Double?, val maxKg: Double?) : SetTarget

    /** «Umbral basado en registro»: % of the evaluated max of that hand and grip. */
    data class PercentOfRecord(val minPercent: Int?, val maxPercent: Int?) : SetTarget

    /** Null when there is no usable band (none set, inverted, or no record yet). */
    fun resolve(recordKg: Double?): TargetZone? = when (this) {
        Peak -> null
        is Kg -> zoneOrNull(minKg, maxKg)
        is PercentOfRecord -> recordKg?.takeIf { it > 0 }?.let { r ->
            zoneOrNull(minPercent?.let { r * it / 100.0 }, maxPercent?.let { r * it / 100.0 })
        }
    }

    /** Problem text for the editor, or null when the band is valid. */
    fun problem(): String? {
        val (min, max) = when (this) {
            Peak -> return null
            is Kg -> minKg to maxKg
            is PercentOfRecord -> minPercent?.toDouble() to maxPercent?.toDouble()
        }
        return when {
            min == null && max == null -> "pon mínimo o máximo"
            min != null && max != null && min >= max -> "el mínimo debe ser menor que el máximo"
            else -> null
        }
    }

    private fun zoneOrNull(min: Double?, max: Double?): TargetZone? =
        if ((min == null && max == null) || (min != null && max != null && min >= max)) null else TargetZone(min, max)
}

/** «Hasta el fallo». */
enum class ToFailure {
    /** Do the configured reps and time. */
    OFF,

    /** Keep adding reps until one cannot be held for the full work time. */
    REPS,

    /** Each rep lasts until force drops below the stop threshold. */
    TIME,
}

/** How a rep starts: on schedule, or when force passes the start threshold. */
enum class StartMode { TIME, WEIGHT }

/** Session-level choice; RIGHT/LEFT pins this set to one hand. */
enum class SetHand { SESSION, RIGHT, LEFT }

/** One row of the session table: #, REPS, WORK, REST, GRIP. */
data class WorkoutSet(
    val reps: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val grip: Grip = Grip.Standard(Exercise.HALF_CRIMP),
    val target: SetTarget = SetTarget.Peak,
    val toFailure: ToFailure = ToFailure.OFF,
    val startMode: StartMode = StartMode.TIME,
    val thresholdKg: Double = DEFAULT_THRESHOLD_KG,
    val hand: SetHand = SetHand.SESSION,
) {
    companion object {
        const val DEFAULT_THRESHOLD_KG = 2.0
    }
}

data class WorkoutTemplate(
    val id: Long = 0,
    val name: String,
    val sets: List<WorkoutSet>,
    val handMode: HandMode = HandMode.SINGLE,
    /** First hand; the other one is used by BOTH_SEQUENTIAL / ALTERNATING. */
    val firstHand: Hand = Hand.RIGHT,
) {
    fun validate(): List<String> = buildList {
        if (name.isBlank()) add("Ponle un nombre")
        if (sets.isEmpty()) add("Añade al menos una serie")
        sets.forEachIndexed { i, s ->
            val n = i + 1
            if (s.reps !in 1..50) add("Serie $n: repeticiones entre 1 y 50")
            if (s.workSeconds !in 1..300) add("Serie $n: tirar entre 1 y 300 s")
            if (s.restSeconds !in 0..1800) add("Serie $n: descanso entre 0 y 1800 s")
            if (s.thresholdKg !in 0.5..50.0) add("Serie $n: umbral entre 0,5 y 50 kg")
            s.target.problem()?.let { add("Serie $n: $it") }
        }
    }

    companion object {
        /** Example like the FREZ captures: 6 sets, 6-6-2-2-2-2 reps, 10 s on / 20 s off. */
        fun example() = WorkoutTemplate(
            name = "Repeticiones 10/20",
            sets = listOf(6, 6, 2, 2, 2, 2).map { WorkoutSet(reps = it, workSeconds = 10, restSeconds = 20) },
        )
    }
}

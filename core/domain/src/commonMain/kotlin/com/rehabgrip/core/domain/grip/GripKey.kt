package com.rehabgrip.core.domain.grip

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.LocalDate

/**
 * THE key that says which grip a record belongs to. Home, Evolution, «Mis máximos» and
 * History all use it, so no screen can read a record somewhere else than another one:
 * 1. the library grip of its `gripId`;
 * 2. else (legacy record, or the grip was deleted) the library grip predefined for its exercise;
 * 3. else the plain exercise (hand dynamometer...).
 */
sealed interface GripKey {
    data class Library(val id: Long) : GripKey
    data class Plain(val exercise: Exercise) : GripKey

    companion object {
        fun of(m: Measurement, library: List<GripSpec>): GripKey {
            m.gripId?.let { id -> library.firstOrNull { it.id == id }?.let { return Library(it.id) } }
            if (m.exercise != Exercise.CUSTOM) {
                library.firstOrNull { it.builtIn == m.exercise }?.let { return Library(it.id) }
            }
            return Plain(m.exercise)
        }

        /** Name shown for the record everywhere. */
        fun label(m: Measurement, library: List<GripSpec>, plainName: (Exercise) -> String): String =
            when (val k = of(m, library)) {
                is Library -> library.first { it.id == k.id }.label
                is Plain -> plainName(k.exercise)
            }

        /** The «exercise» a record counts for on Home (a predefined grip stands for its exercise). */
        fun exerciseOf(m: Measurement, library: List<GripSpec>): Exercise =
            (of(m, library) as? Library)?.let { k -> library.first { it.id == k.id }.builtIn } ?: m.exercise
    }
}

/** One hand's value inside a [HistoryGroup]. */
data class HandEntries(val hand: Hand, val records: List<Measurement>) {
    val best: Measurement? get() = records.maxByOrNull { it.kg }
}

/** History row: one day and one grip with the records of both hands. */
data class HistoryGroup(val day: LocalDate, val key: GripKey, val label: String, val right: HandEntries, val left: HandEntries) {
    val all: List<Measurement> get() = right.records + left.records
    val latest: Long get() = all.maxOf { it.epochMillis }
    fun of(hand: Hand) = if (hand == Hand.RIGHT) right else left
}

object HistoryGroups {
    /** Newest day first; inside a day, most recent grip first. */
    fun build(
        measurements: List<Measurement>,
        library: List<GripSpec>,
        plainName: (Exercise) -> String,
        tz: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault(),
    ): List<HistoryGroup> =
        measurements.groupBy { GripKey.of(it, library) to com.rehabgrip.core.domain.model.RecordDate.day(it.epochMillis, tz) }
            .map { (k, list) ->
                HistoryGroup(
                    day = k.second, key = k.first, label = GripKey.label(list.first(), library, plainName),
                    right = HandEntries(Hand.RIGHT, list.filter { it.hand == Hand.RIGHT }.sortedByDescending { it.epochMillis }),
                    left = HandEntries(Hand.LEFT, list.filter { it.hand == Hand.LEFT }.sortedByDescending { it.epochMillis }),
                )
            }
            .sortedWith(compareByDescending<HistoryGroup> { it.day }.thenByDescending { it.latest })
}

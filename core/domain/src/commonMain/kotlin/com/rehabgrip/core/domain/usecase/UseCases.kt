package com.rehabgrip.core.domain.usecase

import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.port.MeasurementRepository

/** Validates and stores one measurement. */
class SaveMeasurement(private val repository: MeasurementRepository) {
    suspend operator fun invoke(
        exercise: Exercise,
        hand: Hand,
        kg: Double,
        source: MeasurementSource,
        epochMillis: Long,
        note: String = "",
        discomforts: List<Discomfort> = emptyList(),
        gripId: Long? = null,
        person: String = "",
    ): Result<Long> {
        if (kg <= 0.0 || kg > MAX_KG) return Result.failure(IllegalArgumentException("kg out of range: $kg"))
        val id = repository.add(
            Measurement(
                exercise = exercise,
                hand = hand,
                kg = kg,
                epochMillis = epochMillis,
                source = source,
                note = note.trim(),
                gripId = gripId,
                person = person.trim(),
                // One entry per zone: the last level given wins.
                discomforts = discomforts.reversed().distinctBy { it.zone.trim().lowercase() }.reversed(),
            ),
        )
        return Result.success(id)
    }

    companion object {
        /** Same sanity cap used by hang-ten for WH-C06 false positives. */
        const val MAX_KG = 300.0
    }
}

/** Parses user-typed kilograms, accepting comma or dot. */
object ManualKgParser {
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= SaveMeasurement.MAX_KG }
}

/** Edits a stored measurement (date, hand, value, grip); the date can never be in the future. */
class UpdateMeasurement(private val repository: MeasurementRepository) {
    suspend operator fun invoke(measurement: Measurement, nowMillis: Long): Result<Unit> {
        if (measurement.id == 0L) return Result.failure(IllegalArgumentException("not stored yet"))
        if (measurement.kg <= 0.0 || measurement.kg > SaveMeasurement.MAX_KG) return Result.failure(IllegalArgumentException("kg out of range"))
        if (com.rehabgrip.core.domain.model.RecordDate.isFuture(measurement.epochMillis, nowMillis)) {
            return Result.failure(IllegalArgumentException("La fecha no puede ser futura"))
        }
        repository.update(measurement)
        return Result.success(Unit)
    }
}

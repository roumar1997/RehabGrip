package com.rehabgrip.core.domain.usecase

import com.rehabgrip.core.domain.grip.GripEvaluation
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.port.GripEvaluationRepository
import com.rehabgrip.core.domain.port.MeasurementRepository
import com.rehabgrip.core.domain.workout.GripSpec

/**
 * Stores the evaluation of one grip and hand (max, plus min/average of the hold when
 * known). The max is also saved as a measurement so it feeds Evolution and History.
 */
class SaveGripEvaluation(
    private val evaluations: GripEvaluationRepository,
    private val measurements: MeasurementRepository,
) {
    suspend operator fun invoke(
        grip: GripSpec,
        hand: Hand,
        maxKg: Double,
        minKg: Double?,
        avgKg: Double?,
        source: MeasurementSource,
        epochMillis: Long,
    ): Result<Long> {
        val problem = problem(maxKg, minKg, avgKg)
        if (problem != null) return Result.failure(IllegalArgumentException(problem))
        val id = evaluations.add(GripEvaluation(0, grip.id, hand, maxKg, minKg, avgKg, epochMillis))
        measurements.add(
            Measurement(
                exercise = grip.builtIn ?: Exercise.CUSTOM,
                hand = hand,
                kg = maxKg,
                epochMillis = epochMillis,
                source = source,
                note = "Evaluación · ${grip.label}",
                gripId = grip.id,
            ),
        )
        return Result.success(id)
    }

    /** Why the values cannot be stored, or null. */
    fun problem(maxKg: Double, minKg: Double?, avgKg: Double?): String? = when {
        maxKg <= 0.0 || maxKg > SaveMeasurement.MAX_KG -> "El máximo debe estar entre 0 y ${SaveMeasurement.MAX_KG.toInt()} kg"
        minKg != null && (minKg < 0.0 || minKg > maxKg) -> "El mínimo no puede superar al máximo"
        avgKg != null && (avgKg < (minKg ?: 0.0) || avgKg > maxKg) -> "La media debe quedar entre el mínimo y el máximo"
        else -> null
    }
}

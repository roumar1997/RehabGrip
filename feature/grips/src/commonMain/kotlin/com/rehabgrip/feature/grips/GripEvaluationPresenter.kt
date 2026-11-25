package com.rehabgrip.feature.grips

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.domain.grip.HoldStats
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
import com.rehabgrip.core.domain.model.Hand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Value measured (or typed) for one hand. */
data class HandEvaluation(val maxKg: Double, val minKg: Double?, val avgKg: Double?, val fromScale: Boolean)

data class EvaluationState(
    val sourceName: String,
    val status: ScaleStatus = ScaleStatus.IDLE,
    /** Hand being measured right now, or null. */
    val measuring: Hand? = null,
    val currentKg: Double = 0.0,
    val peakKg: Double = 0.0,
    val results: Map<Hand, HandEvaluation> = emptyMap(),
    val message: String? = null,
)

/**
 * Evaluation flow of one grip: measure each hand with the scale (max, plus min and
 * average of the hold) or type the values by hand. UI-agnostic.
 */
class GripEvaluationPresenter(
    private val source: ForceSource,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EvaluationState(sourceName = source.name))
    val state: StateFlow<EvaluationState> = _state.asStateFlow()
    /** Live graph samples (drawn without recomposing). */
    val trace = LiveForceTrace()
    private val buffer = mutableListOf<Double>()
    private var jobs: List<Job> = emptyList()

    fun start() {
        source.start()
        jobs = listOf(
            scope.launch { source.status.collect { s -> _state.update { it.copy(status = s) } } },
            scope.launch { source.readings.collect { onReading(it.kg) } },
        )
    }

    private fun onReading(kg: Double) {
        trace.add(kg)
        val s = _state.value
        if (s.measuring == null) {
            _state.update { it.copy(currentKg = kg) }
            return
        }
        buffer += kg
        _state.update {
            it.copy(currentKg = kg, peakKg = maxOf(it.peakKg, kg))
        }
    }

    fun beginMeasure(hand: Hand) {
        buffer.clear()
        _state.update { it.copy(measuring = hand, peakKg = 0.0, message = null) }
        trace.clear()
        source.setEffort(true, if (hand == Hand.RIGHT) SIM_RIGHT_STRENGTH else 1.0)
    }

    /** Ends the measurement and keeps the hold stats of the hand, if a pull was detected. */
    fun finishMeasure() {
        val hand = _state.value.measuring ?: return
        source.setEffort(false)
        val stats = HoldStats.from(buffer)?.takeIf { it.maxKg >= MIN_VALID_KG }
        _state.update {
            it.copy(
                measuring = null,
                results = if (stats != null) it.results + (hand to HandEvaluation(stats.maxKg, stats.minKg, stats.avgKg, fromScale = true)) else it.results,
                message = if (stats == null) "No se detectó ningún tirón. Inténtalo otra vez." else null,
            )
        }
    }

    fun setManual(hand: Hand, maxKg: Double, minKg: Double?, avgKg: Double?) =
        _state.update { it.copy(results = it.results + (hand to HandEvaluation(maxKg, minKg, avgKg, fromScale = false)), message = null) }

    fun clear(hand: Hand) = _state.update { it.copy(results = it.results - hand) }

    fun dispose() {
        jobs.forEach { it.cancel() }
        source.setEffort(false)
        source.stop()
    }

    private companion object {
        const val MIN_VALID_KG = 1.0
        const val SIM_RIGHT_STRENGTH = 0.85
    }
}

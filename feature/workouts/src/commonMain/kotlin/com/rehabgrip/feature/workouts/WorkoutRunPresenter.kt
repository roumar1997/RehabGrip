package com.rehabgrip.feature.workouts

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.domain.model.ForceSample
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.port.CuePlayer
import com.rehabgrip.core.domain.workout.EngineEvent
import com.rehabgrip.core.domain.workout.EnginePhase
import com.rehabgrip.core.domain.workout.EngineSnapshot
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.HandWorkoutResult
import com.rehabgrip.core.domain.workout.WorkoutEngine
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import com.rehabgrip.core.domain.workout.ZoneStats
import com.rehabgrip.core.domain.workout.ZoneStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

data class RunState(
    val snapshot: EngineSnapshot,
    val status: ScaleStatus = ScaleStatus.IDLE,
    val sourceName: String = "",
    val currentKg: Double = 0.0,
    val samples: List<ForceSample> = emptyList(),
    val zoneStatus: ZoneStatus = ZoneStatus.IN,
    val paused: Boolean = false,
    val totalSlots: Int = 0,
    val results: List<HandWorkoutResult> = emptyList(),
)

/**
 * Drives a guided session: feeds the [WorkoutEngine] with the latest force every
 * tick, plays cues and accumulates [ZoneStats]. Works with the real or simulated scale.
 */
class WorkoutRunPresenter(
    private val template: WorkoutTemplate,
    private val source: ForceSource,
    private val cues: CuePlayer,
    recordKg: (Hand, Grip) -> Double?,
    private val scope: CoroutineScope,
) {
    private val engine = WorkoutEngine(template, recordKg)
    private val stats = ZoneStats()
    private val clock = TimeSource.Monotonic.markNow()
    private var latestKg = 0.0
    private var lastAbove = 0L
    private var lastBelow = 0L
    private val totalSlots = WorkoutEngine.buildSlots(template).size
    private val _state = MutableStateFlow(RunState(engine.snapshot(), sourceName = source.name, totalSlots = totalSlots))
    val state: StateFlow<RunState> = _state.asStateFlow()
    private val jobs = mutableListOf<Job>()

    fun start() {
        source.start()
        jobs += scope.launch { source.status.collect { s -> _state.update { it.copy(status = s) } } }
        jobs += scope.launch { source.readings.collect { latestKg = it.kg } }
        jobs += scope.launch {
            var last = clock.elapsedNow().inWholeMilliseconds
            while (isActive) {
                delay(TICK_MS)
                val now = clock.elapsedNow().inWholeMilliseconds
                val dt = now - last
                last = now
                if (!_state.value.paused) step(now, dt)
            }
        }
    }

    private fun step(now: Long, dt: Long) {
        val kg = latestKg
        val events = engine.tick(dt, kg)
        val snap = engine.snapshot()
        val pulling = snap.phase == EnginePhase.PULL
        val zone = snap.zone
        val sample = ForceSample(now, kg)
        snap.slot?.let { stats.add(sample, it.hand, pulling, zone) }
        events.forEach { e ->
            when (e) {
                is EngineEvent.CountdownTick -> cues.countdownTick()
                is EngineEvent.PullStarted -> cues.pullStart()
                is EngineEvent.PullEnded -> {
                    cues.pullEnd()
                    if (!e.result.skipped) stats.repDone(e.result.slot.hand)
                }
                is EngineEvent.TurnSkipped -> Unit
                EngineEvent.Finished -> cues.finished()
            }
        }
        val zs = if (pulling && zone != null) zone.statusOf(kg) else ZoneStatus.IN
        if (pulling && zs == ZoneStatus.ABOVE && now - lastAbove > ABOVE_EVERY_MS) {
            cues.aboveMax()
            lastAbove = now
        }
        if (pulling && zs == ZoneStatus.BELOW && kg > 0.5 && now - lastBelow > BELOW_EVERY_MS) {
            cues.belowMin()
            lastBelow = now
        }
        _state.update {
            it.copy(
                snapshot = snap,
                currentKg = kg,
                samples = (it.samples + sample).takeLast(MAX_SAMPLES),
                zoneStatus = zs,
                results = stats.results(),
            )
        }
    }

    fun togglePause() = _state.update { it.copy(paused = !it.paused) }

    fun skipTurn() {
        engine.skipTurn()
        _state.update { it.copy(snapshot = engine.snapshot()) }
    }

    fun results(): List<HandWorkoutResult> = stats.results()

    fun dispose() {
        jobs.forEach { it.cancel() }
        source.stop()
    }

    private companion object {
        const val TICK_MS = 50L
        const val MAX_SAMPLES = 400
        const val ABOVE_EVERY_MS = 700L
        const val BELOW_EVERY_MS = 2_000L
    }
}

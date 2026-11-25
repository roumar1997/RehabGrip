package com.rehabgrip.feature.workouts

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.SignalInfo
import com.rehabgrip.core.ble.SignalMonitor
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
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
import kotlin.math.round
import kotlin.time.TimeSource

data class RunState(
    val snapshot: EngineSnapshot,
    val signal: SignalInfo = SignalInfo(),
    val sourceName: String = "",
    /** The simulated scale may start without readings; the real one needs them. */
    val simulated: Boolean = false,
    /** False until the user taps EMPEZAR; the 5 s «Prepárate» countdown then runs by itself. */
    val started: Boolean = false,
    /** Rounded to 0.1 kg and published at most ~10 times per second. */
    val currentKg: Double = 0.0,
    val zoneStatus: ZoneStatus = ZoneStatus.IN,
    val paused: Boolean = false,
    val totalSlots: Int = 0,
    val results: List<HandWorkoutResult> = emptyList(),
) {
    val canStart: Boolean get() = simulated || signal.connected
}

/**
 * Drives a guided session: feeds the [WorkoutEngine] with the latest force every
 * tick, plays cues and accumulates [ZoneStats]. The graph samples go to [trace] and
 * never through the state, so the screen is not recomposed per sample.
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
    private val monitor = SignalMonitor(source, scope)
    private var latestKg = 0.0
    private var lastAbove = 0L
    private var lastBelow = 0L
    private var lastEmit = 0L
    private var lastTick = 0L
    private val totalSlots = WorkoutEngine.buildSlots(template).size
    private val _state = MutableStateFlow(
        RunState(engine.snapshot(), sourceName = source.name, simulated = source.isSimulated, totalSlots = totalSlots),
    )
    val state: StateFlow<RunState> = _state.asStateFlow()
    private val jobs = mutableListOf<Job>()

    /** Live graph samples (drawn without recomposing). */
    val trace = LiveForceTrace()

    fun start() {
        source.start()
        monitor.start()
        jobs += scope.launch { monitor.state.collect { s -> _state.update { it.copy(signal = s) } } }
        jobs += scope.launch { source.readings.collect { latestKg = it.kg; trace.add(it.kg) } }
        jobs += scope.launch {
            lastTick = nowMs()
            while (isActive) {
                delay(TICK_MS)
                val now = nowMs()
                val dt = now - lastTick
                lastTick = now
                val s = _state.value
                if (s.started && !s.paused) step(now, dt)
            }
        }
    }

    private fun nowMs() = clock.elapsedNow().inWholeMilliseconds

    /** EMPEZAR: only with the scale connected (or the simulated one). The 5 s countdown starts now. */
    fun begin() {
        val s = _state.value
        if (s.started || !s.canStart) return
        lastTick = nowMs()
        _state.update { it.copy(started = true) }
    }

    private fun step(now: Long, dt: Long) {
        val kg = latestKg
        val events = engine.tick(dt, kg)
        val snap = engine.snapshot()
        val pulling = snap.phase == EnginePhase.PULL
        // Only the simulated scale reacts: it pulls with the workout and rests with it.
        source.setEffort(
            active = pulling || snap.phase == EnginePhase.WAITING,
            strength = if (snap.slot?.hand == Hand.RIGHT) SIM_RIGHT_STRENGTH else 1.0,
        )
        val zone = snap.zone
        snap.slot?.let { stats.add(ForceSample(now, kg), it.hand, pulling, zone) }
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
        // The screen sees ~10 states per second, or right away when something changes.
        val prev = _state.value.snapshot
        val changed = events.isNotEmpty() || prev.phase != snap.phase || prev.slot != snap.slot
        if (!changed && now - lastEmit < EMIT_EVERY_MS) return
        lastEmit = now
        _state.update {
            it.copy(
                snapshot = snap,
                currentKg = round(kg * 10) / 10,
                zoneStatus = zs,
                results = stats.results(),
            )
        }
    }

    /** Pause is available at any time, also during the countdown. */
    fun togglePause() = _state.update { it.copy(paused = !it.paused) }

    fun skipTurn() {
        engine.skipTurn()
        _state.update { it.copy(snapshot = engine.snapshot()) }
    }

    fun results(): List<HandWorkoutResult> = stats.results()

    fun dispose() {
        jobs.forEach { it.cancel() }
        monitor.stop()
        source.stop()
    }

    private companion object {
        const val TICK_MS = 50L
        const val EMIT_EVERY_MS = 100L
        const val SIM_RIGHT_STRENGTH = 0.85
        const val ABOVE_EVERY_MS = 700L
        const val BELOW_EVERY_MS = 2_000L
    }
}

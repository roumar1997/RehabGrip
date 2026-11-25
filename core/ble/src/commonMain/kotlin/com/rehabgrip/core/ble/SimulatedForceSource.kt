package com.rehabgrip.core.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fake WH-C06 for emulators: 10 Hz, repeated pulls (rest -> ramp -> hold with
 * fatigue and tremor -> release), each a bit weaker than the previous one.
 */
class SimulatedForceSource(
    private val peakKg: Double = 36.0,
    private val random: Random = Random.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ForceSource {
    override val name = "Báscula simulada"
    private val _status = MutableStateFlow(ScaleStatus.IDLE)
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64)
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        _status.value = ScaleStatus.SCANNING
        job = scope.launch {
            delay(CONNECT_DELAY_MS)
            _status.value = ScaleStatus.RECEIVING
            var tick = 0L
            while (isActive) {
                _readings.emit(ScaleReading(valueAt(tick * PERIOD_MS), stable = false))
                tick++
                delay(PERIOD_MS)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _status.value = ScaleStatus.IDLE
    }

    /** Force profile at [t] ms since the stream began. */
    internal fun valueAt(t: Long): Double {
        val cycle = t / CYCLE_MS
        val inCycle = t % CYCLE_MS
        val peak = peakKg * (1.0 - FATIGUE * (cycle % 4)) * (0.96 + random.nextDouble() * 0.06)
        val base = when {
            inCycle < REST_MS -> 0.0
            inCycle < REST_MS + RAMP_MS -> peak * easeOut((inCycle - REST_MS).toDouble() / RAMP_MS)
            inCycle < REST_MS + RAMP_MS + HOLD_MS -> {
                val h = (inCycle - REST_MS - RAMP_MS).toDouble() / HOLD_MS
                peak * (1.0 - 0.12 * h * h) * (1.0 + 0.02 * sin(h * PI * 6))
            }
            inCycle < CYCLE_MS -> {
                val r = (inCycle - REST_MS - RAMP_MS - HOLD_MS).toDouble() / RELEASE_MS
                peak * 0.88 * (1.0 - r)
            }
            else -> 0.0
        }
        val noise = if (base > 0.0) (random.nextDouble() - 0.5) * 0.4 else 0.0
        return (base + noise).coerceAtLeast(0.0)
    }

    private fun easeOut(x: Double) = 1.0 - (1.0 - x) * (1.0 - x)

    private companion object {
        const val PERIOD_MS = 100L
        const val CONNECT_DELAY_MS = 900L
        const val REST_MS = 1_500L
        const val RAMP_MS = 700L
        const val HOLD_MS = 5_000L
        const val RELEASE_MS = 500L
        const val CYCLE_MS = REST_MS + RAMP_MS + HOLD_MS + RELEASE_MS + 1_300L
        const val FATIGUE = 0.04
    }
}

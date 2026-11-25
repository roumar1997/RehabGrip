package com.rehabgrip.core.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

enum class SignalState { CONNECTING, SEARCHING, CONNECTED, LOST, BLUETOOTH_OFF, UNAUTHORIZED }

/** What the user is told about the scale: state plus how many readings per second arrive. */
data class SignalInfo(val state: SignalState = SignalState.CONNECTING, val readingsPerSecond: Int = 0) {
    val connected: Boolean get() = state == SignalState.CONNECTED

    fun label(): String = when (state) {
        SignalState.CONNECTING -> "Conectando con la báscula…"
        SignalState.SEARCHING -> "Buscando báscula…"
        SignalState.CONNECTED -> "Báscula conectada · $readingsPerSecond lecturas/s"
        SignalState.LOST -> "Señal perdida, reintentando…"
        SignalState.BLUETOOTH_OFF -> "Activa el Bluetooth"
        SignalState.UNAUTHORIZED -> "Falta permiso de Bluetooth"
    }

    companion object {
        /** «Buscando señal» only appears after this long without a valid advertisement. */
        const val SEARCH_AFTER_MS = 3_000L

        /**
         * [sinceStartMs]: time since the scan started. [sinceLastReadingMs]: null when no
         * valid reading has arrived yet.
         */
        fun evaluate(status: ScaleStatus, sinceStartMs: Long, sinceLastReadingMs: Long?, rate: Int): SignalInfo = when {
            status == ScaleStatus.BLUETOOTH_OFF -> SignalInfo(SignalState.BLUETOOTH_OFF)
            status == ScaleStatus.UNAUTHORIZED -> SignalInfo(SignalState.UNAUTHORIZED)
            sinceLastReadingMs != null && sinceLastReadingMs < SEARCH_AFTER_MS -> SignalInfo(SignalState.CONNECTED, rate)
            sinceLastReadingMs != null || status == ScaleStatus.LOST -> SignalInfo(SignalState.LOST)
            sinceStartMs < SEARCH_AFTER_MS -> SignalInfo(SignalState.CONNECTING)
            else -> SignalInfo(SignalState.SEARCHING)
        }
    }
}

/**
 * Turns a [ForceSource] into a [SignalInfo]: counts readings per second and applies the
 * 3 s rule. Does not start or stop the source.
 */
class SignalMonitor(private val source: ForceSource, private val scope: CoroutineScope) {
    private val mark = TimeSource.Monotonic.markNow()
    private val _state = MutableStateFlow(SignalInfo())
    val state: StateFlow<SignalInfo> = _state.asStateFlow()
    private val stamps = LongArray(RING)
    private var count = 0
    private var lastReadingAt = -1L
    private var status = ScaleStatus.IDLE
    private var jobs: List<Job> = emptyList()

    private fun nowMs() = mark.elapsedNow().inWholeMilliseconds

    fun start() {
        jobs.forEach { it.cancel() }
        jobs = listOf(
            scope.launch { source.status.collect { status = it; publish() } },
            scope.launch {
                source.readings.collect {
                    val t = nowMs()
                    stamps[count % RING] = t
                    count++
                    lastReadingAt = t
                }
            },
            scope.launch {
                while (isActive) {
                    delay(PUBLISH_MS)
                    publish()
                }
            },
        )
    }

    fun stop() = jobs.forEach { it.cancel() }

    private fun publish() {
        val t = nowMs()
        val since = if (lastReadingAt < 0) null else t - lastReadingAt
        val next = SignalInfo.evaluate(status, t, since, ratePerSecond(t))
        if (next != _state.value) _state.value = next
    }

    private fun ratePerSecond(t: Long): Int {
        var n = 0
        val total = minOf(count, RING)
        for (i in 0 until total) if (t - stamps[i] <= 1_000L) n++
        return n
    }

    private companion object {
        const val RING = 64
        const val PUBLISH_MS = 500L
    }
}

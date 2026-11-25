package com.rehabgrip.feature.measure

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.domain.model.ForceSample
import com.rehabgrip.core.domain.session.Attempt
import com.rehabgrip.core.domain.session.AttemptTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

data class LiveState(
    val sourceName: String,
    val status: ScaleStatus = ScaleStatus.IDLE,
    val recording: Boolean = true,
    val currentKg: Double = 0.0,
    val peak: ForceSample? = null,
    val samples: List<ForceSample> = emptyList(),
    val attempts: List<Attempt> = emptyList(),
)

/**
 * Drives a live session: listens to a [ForceSource], keeps the graph buffer,
 * the session maximum and the attempts. UI-agnostic (no Compose here).
 */
class LiveMeasurementPresenter(
    private val source: ForceSource,
    private val scope: CoroutineScope,
    private val tracker: AttemptTracker = AttemptTracker(),
) {
    private val _state = MutableStateFlow(LiveState(sourceName = source.name))
    val state: StateFlow<LiveState> = _state.asStateFlow()
    private var jobs: List<Job> = emptyList()
    private val startMark = TimeSource.Monotonic.markNow()

    fun start() {
        source.start()
        jobs = listOf(
            scope.launch { source.status.collect { s -> _state.update { it.copy(status = s) } } },
            scope.launch {
                source.readings.collect { reading ->
                    if (!_state.value.recording) return@collect
                    onSample(ForceSample(startMark.elapsedNow().inWholeMilliseconds, reading.kg))
                }
            },
        )
    }

    private fun onSample(sample: ForceSample) {
        tracker.add(sample)
        _state.update { s ->
            val newPeak = if (s.peak == null || sample.kg > s.peak.kg) sample else s.peak
            s.copy(
                currentKg = sample.kg,
                peak = newPeak,
                samples = (s.samples + sample).takeLast(MAX_BUFFER),
                attempts = tracker.attempts,
            )
        }
    }

    /** Pauses/resumes recording without dropping the scan (no rescan throttling). */
    fun toggleRecording() = _state.update { it.copy(recording = !it.recording, currentKg = 0.0) }

    fun dispose() {
        jobs.forEach { it.cancel() }
        source.stop()
    }

    private companion object {
        const val MAX_BUFFER = 1_200
    }
}

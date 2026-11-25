package com.rehabgrip.core.domain.session

import com.rehabgrip.core.domain.model.ForceSample

/** One pull inside a live session: from rising above the threshold until it drops back. */
data class Attempt(val index: Int, val startMillis: Long, val endMillis: Long, val peakKg: Double)

/**
 * Splits a live force stream into attempts. An attempt starts when force rises above
 * [thresholdKg] and ends when it stays below it. Pure logic, no clock or I/O.
 */
class AttemptTracker(private val thresholdKg: Double = 2.0) {
    private val finished = mutableListOf<Attempt>()
    private var openStart: Long? = null
    private var openPeak = 0.0
    private var lastT = 0L

    var sessionPeakKg: Double = 0.0
        private set

    val attempts: List<Attempt> get() = finished + listOfNotNull(current())

    fun current(): Attempt? = openStart?.let { Attempt(finished.size + 1, it, lastT, openPeak) }

    val isPulling: Boolean get() = openStart != null

    fun add(sample: ForceSample) {
        lastT = sample.tMillis
        if (sample.kg > sessionPeakKg) sessionPeakKg = sample.kg
        val start = openStart
        when {
            start == null && sample.kg >= thresholdKg -> {
                openStart = sample.tMillis
                openPeak = sample.kg
            }
            start != null && sample.kg >= thresholdKg -> openPeak = maxOf(openPeak, sample.kg)
            start != null -> {
                finished += Attempt(finished.size + 1, start, sample.tMillis, openPeak)
                openStart = null
                openPeak = 0.0
            }
        }
    }

    fun reset() {
        finished.clear()
        openStart = null
        openPeak = 0.0
        sessionPeakKg = 0.0
        lastT = 0L
    }

    /** Index of the attempt with the highest peak, or null when nothing was pulled. */
    fun bestAttemptIndex(): Int? = attempts.maxByOrNull { it.peakKg }?.index
}

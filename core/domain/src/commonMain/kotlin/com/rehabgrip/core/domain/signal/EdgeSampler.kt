package com.rehabgrip.core.domain.signal

/** The point at the right edge of the live graph: its time and its force. */
class Edge(val tMs: Long, val kg: Float)

/**
 * Where the right edge of the live graph sits and what it shows. The render delay adapts to
 * the stream: 1x the median interval between the latest samples, clamped to 60..180 ms, so
 * there is always a next sample to interpolate toward and no more lag than needed. If the
 * stream stops, the last value is simply held up to "now" (no invented peaks).
 *
 * The big number on screen is the [Edge] value, from the same function and the same instant
 * the graph uses for its edge, so number and graph can never disagree.
 */
class EdgeSampler(private val window: Int = 8) {
    private val scratch = LongArray(window)

    fun lagMs(buf: ForceRingBuffer): Long {
        val n = minOf(window, buf.size - 1)
        if (n <= 0) return DEFAULT_LAG_MS
        for (i in 0 until n) scratch[i] = buf.timeAt(buf.size - 1 - i) - buf.timeAt(buf.size - 2 - i)
        // insertion sort of the few intervals, then the median
        for (i in 1 until n) {
            val v = scratch[i]
            var j = i - 1
            while (j >= 0 && scratch[j] > v) { scratch[j + 1] = scratch[j]; j-- }
            scratch[j + 1] = v
        }
        return scratch[n / 2].coerceIn(MIN_LAG_MS, MAX_LAG_MS)
    }

    fun edge(buf: ForceRingBuffer, nowMs: Long): Edge {
        val t = nowMs - lagMs(buf)
        return Edge(t, buf.interpolate(t))
    }

    companion object {
        const val MIN_LAG_MS = 60L
        const val MAX_LAG_MS = 180L
        const val DEFAULT_LAG_MS = 100L
    }
}

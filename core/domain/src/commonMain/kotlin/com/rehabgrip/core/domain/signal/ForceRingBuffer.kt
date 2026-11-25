package com.rehabgrip.core.domain.signal

/**
 * Fixed-size circular buffer of (time, kg) samples backed by primitive arrays: no
 * list and no object per sample, so a 10 Hz stream does not allocate. Index 0 is the
 * oldest sample. [version] changes on every write so a drawing layer can invalidate
 * itself without recomposition.
 */
class ForceRingBuffer(val capacity: Int = DEFAULT_CAPACITY) {
    private val times = LongArray(capacity)
    private val values = FloatArray(capacity)
    private var head = 0 // slot of the oldest sample

    var size: Int = 0
        private set

    var version: Long = 0
        private set

    init {
        require(capacity >= 2) { "capacity must be at least 2" }
    }

    fun add(tMs: Long, kg: Float) {
        val slot = (head + size) % capacity
        if (size < capacity) {
            size++
        } else {
            head = (head + 1) % capacity
        }
        times[slot] = tMs
        values[slot] = kg
        version++
    }

    fun clear() {
        head = 0
        size = 0
        version++
    }

    fun timeAt(i: Int): Long = times[(head + i) % capacity]

    fun valueAt(i: Int): Float = values[(head + i) % capacity]

    val lastTime: Long get() = if (size == 0) 0L else timeAt(size - 1)

    /** Index of the first sample with time >= [tMs] (size when none). Binary search. */
    fun firstIndexAtOrAfter(tMs: Long): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (timeAt(mid) < tMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Linear interpolation at [tMs]. Before the first sample it returns the first
     * value, after the last one the last value, and 0 when empty.
     */
    fun interpolate(tMs: Long): Float {
        if (size == 0) return 0f
        val next = firstIndexAtOrAfter(tMs)
        if (next == 0) return valueAt(0)
        if (next >= size) return valueAt(size - 1)
        val t0 = timeAt(next - 1)
        val t1 = timeAt(next)
        if (t1 == t0) return valueAt(next)
        val f = (tMs - t0).toFloat() / (t1 - t0)
        return valueAt(next - 1) + (valueAt(next) - valueAt(next - 1)) * f
    }

    companion object {
        const val DEFAULT_CAPACITY = 1_024
    }
}

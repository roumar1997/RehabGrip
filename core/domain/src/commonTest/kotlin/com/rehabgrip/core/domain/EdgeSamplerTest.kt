package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.signal.EdgeSampler
import com.rehabgrip.core.domain.signal.ForceRingBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class EdgeSamplerTest {
    private fun buf(vararg times: Long) = ForceRingBuffer(32).also { b -> times.forEach { b.add(it, it / 10f) } }

    @Test fun lagIsTheMedianIntervalClamped() {
        val s = EdgeSampler()
        assertEquals(100L, s.lagMs(buf(0, 100, 200, 300, 400)))
        assertEquals(60L, s.lagMs(buf(0, 20, 40, 60, 80))) // 80 Hz stream: minimum
        assertEquals(180L, s.lagMs(buf(0, 500, 1000, 1500))) // slow stream (2/s): maximum
        assertEquals(EdgeSampler.DEFAULT_LAG_MS, s.lagMs(buf(0)))
    }

    @Test fun medianIgnoresAnOutlierGap() {
        assertEquals(100L, EdgeSampler().lagMs(buf(0, 100, 200, 1200, 1300, 1400, 1500)))
    }

    @Test fun edgeValueIsTheGraphValueAtTheSameInstant() {
        val b = buf(0, 100, 200, 300)
        val s = EdgeSampler()
        val e = s.edge(b, nowMs = 350)
        assertEquals(250L, e.tMs) // now minus the 100 ms lag
        assertEquals(b.interpolate(e.tMs), e.kg, 0f) // number == right edge of the line
        assertEquals(25f, e.kg, 0.001f)
    }

    @Test fun whenTheStreamStopsTheLastValueIsHeldNotInvented() {
        val b = buf(0, 100, 200, 300)
        val e = EdgeSampler().edge(b, nowMs = 5_000)
        assertEquals(b.valueAt(b.size - 1), e.kg, 0f)
    }
}

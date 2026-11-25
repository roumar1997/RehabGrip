package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.signal.ForceRingBuffer
import com.rehabgrip.core.domain.signal.niceScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalTest {
    @Test fun bufferKeepsOrderAndOverwritesOldest() {
        val b = ForceRingBuffer(4)
        (1..6).forEach { b.add(it * 100L, it.toFloat()) }
        assertEquals(4, b.size)
        assertEquals(300L, b.timeAt(0))
        assertEquals(6f, b.valueAt(3))
        assertEquals(600L, b.lastTime)
    }

    @Test fun versionChangesOnEveryWrite() {
        val b = ForceRingBuffer(4)
        val v0 = b.version
        b.add(0, 1f)
        val v1 = b.version
        b.clear()
        assertTrue(v1 > v0 && b.version > v1)
        assertEquals(0, b.size)
    }

    @Test fun interpolatesBetweenSamplesAndClampsEnds() {
        val b = ForceRingBuffer(8)
        b.add(0, 0f)
        b.add(100, 10f)
        assertEquals(5f, b.interpolate(50), 0.001f)
        assertEquals(0f, b.interpolate(-5), 0.001f)
        assertEquals(10f, b.interpolate(500), 0.001f)
        assertEquals(0f, ForceRingBuffer(4).interpolate(10), 0.001f)
    }

    @Test fun interpolationWorksAfterWrapping() {
        val b = ForceRingBuffer(3)
        (0..5).forEach { b.add(it * 100L, it * 10f) }
        assertEquals(35f, b.interpolate(350), 0.001f)
        assertEquals(2, b.firstIndexAtOrAfter(401))
    }

    @Test fun niceScaleUsesRoundTicks() {
        val s = niceScale(37.4)
        assertEquals(10.0, s.step)
        assertEquals(40.0, s.max)
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0), (0 until s.tickCount).map { s.tick(it) })
        assertEquals(20.0, niceScale(80.0, 4).step)
        assertEquals(5.0, niceScale(0.0, 4, minMax = 20.0).step)
        assertEquals(50.0, niceScale(200.0, 4).step)
    }
}

package com.rehabgrip.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScalePipelineTest {
    private val scaleA = "A"
    private val other = ByteArray(12).also { it[0] = 0x10; it[1] = 0x20; it[10] = 0x22; it[11] = 0x0C } // 87.00 kg

    @Test fun realCapturedFrameDecodes() {
        val iosFrame = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x11, 0x2a, 0xc0.toByte(), 0x19, 0x11, 0x22, 0x7f, 0x01, 0x04, 0x92.toByte(), 0x01, 0xf4.toByte(), 0x01, 0x4e, 0xdb.toByte())
        val payload = CraneAdvertisementParser.payloadOf(iosFrame)
        assertTrue(CraneAdvertisementParser.hasSignature(payload))
        assertEquals(11.70, CraneAdvertisementParser.parseAny(iosFrame)!!.kg, 0.001)
    }

    @Test fun mixedDevicesOnlyTheLockedScaleGetsThrough() {
        val p = ScalePipeline(DeviceLock(null, selectAfterMs = 500))
        var t = 0L
        val out = mutableListOf<Double>()
        repeat(60) {
            t += 100
            p.process(scaleA, "IF_B7", -55, CraneAdvertisementParser.encode(11.7, true), t)?.let { out += it.kg }
            p.process("B", null, -40, other, t)?.let { out += it.kg } // stronger signal but not a scale
        }
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it == 11.7 }, "nothing from the other device: $out")
        assertEquals("A", p.lock.lockedId)
    }

    @Test fun storedIdIgnoresEveryoneElseEvenWithSignature() {
        val p = ScalePipeline(DeviceLock("A"))
        assertNotNull(p.process("A", null, -60, CraneAdvertisementParser.encode(5.0), 0))
        assertNull(p.process("C", "IF_B7", -30, CraneAdvertisementParser.encode(97.9), 100))
    }

    @Test fun autoSelectionPicksTheStrongestScaleLooking() {
        var chosen: String? = null
        val p = ScalePipeline(DeviceLock(null, 300) { id, _ -> chosen = id })
        listOf(0L, 100, 200, 300, 400).forEach { t ->
            p.process("far", "IF_B7", -80, CraneAdvertisementParser.encode(1.0), t)
            p.process("near", null, -50, CraneAdvertisementParser.encode(2.0, true), t)
        }
        assertEquals("near", chosen)
    }

    @Test fun jumpFilterDropsIsolatedSpikesButAcceptsConfirmedChanges() {
        val f = JumpFilter()
        assertEquals(10.0, f.accept(10.0, 0))
        assertNull(f.accept(87.0, 100)) // isolated spike
        assertEquals(10.2, f.accept(10.2, 200)) // back to normal: spike never shown
        assertNull(f.accept(60.0, 300)) // real jump needs confirmation
        assertEquals(60.5, f.accept(60.5, 400))
        assertEquals(70.0, f.accept(70.0, 800)) // slow changes pass
    }
}

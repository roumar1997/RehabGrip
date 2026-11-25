package com.rehabgrip.core.ble

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CraneParserTest {
    private fun payload(raw: Int, flags: Int? = null): ByteArray {
        val size = if (flags == null) 12 else 17
        return ByteArray(size).also {
            it[0] = 0x02; it[1] = 0x03
            it[10] = (raw shr 8).toByte(); it[11] = (raw and 0xFF).toByte()
            if (flags != null) it[14] = flags.toByte()
        }
    }

    @Test
    fun decodesKgBigEndianHundredths() {
        val r = assertNotNull(CraneAdvertisementParser.parse(payload(3270, flags = 0x11)))
        assertEquals(32.7, r.kg, 0.0001)
        assertTrue(r.stable)
    }

    @Test
    fun legacyPacketWithoutUnitByteIsKg() {
        assertEquals(12.34, CraneAdvertisementParser.parse(payload(1234))!!.kg, 0.0001)
    }

    @Test
    fun convertsPounds() {
        assertEquals(45.359237, CraneAdvertisementParser.parse(payload(10000, flags = 0x02))!!.kg, 0.0001)
    }

    @Test
    fun rejectsShortAndAbsurdPayloads() {
        assertNull(CraneAdvertisementParser.parse(ByteArray(11)))
        assertNull(CraneAdvertisementParser.parse(payload(0xFFFF, flags = 0x01)))
    }

    @Test
    fun stripsLittleEndianCompanyId() {
        val full = byteArrayOf(0x00, 0x01) + payload(500)
        val stripped = assertNotNull(CraneAdvertisementParser.stripCompanyId(full))
        assertEquals(5.0, CraneAdvertisementParser.parse(stripped)!!.kg, 0.0001)
        assertNull(CraneAdvertisementParser.stripCompanyId(byteArrayOf(0x4C, 0x00, 1)))
    }

    @Test
    fun simulatorProducesRestAndPull() {
        val sim = SimulatedForceSource(peakKg = 30.0, random = Random(1))
        assertEquals(0.0, sim.valueAt(500))
        assertTrue(sim.valueAt(4_000) in 25.0..33.0)
    }
}

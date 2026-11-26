package com.rehabgrip.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleBookTest {
    @Test fun severalScalesRoundTripWithTheirOwnNames() {
        var list = ScaleBook.upsert(emptyList(), "A", "IF_B7")
        list = ScaleBook.upsert(list, "B", null)
        list = ScaleBook.rename(list, "A", "Del salón")
        val back = ScaleBook.decode(ScaleBook.encode(list))
        assertEquals(list, back)
        assertEquals(listOf("Del salón", "Báscula"), back.map { it.label })
    }

    @Test fun upsertKeepsTheOwnNameAndForgetRemovesOnlyThatOne() {
        var list = ScaleBook.rename(ScaleBook.upsert(emptyList(), "A", "IF_B7"), "A", "Mía")
        list = ScaleBook.upsert(list, "A", "WH-C06")
        assertEquals("Mía", list.single().customName)
        assertEquals("WH-C06", list.single().deviceName)
        list = ScaleBook.upsert(list, "B", "IF_B7")
        assertEquals(listOf("B"), ScaleBook.remove(list, "A").map { it.id })
    }
}

class LiveScalesTest {
    private fun seen(id: String, name: String?, rssi: Int, scale: Boolean = true) =
        SeenDevice(id, name, rssi, if (scale) 0x0100 else 0x004C, "", 10, 5.0, 3.0, scale, null)

    @Test fun savedFirstThenNewlySeenAndOthersIgnored() {
        val saved = listOf(SavedScale("A", "IF_B7", "Salón"), SavedScale("B", "IF_B7"))
        val rows = LiveScales.merge(saved, listOf(seen("A", "IF_B7", -55), seen("C", "IF_B7", -70), seen("X", "TV", -40, scale = false)))
        assertEquals(listOf("A", "B", "C"), rows.map { it.scale.id })
        assertEquals(listOf(true, false, true), rows.map { it.visible })
        assertEquals(listOf(true, true, false), rows.map { it.saved })
        assertEquals(-55, rows[0].rssi)
        assertEquals("Salón", rows[0].scale.label)
        assertEquals("Báscula IF_B7", rows[2].scale.label)
    }

    @Test fun emptyWhenNothingSavedNorSeen() = assertEquals(emptyList(), LiveScales.merge(emptyList(), emptyList()))
}

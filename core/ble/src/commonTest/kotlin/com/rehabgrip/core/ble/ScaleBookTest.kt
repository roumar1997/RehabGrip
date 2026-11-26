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

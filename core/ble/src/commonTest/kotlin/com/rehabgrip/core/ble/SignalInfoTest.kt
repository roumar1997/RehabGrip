package com.rehabgrip.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class SignalInfoTest {
    private fun eval(status: ScaleStatus, start: Long, last: Long?, rate: Int = 0) =
        SignalInfo.evaluate(status, start, last, rate).state

    @Test fun connectingForTheFirstThreeSeconds() {
        assertEquals(SignalState.CONNECTING, eval(ScaleStatus.SCANNING, 2_900, null))
        assertEquals(SignalState.SEARCHING, eval(ScaleStatus.SCANNING, 3_000, null))
    }

    @Test fun connectedWhileReadingsArrive() {
        val s = SignalInfo.evaluate(ScaleStatus.RECEIVING, 10_000, 120, 9)
        assertEquals(SignalState.CONNECTED, s.state)
        assertEquals("Báscula conectada · 9 lecturas/s", s.label())
    }

    @Test fun lostAfterThreeSecondsWithoutReadings() {
        assertEquals(SignalState.CONNECTED, eval(ScaleStatus.RECEIVING, 20_000, 2_999))
        assertEquals(SignalState.LOST, eval(ScaleStatus.RECEIVING, 20_000, 3_000))
    }

    @Test fun bluetoothProblemsWinOverEverything() {
        assertEquals(SignalState.BLUETOOTH_OFF, eval(ScaleStatus.BLUETOOTH_OFF, 100, 10))
        assertEquals(SignalState.UNAUTHORIZED, eval(ScaleStatus.UNAUTHORIZED, 100, null))
    }
}

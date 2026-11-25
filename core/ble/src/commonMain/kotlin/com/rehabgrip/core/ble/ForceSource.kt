package com.rehabgrip.core.ble

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class ScaleStatus { IDLE, SCANNING, RECEIVING, LOST, BLUETOOTH_OFF, UNAUTHORIZED }

/** One decoded reading. [stable] mirrors the scale's own "stable" flag when present. */
data class ScaleReading(val kg: Double, val stable: Boolean)

/**
 * Port for anything that streams force: the real Crane WH-C06 (advertisements only,
 * it never connects) or the simulated scale used in debug / emulators.
 */
interface ForceSource {
    val name: String
    val status: StateFlow<ScaleStatus>
    val readings: SharedFlow<ScaleReading>
    fun start()
    fun stop()
}

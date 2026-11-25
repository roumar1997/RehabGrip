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

    /**
     * Hint from a guided session: the user should be pulling (true) or resting (false).
     * Only the simulated scale uses it, to follow the rhythm of the workout; real scales ignore it.
     * [strength] scales the simulated peak (0-1).
     */
    fun setEffort(active: Boolean, strength: Double = 1.0) = Unit
}

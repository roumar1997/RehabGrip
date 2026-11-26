package com.rehabgrip.core.ble

import kotlin.math.abs

/**
 * Drops isolated spikes: a reading that jumps more than [maxJumpKg] from the previous one
 * in under [windowMs] is held back, and only accepted when the next reading confirms it
 * (two similar readings in a row validate the new level).
 */
class JumpFilter(private val maxJumpKg: Double = 40.0, private val windowMs: Long = 300, private val similarKg: Double = 5.0) {
    private var lastKg: Double? = null
    private var lastAt = 0L
    private var pendingKg: Double? = null

    /** The reading to publish now, or null when it was held back. */
    fun accept(kg: Double, nowMs: Long): Double? {
        val prev = lastKg
        if (prev == null) return take(kg, nowMs)
        val jump = abs(kg - prev) > maxJumpKg && nowMs - lastAt < windowMs
        if (!jump) return take(kg, nowMs)
        val pending = pendingKg
        if (pending != null && abs(pending - kg) <= similarKg) return take(kg, nowMs)
        pendingKg = kg
        return null
    }

    private fun take(kg: Double, nowMs: Long): Double {
        lastKg = kg
        lastAt = nowMs
        pendingKg = null
        return kg
    }

    fun reset() {
        lastKg = null
        pendingKg = null
    }
}

/**
 * Locks the scale to ONE device. With a stored id only that device is heard. Without it,
 * after [selectAfterMs] the scale-looking device with the strongest signal is chosen
 * (name IF_B7 / WH-C06 / Crane, or the 02 03 payload signature), falling back to the
 * strongest company-0x0100 device that decodes. [onSelected] stores the choice.
 */
class DeviceLock(
    var lockedId: String? = null,
    private val selectAfterMs: Long = 1_500,
    private val onSelected: (id: String, name: String?) -> Unit = { _, _ -> },
) {
    private class Cand(var rssi: Int, var name: String?)

    private val strong = HashMap<String, Cand>()
    private val weak = HashMap<String, Cand>()
    private var startMs = -1L

    fun accept(id: String, name: String?, rssi: Int, payload: ByteArray, nowMs: Long): Boolean {
        lockedId?.let { return it == id }
        val isStrong = CraneAdvertisementParser.looksLikeScale(name, payload)
        val target = if (isStrong) strong else weak
        if (!isStrong && CraneAdvertisementParser.parse(payload) == null) return false
        target.getOrPut(id) { Cand(rssi, name) }.also { it.rssi = rssi; if (!name.isNullOrBlank()) it.name = name }
        if (startMs < 0) startMs = nowMs
        if (nowMs - startMs < selectAfterMs) return false
        val pool = strong.ifEmpty { weak }
        val best = pool.maxByOrNull { it.value.rssi } ?: return false
        lockedId = best.key
        onSelected(best.key, best.value.name)
        return best.key == id
    }

    /** Forget the choice (and restart the auto-selection). */
    fun clear() {
        lockedId = null
        strong.clear()
        weak.clear()
        startMs = -1
    }
}

/** Counters of what happened to the frames: seen -> from the locked device -> decoded -> held back by the spike filter -> accepted. */
class PipelineStats {
    var seen = 0
    var fromLocked = 0
    var decoded = 0
    var held = 0
    var accepted = 0

    fun summary() = "recibidas $seen · del dispositivo $fromLocked · decodificadas $decoded · retenidas por saltos $held · aceptadas $accepted"
}

/** Frame in, reading out: device lock, decoding and spike filter. Shared by Android, iOS and the simulator. */
class ScalePipeline(val lock: DeviceLock = DeviceLock(), private val filter: JumpFilter = JumpFilter()) {
    val stats = PipelineStats()

    /** [manufacturer] may carry the 2 company-ID bytes or not. */
    fun process(id: String, name: String?, rssi: Int, manufacturer: ByteArray, nowMs: Long): ScaleReading? {
        val payload = CraneAdvertisementParser.payloadOf(manufacturer)
        stats.seen++
        if (!lock.accept(id, name, rssi, payload, nowMs)) return null
        stats.fromLocked++
        val reading = CraneAdvertisementParser.parse(payload) ?: return null
        stats.decoded++
        val kg = filter.accept(reading.kg, nowMs)
        if (kg == null) {
            stats.held++
            return null
        }
        stats.accepted++
        return reading.copy(kg = kg, rssi = rssi, deviceName = name)
    }
}

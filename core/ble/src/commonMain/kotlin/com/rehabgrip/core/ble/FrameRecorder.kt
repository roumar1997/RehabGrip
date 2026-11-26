package com.rehabgrip.core.ble

import kotlin.math.abs

/** One DISTINCT payload seen from the recorded device. [jump] = it differs a lot from the frame received just before. */
data class RecordedFrame(val hex: String, val count: Int, val kg: Double?, val jump: Boolean)

/** «Grabar tramas»: counts the distinct payloads (hex without company id) of one device. */
class FrameRecorder(private val jumpKg: Double = 40.0) {
    private class Entry(val hex: String, val kg: Double?) {
        var count = 0
        var jump = false
    }

    private val entries = LinkedHashMap<String, Entry>()
    private var lastKg: Double? = null
    var total = 0
        private set

    fun add(manufacturer: ByteArray) {
        val payload = CraneAdvertisementParser.payloadOf(manufacturer)
        val hex = payload.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val kg = CraneAdvertisementParser.parse(payload)?.kg
        val e = entries.getOrPut(hex) { Entry(hex, kg) }
        e.count++
        total++
        val prev = lastKg
        if (kg != null && prev != null && abs(kg - prev) > jumpKg) e.jump = true
        if (kg != null) lastKg = kg
    }

    fun frames(): List<RecordedFrame> = entries.values.map { RecordedFrame(it.hex, it.count, it.kg, it.jump) }

    fun report(deviceLabel: String, seconds: Int): String = buildString {
        appendLine("Tramas de $deviceLabel · $seconds s · $total anuncios · ${entries.size} distintas")
        frames().forEach { appendLine("${if (it.jump) "SALTO " else ""}${it.hex} · x${it.count} · ${it.kg?.let { k -> "$k kg" } ?: "sin peso"}") }
    }
}

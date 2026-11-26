package com.rehabgrip.core.ble

import kotlin.math.abs

/** One DISTINCT payload seen from the recorded device. [jump] = it differs a lot from the frame received just before. */
data class RecordedFrame(val hex: String, val count: Int, val kg: Double?, val jump: Boolean)

/** A kind of frame: first 10 bytes plus length (the scale may alternate types). */
data class FrameType(val key: String, val count: Int)

/** «Grabar tramas»: counts the distinct payloads (hex without company id) of one device. */
class FrameRecorder(private val jumpKg: Double = 40.0) {
    private class Entry(val hex: String, val kg: Double?) {
        var count = 0
        var jump = false
    }

    private val entries = LinkedHashMap<String, Entry>()
    private val types = LinkedHashMap<String, Int>()
    private var lastKg: Double? = null
    var total = 0
        private set

    /** Advertisements of the device that carried no manufacturer data (scan responses). */
    var withoutData = 0
        private set

    /** [manufacturer] null = an advertisement of this device without manufacturer data. */
    fun add(manufacturer: ByteArray?) {
        total++
        if (manufacturer == null) {
            withoutData++
            return
        }
        val payload = CraneAdvertisementParser.payloadOf(manufacturer)
        val hex = payload.hex()
        val kg = CraneAdvertisementParser.parse(payload)?.kg
        val e = entries.getOrPut(hex) { Entry(hex, kg) }
        e.count++
        val type = payload.take(10).toByteArray().hex() + " · ${payload.size} bytes"
        types[type] = (types[type] ?: 0) + 1
        val prev = lastKg
        if (kg != null && prev != null && abs(kg - prev) > jumpKg) e.jump = true
        if (kg != null) lastKg = kg
    }

    fun frames(): List<RecordedFrame> = entries.values.map { RecordedFrame(it.hex, it.count, it.kg, it.jump) }

    fun frameTypes(): List<FrameType> = types.entries.sortedByDescending { it.value }.map { FrameType(it.key, it.value) }

    fun report(deviceLabel: String, seconds: Int, pipeline: String? = null): String = buildString {
        appendLine("Tramas de $deviceLabel · $seconds s · $total anuncios (${total - withoutData} con datos, $withoutData sin datos) · ${entries.size} distintas")
        pipeline?.let { appendLine("Pipeline: $it") }
        appendLine("Tipos de trama:")
        frameTypes().forEach { appendLine("  ${it.key} · x${it.count}") }
        frames().forEach { appendLine("${if (it.jump) "SALTO " else ""}${it.hex} · x${it.count} · ${it.kg?.let { k -> "$k kg" } ?: "sin peso"}") }
    }

    private fun ByteArray.hex() = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

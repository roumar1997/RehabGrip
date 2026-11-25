package com.rehabgrip.core.ble

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One advertisement of any BLE device. [manufacturer] is always iOS-style: the 2 company
 * ID bytes (little-endian) followed by the payload, so both platforms report the same way.
 */
class RawAdvertisement(val id: String, val name: String?, val rssi: Int, val manufacturer: ByteArray?)

/** Unfiltered BLE scan used only by the Bluetooth diagnostics screen. */
interface AdvertisementScanner {
    val advertisements: SharedFlow<RawAdvertisement>
    val status: StateFlow<ScaleStatus>
    fun start()
    fun stop()
}

/** What the diagnostics list shows about one device. */
data class SeenDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
    val companyId: Int?,
    val manufacturerHex: String,
    val advertisements: Int,
    val perSecond: Double,
    val looksLikeScale: Boolean,
    /** Weight read with the WH-C06 protocol, or null when the payload does not fit it. */
    val interpretedKg: Double?,
)

/** Collects advertisements into [SeenDevice] rows. Pure: time comes from the caller. */
class DiagnosticsAccumulator {
    private class Entry(val id: String) {
        var name: String? = null
        var rssi = 0
        var manufacturer: ByteArray? = null
        var count = 0
        var kg: Double? = null
    }

    private val entries = LinkedHashMap<String, Entry>()
    private var startedAt = 0L
    private var lastAt = 0L

    fun reset(nowMs: Long) {
        entries.clear()
        startedAt = nowMs
        lastAt = nowMs
    }

    fun add(ad: RawAdvertisement, nowMs: Long) {
        lastAt = nowMs
        val e = entries.getOrPut(ad.id) { Entry(ad.id) }
        e.count++
        e.rssi = ad.rssi
        if (!ad.name.isNullOrBlank()) e.name = ad.name
        ad.manufacturer?.let { m ->
            e.manufacturer = m
            CraneAdvertisementParser.parseAny(m)?.let { e.kg = it.kg }
        }
    }

    /** Devices that look like the scale first, then the strongest signal. */
    fun devices(): List<SeenDevice> {
        val seconds = ((lastAt - startedAt).coerceAtLeast(1_000L)) / 1000.0
        return entries.values.map { e ->
            val m = e.manufacturer
            val company = if (m != null && m.size >= 2) (m[0].toInt() and 0xFF) or (m[1].toInt() and 0xFF shl 8) else null
            SeenDevice(
                id = e.id, name = e.name, rssi = e.rssi, companyId = company,
                manufacturerHex = m?.toHex().orEmpty(), advertisements = e.count,
                perSecond = e.count / seconds, looksLikeScale = looksLikeScale(e.name, company), interpretedKg = e.kg,
            )
        }.sortedWith(compareByDescending<SeenDevice> { it.looksLikeScale }.thenByDescending { it.rssi })
    }

    companion object {
        fun looksLikeScale(name: String?, companyId: Int?): Boolean =
            companyId == CraneAdvertisementParser.COMPANY_ID ||
                (name != null && listOf("WH-C06", "Crane", "IF_B7").any { name.contains(it, ignoreCase = true) })

        /** Plain-text report to paste in a message. */
        fun report(devices: List<SeenDevice>, seconds: Int): String = buildString {
            appendLine("Diagnóstico Bluetooth Rehagrip · escaneo de $seconds s · ${devices.size} dispositivos")
            devices.forEach { d ->
                appendLine()
                appendLine("${if (d.looksLikeScale) "[WH-C06?] " else ""}${d.name ?: "(sin nombre)"} · ${d.id}")
                appendLine("  RSSI ${d.rssi} dBm · ${d.advertisements} anuncios (${(d.perSecond * 10).toInt() / 10.0}/s)")
                appendLine("  company ${d.companyId?.let { "0x" + it.toString(16).padStart(4, '0') } ?: "—"} · datos ${d.manufacturerHex.ifEmpty { "—" }}")
                d.interpretedKg?.let { appendLine("  peso interpretado: $it kg") }
            }
        }

        private fun ByteArray.toHex() = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}

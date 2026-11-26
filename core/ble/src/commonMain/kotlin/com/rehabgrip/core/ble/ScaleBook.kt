package com.rehabgrip.core.ble

/** A scale this phone knows: its identifier (iOS UUID / Android MAC), the name it advertises and the user's own name. */
data class SavedScale(val id: String, val deviceName: String?, val customName: String? = null) {
    /** «Mi báscula del salón» or, without own name, «Báscula IF_B7». */
    val label: String get() = customName?.takeIf { it.isNotBlank() } ?: ("Báscula" + (deviceName?.let { " $it" } ?: ""))
}

/** The list of known scales, stored as text in one setting. Only one is active (the lock of the pipeline). */
object ScaleBook {
    fun encode(scales: List<SavedScale>): String =
        scales.joinToString("\n") { listOf(it.id, it.deviceName.orEmpty(), it.customName.orEmpty()).joinToString("\t") { f -> f.replace('\t', ' ').replace('\n', ' ') } }

    fun decode(text: String?): List<SavedScale> =
        text.orEmpty().lines().filter { it.isNotBlank() }.map { line ->
            val p = line.split('\t')
            SavedScale(p[0], p.getOrNull(1)?.ifBlank { null }, p.getOrNull(2)?.ifBlank { null })
        }

    /** Adds the scale or refreshes the advertised name, keeping the user's own name. */
    fun upsert(scales: List<SavedScale>, id: String, deviceName: String?): List<SavedScale> =
        if (scales.any { it.id == id }) scales.map { if (it.id == id) it.copy(deviceName = deviceName ?: it.deviceName) else it }
        else scales + SavedScale(id, deviceName)

    fun rename(scales: List<SavedScale>, id: String, name: String): List<SavedScale> =
        scales.map { if (it.id == id) it.copy(customName = name.trim().ifBlank { null }) else it }

    fun remove(scales: List<SavedScale>, id: String): List<SavedScale> = scales.filterNot { it.id == id }
}

/** A row of «Mis básculas»: a saved scale and/or one seen right now. */
data class ScaleRow(val scale: SavedScale, val rssi: Int?, val visible: Boolean, val saved: Boolean)

object LiveScales {
    /**
     * Saved scales first (with their live signal when seen now), then the scales seen now that are not
     * saved yet (they are saved automatically, this is just so the list never looks empty meanwhile).
     */
    fun merge(saved: List<SavedScale>, seen: List<SeenDevice>): List<ScaleRow> {
        val bySeen = seen.filter { it.looksLikeScale }.associateBy { it.id }
        val rows = saved.map { s -> ScaleRow(s, bySeen[s.id]?.rssi, visible = s.id in bySeen, saved = true) }
        val extra = bySeen.values.filter { d -> saved.none { it.id == d.id } }
            .sortedByDescending { it.rssi }.map { d -> ScaleRow(SavedScale(d.id, d.name), d.rssi, visible = true, saved = false) }
        return rows + extra
    }
}

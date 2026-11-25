package com.rehabgrip.core.domain.export

import com.rehabgrip.core.domain.model.Measurement
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Serializes measurements to CSV (semicolon separated, decimal comma: opens fine in Spanish Excel). */
class CsvExporter(private val timeZone: TimeZone = TimeZone.currentSystemDefault()) {

    fun export(measurements: List<Measurement>): String = buildString {
        appendLine("fecha;hora;ejercicio;mano;kg;origen;nota")
        measurements.sortedBy { it.epochMillis }.forEach { m ->
            val dt = Instant.fromEpochMilliseconds(m.epochMillis).toLocalDateTime(timeZone)
            val time = "${dt.hour.pad()}:${dt.minute.pad()}"
            val kg = formatKg(m.kg).replace('.', ',')
            appendLine("${dt.date};$time;${m.exercise.name};${m.hand.name};$kg;${m.source.name};${escape(m.note)}")
        }
    }

    private fun Int.pad() = toString().padStart(2, '0')

    private fun escape(text: String): String =
        if (text.any { it == ';' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    companion object {
        /** Formats kg with one decimal without platform-specific formatters. */
        fun formatKg(kg: Double): String {
            val tenths = kotlin.math.round(kg * 10).toLong()
            val sign = if (tenths < 0) "-" else ""
            val abs = kotlin.math.abs(tenths)
            return "$sign${abs / 10}.${abs % 10}"
        }
    }
}

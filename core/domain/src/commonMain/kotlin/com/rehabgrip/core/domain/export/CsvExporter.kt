package com.rehabgrip.core.domain.export

import com.rehabgrip.core.domain.grip.GripKey
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Serializes measurements to a CSV a person can read: UTF-8 with BOM, «;» separator, decimal
 * comma (opens fine in Spanish Excel), one clear column per thing, oldest first.
 */
class CsvExporter(private val timeZone: TimeZone = TimeZone.currentSystemDefault()) {

    fun export(measurements: List<Measurement>, library: List<GripSpec> = emptyList(), injuredHand: Hand? = null): String = buildString {
        append(BOM)
        appendLine(HEADER.joinToString(";"))
        measurements.sortedBy { it.epochMillis }.forEach { m ->
            val dt = Instant.fromEpochMilliseconds(m.epochMillis).toLocalDateTime(timeZone)
            val grip = (GripKey.of(m, library) as? GripKey.Library)?.let { k -> library.first { it.id == k.id } }
            val cells = listOf(
                dt.date.toString(),
                "${dt.hour.pad()}:${dt.minute.pad()}",
                exerciseName(m.exercise, grip),
                if (m.exercise == Exercise.DYNAMOMETER) "" else grip?.label.orEmpty(),
                grip?.edgeMm?.toString().orEmpty(),
                if (m.hand == Hand.RIGHT) "Derecha" else "Izquierda",
                if (injuredHand != null && m.hand == injuredHand) "Sí" else "No",
                formatKg(m.kg).replace('.', ','),
                origin(m),
                m.person,
                m.discomforts.joinToString(" | ") { "${it.zone} ${it.level}/10" },
                m.note,
            )
            appendLine(cells.joinToString(";") { escape(it) })
        }
    }

    /** File name: rehagrip-AAAA-MM-DD.csv */
    fun fileName(nowMillis: Long): String =
        "rehagrip-${Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date}.csv"

    private fun exerciseName(e: Exercise, grip: GripSpec?): String = when (e) {
        Exercise.HALF_CRIMP -> "Semiarqueo"
        Exercise.THREE_FINGER -> "Extensión"
        Exercise.OPEN_HAND -> "Semiestirado"
        Exercise.DYNAMOMETER -> "Dinamómetro de mano"
        Exercise.CUSTOM -> grip?.label ?: "Agarre propio"
    }

    private fun origin(m: Measurement): String = when {
        m.note.startsWith("Evaluación") -> "Evaluación"
        m.note.startsWith("Entreno") -> "Entreno"
        m.source == MeasurementSource.MANUAL -> "A mano"
        else -> "Medir"
    }

    private fun Int.pad() = toString().padStart(2, '0')

    private fun escape(text: String): String =
        if (text.any { it == ';' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    companion object {
        const val BOM = "﻿"
        val HEADER = listOf(
            "Fecha", "Hora", "Ejercicio", "Agarre", "Regleta (mm)", "Mano", "Mano lesionada", "Kilos",
            "Origen", "Persona", "Molestias", "Nota",
        )

        /** Formats kg with one decimal without platform-specific formatters. */
        fun formatKg(kg: Double): String {
            val tenths = kotlin.math.round(kg * 10).toLong()
            val sign = if (tenths < 0) "-" else ""
            val abs = kotlin.math.abs(tenths)
            return "$sign${abs / 10}.${abs % 10}"
        }
    }
}

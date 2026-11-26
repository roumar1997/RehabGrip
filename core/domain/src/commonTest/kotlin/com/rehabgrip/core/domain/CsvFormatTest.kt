package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.export.CsvExporter
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvFormatTest {
    private val library = GripSpec.predefined().mapIndexed { i, g -> g.copy(id = i + 1L) }
    private val day = 86_400_000L
    private val csv = CsvExporter(TimeZone.UTC)

    @Test fun readableColumnsBomSortedAndFileName() {
        val ext = library.first { it.builtIn == Exercise.THREE_FINGER }
        val rows = listOf(
            Measurement(2, Exercise.THREE_FINGER, Hand.LEFT, 31.5, 5 * day + 3_600_000L, MeasurementSource.SCALE, "Evaluación · Extensión 3 dedos", gripId = ext.id),
            Measurement(1, Exercise.THREE_FINGER, Hand.RIGHT, 13.04, 2 * day, MeasurementSource.SCALE, "Entreno «Repeticiones»", gripId = ext.id,
                discomforts = listOf(Discomfort("Polea A2", 2)), person = "Pepe"),
        )
        val text = csv.export(rows, library, injuredHand = Hand.RIGHT)
        assertTrue(text.startsWith(CsvExporter.BOM), "UTF-8 BOM first")
        val lines = text.removePrefix(CsvExporter.BOM).trim().lines()
        assertEquals("Fecha;Hora;Ejercicio;Agarre;Regleta (mm);Mano;Mano lesionada;Kilos;Origen;Persona;Molestias;Nota", lines[0])
        assertEquals("1970-01-03;00:00;Extensión;Extensión 3 dedos;20;Derecha;Sí;13,0;Entreno;Pepe;Polea A2 2/10;Entreno «Repeticiones»", lines[1])
        assertEquals("1970-01-06;01:00;Extensión;Extensión 3 dedos;20;Izquierda;No;31,5;Evaluación;;;Evaluación · Extensión 3 dedos", lines[2])
        assertEquals("rehagrip-1970-01-06.csv", csv.fileName(5 * day))
    }

    @Test fun originsAndOrphanGrip() {
        val manual = Measurement(1, Exercise.HALF_CRIMP, Hand.RIGHT, 40.0, 0, MeasurementSource.MANUAL, gripId = 999)
        val meter = Measurement(2, Exercise.HALF_CRIMP, Hand.RIGHT, 40.0, 1, MeasurementSource.SCALE, "Medidor")
        val lines = csv.export(listOf(manual, meter), library).removePrefix(CsvExporter.BOM).trim().lines()
        assertTrue(lines[1].contains(";A mano;"))
        assertTrue(lines[2].contains(";Medir;"))
        assertTrue(lines[1].startsWith("1970-01-01;00:00;Semiarqueo;Semiarqueo 4 dedos;20;"), "orphan grip id still names its predefined grip")
    }
}

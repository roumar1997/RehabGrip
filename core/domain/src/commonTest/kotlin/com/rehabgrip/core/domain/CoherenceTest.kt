package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.grip.GripKey
import com.rehabgrip.core.domain.grip.HistoryGroups
import com.rehabgrip.core.domain.grip.MaxRecords
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Home, Evolution, «Mis máximos» and History must read every record the same way, whatever
 * its origin (Medir, Entreno, Evaluación, manual) and however it is tied to its grip.
 */
class CoherenceTest {
    private val day = 86_400_000L
    private val now = 100 * day + 43_200_000L
    private val library = (GripSpec.predefined() + GripSpec(name = "Mi drag 15", edgeMm = 15, type = com.rehabgrip.core.domain.workout.GripType.DRAG))
        .mapIndexed { i, g -> g.copy(id = i + 1L) }
    private val extension = library.first { it.builtIn == Exercise.THREE_FINGER }
    private val crimp = library.first { it.builtIn == Exercise.HALF_CRIMP }
    private val custom = library.last()

    private fun m(id: Long, ex: Exercise, hand: Hand, kg: Double, ago: Long, gripId: Long? = null, source: MeasurementSource = MeasurementSource.SCALE) =
        Measurement(id, ex, hand, kg, now - ago * day, source, gripId = gripId)

    private val data = listOf(
        m(1, Exercise.THREE_FINGER, Hand.RIGHT, 13.0, 1, gripId = null, source = MeasurementSource.MANUAL), // legacy manual
        m(2, Exercise.THREE_FINGER, Hand.LEFT, 31.5, 1, gripId = extension.id), // Evaluación
        m(3, Exercise.THREE_FINGER, Hand.RIGHT, 20.0, 2, gripId = 999), // grip deleted afterwards (orphan id)
        m(4, Exercise.HALF_CRIMP, Hand.LEFT, 38.0, 3, gripId = crimp.id), // Entreno
        m(5, Exercise.HALF_CRIMP, Hand.LEFT, 10.0, 3, gripId = null), // legacy, same day
        m(6, Exercise.CUSTOM, Hand.RIGHT, 31.0, 4, gripId = custom.id), // Medir on an own grip
        m(7, Exercise.DYNAMOMETER, Hand.RIGHT, 40.0, 5, source = MeasurementSource.MANUAL),
    )

    @Test fun everyRecordHasOneKeyAndOneLabel() {
        val keys = data.map { GripKey.of(it, library) }
        assertEquals(GripKey.Library(extension.id), keys[0])
        assertEquals(GripKey.Library(extension.id), keys[2], "orphan grip id falls back to its predefined exercise grip")
        assertEquals(GripKey.Library(crimp.id), keys[4])
        assertEquals(GripKey.Library(custom.id), keys[5])
        assertEquals(GripKey.Plain(Exercise.DYNAMOMETER), keys[6])
    }

    @Test fun homeEvolutionMaxesAndHistoryAgreeOnEveryGrip() {
        val calc = ProgressCalculator(TimeZone.UTC)
        val maxRows = MaxRecords.mine(data, library).associateBy { it.label }
        val groups = HistoryGroups.build(data, library, { "Dinamómetro de mano" }, TimeZone.UTC)
        (library).forEach { g ->
            val evo = calc.summarizeGrip(data, g, ProgressRange.ALL, now, library)
            val row = maxRows.getValue(g.label)
            val hist = groups.filter { it.label == g.label }
            fun histBest(h: Hand) = hist.mapNotNull { it.of(h).best?.kg }.maxOrNull()
            assertEquals(evo.right.bestKg, row.right?.kg, "evolution vs max (right) of ${g.label}")
            assertEquals(evo.left.bestKg, row.left?.kg, "evolution vs max (left) of ${g.label}")
            assertEquals(evo.right.bestKg, histBest(Hand.RIGHT), "evolution vs history (right) of ${g.label}")
            assertEquals(evo.left.bestKg, histBest(Hand.LEFT), "evolution vs history (left) of ${g.label}")
        }
        // The case seen on the phone: Extension has 13 and 20 on the right, 31.5 on the left, everywhere.
        val ext = calc.summarizeGrip(data, extension, ProgressRange.ALL, now, library)
        assertEquals(20.0, ext.right.bestKg)
        assertEquals(31.5, ext.left.bestKg)
    }

    @Test fun historyGroupsByDayAndGripWithBothHands() {
        val groups = HistoryGroups.build(data, library, { "Dinamómetro de mano" }, TimeZone.UTC)
        val crimpDay = groups.single { it.key == GripKey.Library(crimp.id) }
        assertEquals(2, crimpDay.left.records.size, "two left records the same day in one expandable group")
        assertEquals(38.0, crimpDay.left.best!!.kg)
        assertEquals(null, crimpDay.right.best)
        val ext1 = groups.single { it.key == GripKey.Library(extension.id) && it.day == com.rehabgrip.core.domain.model.RecordDate.day(data[0].epochMillis, TimeZone.UTC) }
        assertEquals(13.0, ext1.right.best!!.kg)
        assertEquals(31.5, ext1.left.best!!.kg)
        assertEquals(groups.sortedByDescending { it.day }.map { it.day }, groups.map { it.day })
    }

    @Test fun friendsNeverLeakIntoMine() {
        val withFriend = data + m(8, Exercise.THREE_FINGER, Hand.RIGHT, 90.0, 0, gripId = extension.id).copy(person = "Pepe")
        assertEquals(20.0, MaxRecords.mine(withFriend, library).first { it.grip?.id == extension.id }.right!!.kg)
    }
}

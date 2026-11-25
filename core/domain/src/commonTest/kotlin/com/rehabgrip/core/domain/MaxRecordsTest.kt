package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.grip.MaxRecords
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.usecase.SaveMeasurement
import com.rehabgrip.core.domain.workout.GripSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxRecordsTest {
    private val library = GripSpec.predefined().mapIndexed { i, g -> g.copy(id = i + 1L) }
    private val crimp = library[0]

    private fun m(kg: Double, hand: Hand, at: Long, person: String = "", grip: GripSpec = crimp) =
        Measurement(exercise = grip.builtIn ?: Exercise.CUSTOM, hand = hand, kg = kg, epochMillis = at, source = MeasurementSource.SCALE, gripId = grip.id, person = person)

    @Test fun mineUsesTheBestValueNotTheLatestAndIgnoresFriends() {
        val all = listOf(m(40.0, Hand.RIGHT, 1), m(35.0, Hand.RIGHT, 5), m(90.0, Hand.RIGHT, 6, "Pepe"), m(30.0, Hand.LEFT, 2))
        val row = MaxRecords.mine(all, library).first()
        assertEquals(40.0, row.right!!.kg)
        assertEquals(30.0, row.left!!.kg)
    }

    @Test fun friendsAreGroupedByNameAndKeptApart() {
        val all = listOf(m(50.0, Hand.RIGHT, 1, "Pepe"), m(60.0, Hand.RIGHT, 3, "Pepe"), m(20.0, Hand.LEFT, 9, "Ana"), m(10.0, Hand.LEFT, 2))
        val f = MaxRecords.friends(all, library)
        assertEquals(listOf("Ana", "Pepe"), f.map { it.person })
        assertEquals(60.0, f[1].rows.first().right!!.kg)
        assertEquals(listOf("Ana", "Pepe"), MaxRecords.recentPeople(all))
    }

    @Test fun dynamometerRowAndEmptyGrips() {
        val dyn = Measurement(exercise = Exercise.DYNAMOMETER, hand = Hand.LEFT, kg = 44.0, epochMillis = 1, source = MeasurementSource.MANUAL)
        val rows = MaxRecords.mine(listOf(dyn), library)
        assertEquals("Dinamómetro de mano", rows.last().label)
        assertEquals(44.0, rows.last().left!!.kg)
        assertNull(rows.first().right)
    }

    @Test fun savedMeasurementKeepsTheFriendName() {
        val fake = object : com.rehabgrip.core.domain.port.MeasurementRepository {
            var saved: Measurement? = null
            override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<Measurement>())
            override suspend fun add(measurement: Measurement): Long { saved = measurement; return 1 }
            override suspend fun delete(id: Long) = Unit
            override suspend fun replaceAll(measurements: List<Measurement>) = Unit
        }
        kotlinx.coroutines.test.runTest {
            SaveMeasurement(fake)(Exercise.CUSTOM, Hand.RIGHT, 30.0, MeasurementSource.SCALE, 1, gripId = 4, person = "  Pepe ")
        }
        assertEquals("Pepe", fake.saved!!.person)
    }
}

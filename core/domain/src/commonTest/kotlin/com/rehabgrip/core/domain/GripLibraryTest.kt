package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.grip.GripEvaluation
import com.rehabgrip.core.domain.grip.GripRecords
import com.rehabgrip.core.domain.grip.HoldStats
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.port.GripEvaluationRepository
import com.rehabgrip.core.domain.port.MeasurementRepository
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.usecase.SaveGripEvaluation
import com.rehabgrip.core.domain.workout.EnginePhase
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.GripType
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.HandState
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.WorkoutEngine
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GripSpecTest {
    @Test
    fun summaryIsFrezLike() {
        val g = GripSpec(edgeMm = 20, fingerCount = 2, fingerNote = "centrales", type = GripType.HALF_CRIMP)
        assertEquals("20 mm / 2 dedos centrales / semiarqueado", g.summary)
        assertEquals(g.summary, g.label)
        assertEquals("Mi agarre", g.copy(name = " Mi agarre ").label)
        assertEquals("15 mm / 1 dedo índice / drag", GripSpec(edgeMm = 15, fingerCount = 1, fingerNote = "índice", type = GripType.DRAG).summary)
    }

    @Test
    fun anyEdgeWidthIsAcceptedWithinRange() {
        listOf(15, 20, 25, 30).forEach { assertTrue(GripSpec(edgeMm = it).validate().isEmpty()) }
        assertTrue(GripSpec(edgeMm = 2).validate().isNotEmpty())
        assertTrue(GripSpec(fingerCount = 5).validate().isNotEmpty())
        assertTrue(GripSpec(fingerCount = 0).validate().isNotEmpty())
    }

    @Test
    fun predefinedAreTheOriginalOnesPlusTheFourTwoFingerGrips() {
        val p = GripSpec.predefined()
        assertEquals(listOf(Exercise.HALF_CRIMP, Exercise.THREE_FINGER, Exercise.OPEN_HAND), p.take(3).map { it.builtIn })
        assertEquals(
            listOf("Semiarqueo 4 dedos", "Extensión 3 dedos", "Semiestirado 4 dedos", "Bidedo estirado", "Bidedo arqueado", "Bidedo pistola", "Bidedo pistola arqueado"),
            p.map { it.name },
        )
        assertTrue(p.all { it.edgeMm == 20 && it.isPredefined })
        assertEquals(setOf(GripType.DRAG, GripType.HALF_CRIMP), p.drop(3).map { it.type }.toSet())
        assertEquals(listOf("delanteros", "delanteros", "centrales", "centrales"), p.drop(3).map { it.fingerNote })
        assertEquals("Semiarqueo 4 dedos · 20 mm", p.first().fullLabel)
    }

    @Test
    fun matchesByIdOrLegacyExercise() {
        val builtIn = GripSpec(id = 1, builtIn = Exercise.HALF_CRIMP)
        val custom = GripSpec(id = 7)
        val legacy = m(Hand.RIGHT, 30.0, 1, Exercise.HALF_CRIMP)
        assertTrue(builtIn.matches(legacy))
        assertTrue(!custom.matches(legacy))
        assertTrue(custom.matches(m(Hand.RIGHT, 20.0, 2, Exercise.CUSTOM, gripId = 7)))
        assertTrue(!builtIn.matches(m(Hand.RIGHT, 20.0, 2, Exercise.CUSTOM, gripId = 7)))
    }
}

class GripRecordsTest {
    private val g = GripSpec(id = 5, builtIn = Exercise.HALF_CRIMP)

    @Test
    fun latestEvaluationWinsOverMeasurements() {
        val evals = listOf(
            GripEvaluation(1, 5, Hand.RIGHT, 30.0, epochMillis = 100),
            GripEvaluation(2, 5, Hand.RIGHT, 34.0, epochMillis = 200),
            GripEvaluation(3, 5, Hand.LEFT, 40.0, epochMillis = 150),
        )
        val ms = listOf(m(Hand.RIGHT, 50.0, 300, Exercise.HALF_CRIMP))
        val r = GripRecords.record(evals, ms, g, Hand.RIGHT)!!
        assertEquals(34.0, r.kg)
        assertTrue(r.fromEvaluation)
        assertEquals(40.0, GripRecords.record(evals, ms, g, Hand.LEFT)!!.kg)
    }

    @Test
    fun predefinedFallsBackToLatestMeasurementButCustomDoesNot() {
        val ms = listOf(m(Hand.RIGHT, 28.0, 100, Exercise.HALF_CRIMP), m(Hand.RIGHT, 31.0, 200, Exercise.HALF_CRIMP))
        val r = GripRecords.record(emptyList(), ms, g, Hand.RIGHT)!!
        assertEquals(31.0, r.kg)
        assertTrue(!r.fromEvaluation)
        assertNull(GripRecords.record(emptyList(), ms, GripSpec(id = 9), Hand.RIGHT))
    }

    @Test
    fun missingBasesListsGripsWithoutRecordForAnyHand() {
        val custom = GripSpec(id = 9, name = "Mío")
        val sets = listOf(
            WorkoutSet(6, 10, 20, Grip.Library(custom), SetTarget.PercentOfRecord(70, 85)),
            WorkoutSet(6, 10, 20, Grip.Library(g), SetTarget.Peak),
            WorkoutSet(6, 10, 20, Grip.Library(g), SetTarget.PercentOfRecord(70, 85)),
        )
        val lookup: (Hand, Grip) -> Double? = { h, grip -> if (h == Hand.RIGHT && (grip as Grip.Library).grip.id == 5L) 30.0 else null }
        val both = WorkoutTemplate(name = "t", sets = sets, handMode = HandMode.BOTH_SEQUENTIAL, firstHand = Hand.RIGHT)
        val missing = GripRecords.missingBases(both, lookup)
        assertEquals(setOf(9L to Hand.RIGHT, 9L to Hand.LEFT, 5L to Hand.LEFT), missing.map { it.grip.id to it.hand }.toSet())
        val single = both.copy(handMode = HandMode.SINGLE)
        assertEquals(listOf(9L to Hand.RIGHT), GripRecords.missingBases(single, lookup).map { it.grip.id to it.hand })
    }

    @Test
    fun holdStatsIgnoreRampAndRelease() {
        val s = HoldStats.from(listOf(0.0, 5.0, 20.0, 38.0, 36.0, 34.0, 35.0, 12.0, 0.0))!!
        assertEquals(38.0, s.maxKg)
        assertEquals(34.0, s.minKg)
        assertEquals(35.75, s.avgKg)
        assertNull(HoldStats.from(listOf(0.0, 0.0)))
        assertNull(HoldStats.from(emptyList()))
    }

    @Test
    fun percentTargetUsesTheRecordOfThatHandAndGrip() {
        val zone = SetTarget.PercentOfRecord(70, 85).resolve(40.0)!!
        assertEquals(28.0, zone.minKg)
        assertEquals(34.0, zone.maxKg)
    }
}

class GripEvaluationFlowTest {
    private class Evals : GripEvaluationRepository {
        val saved = mutableListOf<GripEvaluation>()
        override fun observeAll(): Flow<List<GripEvaluation>> = flowOf(saved)
        override suspend fun add(evaluation: GripEvaluation): Long { saved += evaluation; return saved.size.toLong() }
    }

    private class Ms : MeasurementRepository {
        val saved = mutableListOf<Measurement>()
        override fun observeAll(): Flow<List<Measurement>> = flowOf(saved)
        override suspend fun add(measurement: Measurement): Long { saved += measurement; return saved.size.toLong() }
        override suspend fun delete(id: Long) = Unit
        override suspend fun update(measurement: Measurement) = Unit
        override suspend fun replaceAll(measurements: List<Measurement>) = Unit
    }

    @Test
    fun savesEvaluationAndFeedsEvolutionWithTheGripId() = runTest {
        val evals = Evals()
        val ms = Ms()
        val grip = GripSpec(id = 7, name = "20mm drag", type = GripType.DRAG)
        val result = SaveGripEvaluation(evals, ms)(grip, Hand.LEFT, 38.0, 20.0, 31.0, MeasurementSource.SCALE, 1_000)
        assertTrue(result.isSuccess)
        assertEquals(GripEvaluation(0, 7, Hand.LEFT, 38.0, 20.0, 31.0, 1_000), evals.saved.single())
        val m = ms.saved.single()
        assertEquals(7L, m.gripId)
        assertEquals(Exercise.CUSTOM, m.exercise)
        assertEquals(38.0, m.kg)
    }

    @Test
    fun rejectsInconsistentValues() = runTest {
        val save = SaveGripEvaluation(Evals(), Ms())
        val g = GripSpec(id = 1)
        assertTrue(save(g, Hand.RIGHT, 0.0, null, null, MeasurementSource.MANUAL, 1).isFailure)
        assertTrue(save(g, Hand.RIGHT, 30.0, 35.0, null, MeasurementSource.MANUAL, 1).isFailure)
        assertTrue(save(g, Hand.RIGHT, 30.0, 10.0, 40.0, MeasurementSource.MANUAL, 1).isFailure)
        assertTrue(save(g, Hand.RIGHT, 30.0, null, null, MeasurementSource.MANUAL, 1).isSuccess)
    }

    @Test
    fun eachGripHasItsOwnEvolution() {
        val a = GripSpec(id = 1, builtIn = Exercise.HALF_CRIMP)
        val b = GripSpec(id = 2, name = "Mío")
        val all = listOf(
            m(Hand.RIGHT, 30.0, 1_000, Exercise.HALF_CRIMP),
            m(Hand.LEFT, 40.0, 1_000, Exercise.HALF_CRIMP),
            m(Hand.RIGHT, 20.0, 1_000, Exercise.CUSTOM, gripId = 2),
            m(Hand.LEFT, 25.0, 1_000, Exercise.CUSTOM, gripId = 2),
        )
        val calc = ProgressCalculator()
        val sa = calc.summarizeGrip(all, a, ProgressRange.ALL, 2_000)
        val sb = calc.summarizeGrip(all, b, ProgressRange.ALL, 2_000)
        assertEquals(30.0, sa.right.lastKg)
        assertEquals(75.0, sa.symmetryPercent(Hand.RIGHT))
        assertEquals(20.0, sb.right.lastKg)
        assertEquals(80.0, sb.symmetryPercent(Hand.RIGHT))
        assertEquals("Mío", sb.title)
        assertNotNull(sa.title)
        // The plain-exercise summary never mixes in custom grips.
        assertEquals(30.0, calc.summarize(all, Exercise.HALF_CRIMP, ProgressRange.ALL, 2_000).right.lastKg)
    }
}

class HandPanelsTest {
    private fun tpl(mode: HandMode) = WorkoutTemplate(
        name = "t", sets = listOf(WorkoutSet(2, 5, 8)), handMode = mode, firstHand = Hand.RIGHT,
    )

    private fun WorkoutEngine.run(ms: Long, kg: Double) = repeat((ms / 100).toInt()) { tick(100, kg) }

    @Test
    fun alternatingShowsBothHandsWithTheirOwnState() {
        val e = WorkoutEngine(tpl(HandMode.ALTERNATING), prepareMs = 0)
        e.tick(100, 30.0) // right starts pulling
        var panels = e.snapshot().panels.associateBy { it.hand }
        assertEquals(HandState.PULL, panels.getValue(Hand.RIGHT).state)
        assertTrue(panels.getValue(Hand.RIGHT).inTurn)
        assertEquals(HandState.WAIT, panels.getValue(Hand.LEFT).state)

        e.run(5_000, 30.0) // right finishes, now left in turn (rest 0 -> waiting/pull) and right rests
        panels = e.snapshot().panels.associateBy { it.hand }
        assertEquals(HandState.REST, panels.getValue(Hand.RIGHT).state)
        assertTrue(panels.getValue(Hand.RIGHT).displayMs in 1..8_000)
        assertTrue(panels.getValue(Hand.LEFT).inTurn)
        assertEquals(EnginePhase.WAITING, e.snapshot().phase)
        e.tick(100, 30.0)
        assertEquals(HandState.PULL, e.snapshot().panels.first { it.hand == Hand.LEFT }.state)
    }

    @Test
    fun restingHandKeepsItsTimerWhileTheOtherPulls() {
        val e = WorkoutEngine(tpl(HandMode.ALTERNATING), prepareMs = 0)
        e.tick(100, 30.0)
        e.run(5_000, 30.0)
        val before = e.snapshot().panels.first { it.hand == Hand.RIGHT }.displayMs
        e.run(1_000, 30.0)
        val after = e.snapshot().panels.first { it.hand == Hand.RIGHT }.displayMs
        assertTrue(after < before)
    }

    @Test
    fun singleHandHasOnePanel() {
        assertEquals(1, WorkoutEngine(tpl(HandMode.SINGLE), prepareMs = 0).snapshot().panels.size)
        assertEquals(2, WorkoutEngine(tpl(HandMode.BOTH_SEQUENTIAL), prepareMs = 0).snapshot().panels.size)
    }
}

private fun m(hand: Hand, kg: Double, at: Long, exercise: Exercise, gripId: Long? = null) =
    Measurement(exercise = exercise, hand = hand, kg = kg, epochMillis = at, source = MeasurementSource.SCALE, gripId = gripId)

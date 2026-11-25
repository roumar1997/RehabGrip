package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.EnginePhase
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.ToFailure
import com.rehabgrip.core.domain.workout.WorkoutEngine
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Round4Test {
    private val library = GripSpec.predefined().mapIndexed { i, g -> g.copy(id = i + 1L) }

    @Test
    fun defaultGripSessionHasSixSetsInOrderForBothHands() {
        val t = assertNotNull(WorkoutTemplate.gripSession(library))
        assertEquals("Entreno de agarre", t.name)
        assertEquals(listOf(6, 6, 2, 2, 2, 2), t.sets.map { it.reps })
        assertTrue(t.sets.all { it.workSeconds == 10 && it.restSeconds == 20 })
        assertEquals(HandMode.ALTERNATING, t.handMode)
        val names = t.sets.map { (it.grip as com.rehabgrip.core.domain.workout.Grip.Library).grip.name }
        assertEquals(
            listOf("Semiarqueo 4 dedos", "Extensión 3 dedos", "Bidedo estirado", "Bidedo pistola", "Bidedo arqueado", "Bidedo pistola arqueado"),
            names,
        )
        assertTrue(t.validate().isEmpty())
        // Strict alternation: 20 reps per hand.
        val slots = WorkoutEngine.buildSlots(t)
        assertEquals(40, slots.size)
        assertEquals(listOf(Hand.RIGHT, Hand.LEFT), slots.take(2).map { it.hand })
    }

    @Test
    fun gripSessionIsNullWhenAGripIsMissing() {
        assertNull(WorkoutTemplate.gripSession(library.take(3)))
    }

    @Test
    fun eachHandHasItsOwnTargetAndFailureMode() {
        val set = WorkoutSet(reps = 2, workSeconds = 5, restSeconds = 5)
            .withHand(Hand.RIGHT, SetTarget.Kg(10.0, 20.0), ToFailure.OFF)
            .withHand(Hand.LEFT, SetTarget.PercentOfRecord(70, 80), ToFailure.TIME)
        assertEquals(SetTarget.Kg(10.0, 20.0), set.targetFor(Hand.RIGHT))
        assertEquals(SetTarget.PercentOfRecord(70, 80), set.targetFor(Hand.LEFT))
        assertEquals(ToFailure.TIME, set.toFailureFor(Hand.LEFT))
        val t = WorkoutTemplate(name = "t", sets = listOf(set), handMode = HandMode.ALTERNATING)
        val e = WorkoutEngine(t, recordKg = { h, _ -> if (h == Hand.LEFT) 50.0 else null }, prepareMs = 0)
        // Right is first: its own kg band applies.
        assertEquals(10.0, e.snapshot().zone!!.minKg)
        e.run(7_000, 15.0)
        assertEquals(Hand.LEFT, e.snapshot().slot!!.hand)
        assertEquals(35.0, e.snapshot().zone!!.minKg) // 70 % of THAT hand's record
    }

    @Test
    fun leftWithoutOverrideFollowsTheRightHand() {
        val set = WorkoutSet(reps = 1, workSeconds = 5, restSeconds = 5, target = SetTarget.Kg(5.0, 9.0), toFailure = ToFailure.REPS)
        assertEquals(SetTarget.Kg(5.0, 9.0), set.targetFor(Hand.LEFT))
        assertEquals(ToFailure.REPS, set.toFailureFor(Hand.LEFT))
    }

    @Test
    fun repsToFailureOnOneHandOnlyExtendsThatHand() {
        val set = WorkoutSet(reps = 2, workSeconds = 5, restSeconds = 5).withHand(Hand.LEFT, toFailure = ToFailure.REPS)
        val slots = WorkoutEngine.buildSlots(WorkoutTemplate(name = "t", sets = listOf(set), handMode = HandMode.ALTERNATING))
        assertEquals(2, slots.count { it.hand == Hand.RIGHT })
        assertEquals(WorkoutEngine.MAX_REPS_TO_FAILURE, slots.count { it.hand == Hand.LEFT })
    }

    private fun WorkoutEngine.run(ms: Long, kg: Double) = (1..ms / 100).forEach { tick(100, kg) }
}

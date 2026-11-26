package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.EnginePhase
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.WorkoutEngine
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandoverTest {
    private fun engine(handover: Int, rest: Int = 2) = WorkoutEngine(
        WorkoutTemplate(
            name = "t", sets = listOf(WorkoutSet(reps = 2, workSeconds = 2, restSeconds = rest)),
            handMode = HandMode.ALTERNATING, firstHand = Hand.RIGHT, handoverSeconds = handover,
        ),
        prepareMs = 0,
    )

    private fun WorkoutEngine.run(ms: Long, kg: Double) = repeat((ms / 100).toInt()) { tick(100, kg) }

    @Test fun afterTheRightHandTheLeftOneGetsAShortChangeCountdownWithoutPenalty() {
        val e = engine(handover = 3)
        e.run(2_200, 20.0) // right pulls its 2 s
        assertEquals(Hand.LEFT, e.snapshot().slot!!.hand)
        assertEquals(EnginePhase.HANDOVER, e.snapshot().phase)
        assertTrue(e.snapshot().displayMs in 1..3_000)
        e.run(2_000, 20.0) // force is applied during the pause: it does not start nor count
        assertEquals(EnginePhase.HANDOVER, e.snapshot().phase)
        assertEquals(1, e.snapshot().results.size)
    }

    @Test fun waitingForeverNeverFailsAndTheHandInTurnPullsFirst() {
        val e = engine(handover = 3)
        e.run(2_200, 20.0)
        e.run(60_000, 0.0) // a minute of fatigue: nobody pulls
        assertEquals(EnginePhase.WAITING, e.snapshot().phase)
        assertEquals(1, e.snapshot().results.size)
        assertFalse(e.snapshot().results.any { it.failed })
        e.run(300, 20.0) // pulls now: it is the LEFT hand's turn
        assertEquals(EnginePhase.PULL, e.snapshot().phase)
        assertEquals(Hand.LEFT, e.snapshot().slot!!.hand)
    }

    @Test fun zeroSecondsMeansNoPause() {
        val e = engine(handover = 0)
        e.run(2_200, 20.0)
        assertEquals(Hand.LEFT, e.snapshot().slot!!.hand)
        assertTrue(e.snapshot().phase != EnginePhase.HANDOVER)
    }

    @Test fun eachHandRestsFromTheEndOfItsOwnPull() {
        val e = engine(handover = 0, rest = 5)
        e.run(2_200, 20.0) // right pulled
        e.run(2_200, 20.0) // left pulled right away
        // right ended ~2.3 s ago of its 5 s rest: still resting, although the left finished later
        val s = e.snapshot()
        assertEquals(Hand.RIGHT, s.slot!!.hand)
        assertEquals(EnginePhase.REST, s.phase)
        assertTrue(s.displayMs in 1_500..3_200, "right rest keeps counting from its own pull, was ${s.displayMs}")
    }
}

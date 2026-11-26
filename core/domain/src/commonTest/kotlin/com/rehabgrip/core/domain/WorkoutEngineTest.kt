package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.model.ForceSample
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.EngineEvent
import com.rehabgrip.core.domain.workout.EnginePhase
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.SetHand
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.StartMode
import com.rehabgrip.core.domain.workout.TargetZone
import com.rehabgrip.core.domain.workout.ToFailure
import com.rehabgrip.core.domain.workout.WorkoutEngine
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import com.rehabgrip.core.domain.workout.ZoneStats
import com.rehabgrip.core.domain.workout.ZoneStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutEngineTest {
    private fun tpl(vararg sets: WorkoutSet, mode: HandMode = HandMode.SINGLE) =
        WorkoutTemplate(name = "t", sets = sets.toList(), handMode = mode, firstHand = Hand.LEFT, handoverSeconds = 0)

    /** Ticks [ms] in 100 ms steps with constant [kg]; returns all events. */
    private fun WorkoutEngine.run(ms: Long, kg: Double): List<EngineEvent> =
        (1..ms / 100).flatMap { tick(100, kg) }

    @Test
    fun exampleTemplateEstimatesNineFortyFive() {
        val example = WorkoutTemplate.example()
        assertEquals(listOf(6, 6, 2, 2, 2, 2), example.sets.map { it.reps })
        assertEquals((9 * 60 + 45) * 1000L, WorkoutEngine.estimateDurationMs(example))
        assertTrue(example.validate().isEmpty())
    }

    @Test
    fun alternatingTurnIsStrictWhenBothRestsAreOver() {
        val e = WorkoutEngine(tpl(WorkoutSet(reps = 2, workSeconds = 2, restSeconds = 3), mode = HandMode.ALTERNATING), prepareMs = 0)
        e.run(2_100, 20.0) // LEFT pulls rep 1 (the first tick starts it)
        e.run(2_100, 20.0) // RIGHT pulls rep 1 (its rest was 0)
        // User gets distracted for 10 s with no force: both rests expire, nothing starts (weight-free time mode starts
        // immediately, so use the snapshot to check order instead).
        val s = e.snapshot()
        assertEquals(Hand.LEFT, s.slot!!.hand, "after L,R the turn is LEFT again")
        val results = e.snapshot().results.map { it.slot.hand }
        assertEquals(listOf(Hand.LEFT, Hand.RIGHT), results)
    }

    @Test
    fun bothRestedOnlyTurnHandIsCredited() {
        val set = WorkoutSet(reps = 2, workSeconds = 2, restSeconds = 1, startMode = StartMode.WEIGHT)
        val e = WorkoutEngine(tpl(set, mode = HandMode.ALTERNATING), prepareMs = 0)
        e.run(2_100, 20.0) // LEFT starts and pulls 2 s
        e.run(2_100, 20.0) // RIGHT pulls (2 s) right away
        e.run(5_000, 0.0) // distracted: both rests over, no force
        val waiting = e.snapshot()
        assertEquals(EnginePhase.WAITING, waiting.phase)
        assertEquals(Hand.LEFT, waiting.slot!!.hand)
        assertTrue(waiting.otherHandReady, "RIGHT is ready but must wait")
        val events = e.run(2_200, 20.0)
        val ended = events.filterIsInstance<EngineEvent.PullEnded>().map { it.result.slot.hand }
        assertEquals(listOf(Hand.LEFT), ended, "the pull goes only to the hand in turn")
        assertEquals(Hand.RIGHT, e.snapshot().slot!!.hand)
    }

    @Test
    fun turnHandStillRestingMakesOtherWaitEvenIfReady() {
        val set = WorkoutSet(reps = 2, workSeconds = 1, restSeconds = 6, startMode = StartMode.WEIGHT)
        val e = WorkoutEngine(tpl(set, mode = HandMode.ALTERNATING), prepareMs = 0)
        e.run(1_100, 20.0) // L
        e.run(1_100, 20.0) // R
        val s = e.snapshot() // L needs ~4 s more rest
        assertEquals(EnginePhase.REST, s.phase)
        assertEquals(Hand.LEFT, s.slot!!.hand)
        assertTrue(s.displayMs in 3_000..5_000)
        val events = e.run(2_000, 30.0) // strong pull while L rests: nobody is credited
        assertTrue(events.none { it is EngineEvent.PullStarted })
    }

    @Test
    fun skipTurnPassesToOtherHand() {
        val set = WorkoutSet(reps = 1, workSeconds = 1, restSeconds = 0, startMode = StartMode.WEIGHT)
        val e = WorkoutEngine(tpl(set, mode = HandMode.ALTERNATING), prepareMs = 0)
        e.tick(100, 0.0)
        assertEquals(Hand.LEFT, e.snapshot().slot!!.hand)
        val ev = e.skipTurn()
        assertTrue(ev.first() is EngineEvent.TurnSkipped)
        assertEquals(Hand.RIGHT, e.snapshot().slot!!.hand)
        assertTrue(e.snapshot().results.single().skipped)
    }

    @Test
    fun timerNeverRunsBeforeReachingTheThreshold() {
        val set = WorkoutSet(reps = 1, workSeconds = 3, restSeconds = 0, thresholdKg = 5.0)
        val e = WorkoutEngine(tpl(set), prepareMs = 0)
        e.run(30_000, 3.0) // far below the threshold, whatever the start mode
        assertEquals(EnginePhase.WAITING, e.snapshot().phase)
        assertTrue(e.snapshot().results.isEmpty())
        e.run(500, 10.0)
        assertEquals(EnginePhase.PULL, e.snapshot().phase)
        assertEquals(3_000L - 400, e.snapshot().displayMs, "first tick starts the pull, the clock follows")
    }

    @Test
    fun workTimeOnlyCountsWhileAboveThreshold() {
        val set = WorkoutSet(reps = 1, workSeconds = 4, restSeconds = 0, thresholdKg = 5.0)
        val e = WorkoutEngine(tpl(set), prepareMs = 0)
        e.run(2_000, 10.0)
        val before = e.snapshot().displayMs
        e.run(1_000, 1.0) // short dip (< 1.5 s): the clock stops, the rep goes on
        assertEquals(before, e.snapshot().displayMs)
        assertEquals(EnginePhase.PULL, e.snapshot().phase)
        e.run(2_100, 10.0) // pulls again and finishes the 4 s
        assertEquals(EnginePhase.DONE, e.snapshot().phase)
        assertFalse(e.snapshot().results.single().failed)
    }

    @Test
    fun droppingBelowThresholdForTooLongMarksFailureAndGoesToRest() {
        val set = WorkoutSet(reps = 2, workSeconds = 5, restSeconds = 10, thresholdKg = 5.0)
        val e = WorkoutEngine(tpl(set), prepareMs = 0)
        e.run(1_000, 10.0)
        e.run(1_700, 1.0) // more than 1.5 s under the threshold
        val first = e.snapshot().results.single()
        assertTrue(first.failed)
        assertEquals(EnginePhase.REST, e.snapshot().phase)
        assertEquals(2, e.snapshot().slot!!.rep, "plain sets continue with the next rep")
    }

    @Test
    fun repsToFailureStopsSetWhenARepIsNotHeld() {
        val set = WorkoutSet(reps = 3, workSeconds = 2, restSeconds = 1, toFailure = ToFailure.REPS)
        val next = WorkoutSet(reps = 1, workSeconds = 1, restSeconds = 0)
        val e = WorkoutEngine(tpl(set, next), prepareMs = 0)
        e.run(2_000, 20.0) // rep 1 ok
        e.run(1_000, 0.0) // rest
        e.run(1_500, 20.0)
        e.run(1_700, 0.0) // rep 2 drops under the threshold for over 1.5 s: fail
        assertTrue(e.snapshot().results.last().failed)
        assertEquals(1, e.snapshot().slot!!.setIndex, "jumps to the next set")
    }

    @Test
    fun timeToFailureEndsWhenForceDrops() {
        val set = WorkoutSet(reps = 1, workSeconds = 5, restSeconds = 0, toFailure = ToFailure.TIME)
        val e = WorkoutEngine(tpl(set), prepareMs = 0)
        e.run(8_000, 20.0)
        assertEquals(EnginePhase.PULL, e.snapshot().phase, "open-ended beyond work seconds")
        e.run(1_700, 0.5)
        assertEquals(EnginePhase.DONE, e.snapshot().phase)
    }

    @Test
    fun percentOfRecordTargetUsesHandRecord() {
        val set = WorkoutSet(reps = 1, workSeconds = 1, restSeconds = 0, target = SetTarget.PercentOfRecord(70, 80))
        val e = WorkoutEngine(tpl(set), recordKg = { h, _ -> if (h == Hand.LEFT) 40.0 else null }, prepareMs = 0)
        assertEquals(TargetZone(28.0, 32.0), e.snapshot().zone)
        assertNull(SetTarget.PercentOfRecord(70, 80).resolve(null))
    }

    @Test
    fun sequentialDoesAllSetsPerHandAndPinnedSetsRespectHand() {
        val t = tpl(WorkoutSet(2, 1, 0), WorkoutSet(1, 1, 0, hand = SetHand.RIGHT), mode = HandMode.BOTH_SEQUENTIAL)
        val hands = WorkoutEngine.buildSlots(t).map { it.hand }
        assertEquals(listOf(Hand.LEFT, Hand.LEFT, Hand.RIGHT, Hand.RIGHT, Hand.RIGHT), hands)
    }

    @Test
    fun validation() {
        assertEquals(2, tpl().copy(name = " ").validate().size)
        assertFailsWith<IllegalArgumentException> { TargetZone(30.0, 20.0) }
    }
}

class ZoneStatsTest {
    @Test
    fun timeInZoneOnlyCountsPulls() {
        val zone = TargetZone(20.0, 30.0)
        assertEquals(ZoneStatus.ABOVE, zone.statusOf(31.0))
        val stats = ZoneStats()
        stats.add(ForceSample(0, 0.0), Hand.RIGHT, pulling = false, zone = zone)
        listOf(25.0, 25.0, 35.0, 10.0).forEachIndexed { i, kg ->
            stats.add(ForceSample((i + 1) * 100L, kg), Hand.RIGHT, pulling = true, zone = zone)
        }
        stats.add(ForceSample(500, 50.0), Hand.RIGHT, pulling = false, zone = zone)
        val r = stats.results().single()
        assertEquals(35.0, r.maxKg)
        assertEquals(50.0, r.inZonePercent!!, 0.01)
        assertEquals(23.75, r.avgPullKg, 0.01)
    }
}

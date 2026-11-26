package com.rehabgrip.core.domain.workout

import com.rehabgrip.core.domain.model.Hand

/** One scheduled pull, in strict turn order. [setIndex] 0-based, [rep] 1-based. */
data class Slot(val setIndex: Int, val rep: Int, val hand: Hand)

/** HANDOVER = «Cambia a IZQUIERDA · 3…2…1»: a short pause when the other hand is up next. */
enum class EnginePhase { PREPARE, REST, HANDOVER, WAITING, PULL, DONE }

/** What one hand is doing right now: pulling (TIRA), resting (DESCANSA) or waiting for its turn (ESPERA). */
enum class HandState { PULL, REST, WAIT }

/** Per-hand view for the two-column screen. [displayMs] is that hand's own timer (0 when none). */
data class HandPanel(val hand: Hand, val state: HandState, val displayMs: Long, val inTurn: Boolean)

data class RepResult(
    val slot: Slot,
    val peakKg: Double,
    val durationMs: Long,
    val failed: Boolean = false,
    val skipped: Boolean = false,
)

sealed interface EngineEvent {
    data class CountdownTick(val secondsLeft: Int) : EngineEvent
    data class PullStarted(val slot: Slot) : EngineEvent
    data class PullEnded(val result: RepResult) : EngineEvent
    data class TurnSkipped(val slot: Slot) : EngineEvent
    data object Finished : EngineEvent
}

data class EngineSnapshot(
    val phase: EnginePhase,
    val slot: Slot?,
    /** Countdown shown big: prepare / rest of the turn hand / work left (elapsed when open-ended). */
    val displayMs: Long,
    val zone: TargetZone?,
    /** Alternating only: the hand that is NOT in turn already finished its rest. */
    val otherHandReady: Boolean,
    val otherHand: Hand?,
    val pullsDone: Int,
    val results: List<RepResult>,
    /** Panels of the hands taking part (right first), for the split screen. */
    val panels: List<HandPanel> = emptyList(),
)

/**
 * Pure state machine for a guided session. Each hand has its own rest timer, but the
 * turn is strict: only the hand of the current [Slot] may start a pull, even when the
 * other one is rested too. A pull is credited only to the hand in turn.
 *
 * [recordKg] gives the last record of a hand for a grip (for % targets).
 */
class WorkoutEngine(
    private val template: WorkoutTemplate,
    private val recordKg: (Hand, Grip) -> Double? = { _, _ -> null },
    private val prepareMs: Long = PREPARE_MS,
) {
    private val slots: MutableList<Slot> = buildSlots(template).toMutableList()
    private val rest = mutableMapOf(Hand.RIGHT to 0L, Hand.LEFT to 0L)
    private val results = mutableListOf<RepResult>()
    private var index = 0
    private var prepareLeft = prepareMs
    private var pulling = false
    private var pullMs = 0L
    private var belowMs = 0L
    private var handoverLeft = 0L
    private val handoverMs = template.handoverSeconds.coerceIn(0, WorkoutTemplate.MAX_HANDOVER_SECONDS) * 1000L
    private var peak = 0.0
    private var lastCountdown = Int.MAX_VALUE

    val alternating: Boolean = template.handMode == HandMode.ALTERNATING
    private val participants: List<Hand> = Hand.entries.filter { h -> slots.any { it.hand == h } }

    private val current: Slot? get() = slots.getOrNull(index)
    private fun setOf(slot: Slot) = template.sets[slot.setIndex]

    fun zoneFor(slot: Slot): TargetZone? = setOf(slot).targetFor(slot.hand).resolve(recordKg(slot.hand, setOf(slot).grip))

    /** Advances time by [dtMs] with the latest force reading [kg]. */
    fun tick(dtMs: Long, kg: Double): List<EngineEvent> {
        val events = mutableListOf<EngineEvent>()
        if (prepareLeft > 0) {
            prepareLeft = (prepareLeft - dtMs).coerceAtLeast(0)
            countdown(prepareLeft, events)
            if (prepareLeft > 0) return events
        } else {
            rest.keys.forEach { h -> rest[h] = (rest.getValue(h) - dtMs).coerceAtLeast(0) }
            handoverLeft = (handoverLeft - dtMs).coerceAtLeast(0)
        }
        val slot = current ?: return events
        val set = setOf(slot)
        if (!pulling) {
            val left = rest.getValue(slot.hand)
            countdown(left, events)
            if (left > 0) return events
            // Short «Cambia a…» pause: no penalty, and waiting for the user afterwards never fails a rep.
            countdown(handoverLeft, events)
            if (handoverLeft > 0) return events
            // The clock never runs until the force reaches the threshold.
            val startKg = if (set.startMode == StartMode.WEIGHT) maxOf(set.thresholdKg, zoneFor(slot)?.minKg ?: 0.0) else set.thresholdKg
            if (kg < startKg) return events
            pulling = true
            pullMs = 0
            belowMs = 0
            peak = kg
            lastCountdown = Int.MAX_VALUE
            events += EngineEvent.PullStarted(slot)
            return events
        }
        // Work time only counts while pulling at or above the threshold.
        val above = kg >= set.thresholdKg
        if (above) {
            pullMs += dtMs
            belowMs = 0
        } else {
            belowMs += dtMs
        }
        peak = maxOf(peak, kg)
        val workMs = set.workSeconds * 1000L
        val dropped = belowMs > FAIL_BELOW_MS
        when (set.toFailureFor(slot.hand)) {
            ToFailure.TIME -> if (dropped || pullMs >= MAX_OPEN_PULL_MS) endPull(slot, failed = false, events)
            ToFailure.REPS -> when {
                pullMs >= workMs -> endPull(slot, failed = false, events)
                dropped -> {
                    endPull(slot, failed = true, events)
                    dropRemainingReps(slot)
                }
            }
            ToFailure.OFF -> when {
                pullMs >= workMs -> endPull(slot, failed = false, events)
                dropped -> endPull(slot, failed = true, events) // «Parada: fallo», on to the rest
            }
        }
        if (current == null && events.none { it is EngineEvent.Finished }) events += EngineEvent.Finished
        return events
    }

    /** Gives up the current turn (only when not pulling). The other hand is next. */
    fun skipTurn(): List<EngineEvent> {
        val slot = current ?: return emptyList()
        if (pulling) return emptyList()
        results += RepResult(slot, 0.0, 0, skipped = true)
        index++
        handoverLeft = 0
        return listOfNotNull(EngineEvent.TurnSkipped(slot), if (current == null) EngineEvent.Finished else null)
    }

    fun snapshot(): EngineSnapshot {
        val slot = current
        val set = slot?.let(::setOf)
        val phase = when {
            slot == null -> EnginePhase.DONE
            prepareLeft > 0 -> EnginePhase.PREPARE
            pulling -> EnginePhase.PULL
            rest.getValue(slot.hand) > 0 -> EnginePhase.REST
            handoverLeft > 0 -> EnginePhase.HANDOVER
            else -> EnginePhase.WAITING
        }
        val display = when (phase) {
            EnginePhase.PREPARE -> prepareLeft
            EnginePhase.REST -> rest.getValue(slot!!.hand)
            EnginePhase.HANDOVER -> handoverLeft
            EnginePhase.PULL -> if (set!!.toFailureFor(slot!!.hand) == ToFailure.TIME) pullMs else (set.workSeconds * 1000L - pullMs).coerceAtLeast(0)
            else -> 0
        }
        val other = slot?.hand?.other?.takeIf { alternating }
        return EngineSnapshot(
            phase = phase,
            slot = slot,
            displayMs = display,
            zone = slot?.let(::zoneFor),
            otherHandReady = other != null && rest.getValue(other) == 0L && phase != EnginePhase.PREPARE,
            otherHand = other,
            pullsDone = results.count { !it.skipped },
            results = results.toList(),
            panels = panels(slot, phase, display),
        )
    }

    private fun panels(slot: Slot?, phase: EnginePhase, display: Long): List<HandPanel> = participants.map { h ->
        when {
            slot == null -> HandPanel(h, HandState.WAIT, 0, inTurn = false)
            h == slot.hand -> when (phase) {
                EnginePhase.PREPARE -> HandPanel(h, HandState.WAIT, display, inTurn = true)
                EnginePhase.REST -> HandPanel(h, HandState.REST, display, inTurn = true)
                EnginePhase.HANDOVER -> HandPanel(h, HandState.WAIT, display, inTurn = true)
                else -> HandPanel(h, HandState.PULL, if (phase == EnginePhase.PULL) display else 0, inTurn = true)
            }
            phase != EnginePhase.PREPARE && rest.getValue(h) > 0 -> HandPanel(h, HandState.REST, rest.getValue(h), inTurn = false)
            else -> HandPanel(h, HandState.WAIT, 0, inTurn = false)
        }
    }

    /** Slots still to run (for "rep 3/6"). */
    fun repsInSet(slot: Slot): Int = slots.count { it.setIndex == slot.setIndex && it.hand == slot.hand }

    private fun endPull(slot: Slot, failed: Boolean, events: MutableList<EngineEvent>) {
        val result = RepResult(slot, peak, pullMs, failed = failed)
        results += result
        pulling = false
        belowMs = 0
        index++
        rest[slot.hand] = if (current == null) 0 else setOf(slot).restSeconds * 1000L
        // The rest of THIS hand counts from the end of ITS pull; the other hand is up next.
        handoverLeft = if (current != null && current!!.hand != slot.hand) handoverMs else 0L
        events += EngineEvent.PullEnded(result)
    }

    private fun dropRemainingReps(failedSlot: Slot) {
        val tail = slots.subList(index, slots.size)
        tail.removeAll { it.setIndex == failedSlot.setIndex && it.hand == failedSlot.hand }
    }

    private fun countdown(leftMs: Long, events: MutableList<EngineEvent>) {
        val secs = ((leftMs + 999) / 1000).toInt()
        if (leftMs > 0 && secs <= 3 && secs < lastCountdown) {
            events += EngineEvent.CountdownTick(secs)
        }
        lastCountdown = if (leftMs == 0L) Int.MAX_VALUE else secs
    }

    companion object {
        const val PREPARE_MS = 5_000L

        /** Below the threshold longer than this ends the rep as a failure. */
        const val FAIL_BELOW_MS = 1_500L
        const val MAX_OPEN_PULL_MS = 300_000L
        const val MAX_REPS_TO_FAILURE = 50

        /** Strict turn order for the whole session. */
        fun buildSlots(t: WorkoutTemplate): List<Slot> {
            val a = t.firstHand
            val b = a.other
            fun handsFor(set: WorkoutSet): List<Hand> = when (set.hand) {
                SetHand.RIGHT -> listOf(Hand.RIGHT)
                SetHand.LEFT -> listOf(Hand.LEFT)
                SetHand.SESSION -> if (t.handMode == HandMode.SINGLE) listOf(a) else listOf(a, b)
            }
            fun reps(set: WorkoutSet, hand: Hand) = if (set.toFailureFor(hand) == ToFailure.REPS) MAX_REPS_TO_FAILURE else set.reps
            if (t.handMode == HandMode.BOTH_SEQUENTIAL) {
                return listOf(a, b).flatMap { hand ->
                    t.sets.withIndex().filter { hand in handsFor(it.value) }
                        .flatMap { (i, s) -> (1..reps(s, hand)).map { Slot(i, it, hand) } }
                }
            }
            return t.sets.withIndex().flatMap { (i, s) ->
                val hands = handsFor(s)
                val maxReps = hands.maxOf { reps(s, it) }
                (1..maxReps).flatMap { rep -> hands.filter { rep <= reps(s, it) }.map { Slot(i, rep, it) } }
            }
        }

        /** «Duración estimada»: runs the plan as if every rep went to schedule. */
        fun estimateDurationMs(t: WorkoutTemplate): Long {
            val plain = t.copy(sets = t.sets.map { it.copy(toFailure = ToFailure.OFF, leftToFailure = ToFailure.OFF, startMode = StartMode.TIME) })
            val engine = WorkoutEngine(plain)
            var elapsed = 0L
            while (engine.current != null && elapsed < MAX_ESTIMATE_MS) {
                engine.tick(STEP_MS, 1_000.0)
                elapsed += STEP_MS
            }
            return elapsed
        }

        private const val STEP_MS = 1_000L
        private const val MAX_ESTIMATE_MS = 6L * 60 * 60 * 1000
    }
}

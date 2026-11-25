package com.rehabgrip.core.domain.workout

import com.rehabgrip.core.domain.model.ForceSample
import com.rehabgrip.core.domain.model.Hand

data class HandWorkoutResult(
    val hand: Hand,
    val maxKg: Double,
    /** Average force while pulling. */
    val avgPullKg: Double,
    /** % of pulling time inside the target zone (null when no set had a zone). */
    val inZonePercent: Double?,
    val reps: Int,
)

/**
 * Accumulates force only while pulling, per hand. Time in zone is weighted by the
 * gap between samples (capped so a lost signal does not count).
 */
class ZoneStats {
    private class Acc {
        var max = 0.0
        var sumKgMs = 0.0
        var pullMs = 0L
        var zonedMs = 0L
        var inZoneMs = 0L
    }

    private val acc = mutableMapOf<Hand, Acc>()
    private val reps = mutableMapOf<Hand, Int>()
    private var last: ForceSample? = null

    fun add(sample: ForceSample, hand: Hand, pulling: Boolean, zone: TargetZone?) {
        val prev = last
        last = sample
        if (!pulling) return
        val a = acc.getOrPut(hand) { Acc() }
        a.max = maxOf(a.max, sample.kg)
        val dt = if (prev == null) 0L else (sample.tMillis - prev.tMillis).coerceIn(0, MAX_GAP_MS)
        a.pullMs += dt
        a.sumKgMs += sample.kg * dt
        if (zone != null) {
            a.zonedMs += dt
            if (zone.statusOf(sample.kg) == ZoneStatus.IN) a.inZoneMs += dt
        }
    }

    fun repDone(hand: Hand) {
        reps[hand] = (reps[hand] ?: 0) + 1
    }

    fun results(): List<HandWorkoutResult> = acc.map { (hand, a) ->
        HandWorkoutResult(
            hand = hand,
            maxKg = a.max,
            avgPullKg = if (a.pullMs == 0L) 0.0 else a.sumKgMs / a.pullMs,
            inZonePercent = if (a.zonedMs == 0L) null else a.inZoneMs * 100.0 / a.zonedMs,
            reps = reps[hand] ?: 0,
        )
    }.sortedBy { it.hand.ordinal }

    private companion object {
        const val MAX_GAP_MS = 500L
    }
}

/** A finished guided session as stored in history. */
data class WorkoutResultEntry(
    val name: String,
    val epochMillis: Long,
    val hands: List<HandWorkoutResult>,
)

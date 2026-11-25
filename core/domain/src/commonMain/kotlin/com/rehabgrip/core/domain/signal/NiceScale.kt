package com.rehabgrip.core.domain.signal

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Round axis: ticks at 0, [step], 2*[step]... up to [max] (a multiple of [step]). */
data class NiceScale(val step: Double, val max: Double) {
    val tickCount: Int get() = (max / step + 0.5).toInt() + 1

    fun tick(i: Int): Double = step * i
}

/**
 * Picks a round step (1, 2, 5 x 10^n) so that [maxValue] fits in about [targetTicks]
 * divisions and the axis ends on a multiple of the step: 37 kg gives 0, 10, 20, 30, 40.
 */
fun niceScale(maxValue: Double, targetTicks: Int = 4, minMax: Double = 10.0): NiceScale {
    val top = maxOf(abs(maxValue), minMax)
    val raw = top / targetTicks.coerceAtLeast(1)
    val magnitude = 10.0.pow(floor(log10(raw)))
    val fraction = raw / magnitude
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    val step = nice * magnitude
    return NiceScale(step, ceil(top / step - 1e-9) * step)
}

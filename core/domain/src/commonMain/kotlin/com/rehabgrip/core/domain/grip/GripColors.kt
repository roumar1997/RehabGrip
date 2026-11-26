package com.rehabgrip.core.domain.grip

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One colour: [base] (right hand, borders), [tint] (left hand fill), [dark] (letters on the tint), [onBase] (letters on the base). ARGB. */
data class GripColor(val base: Long, val tint: Long, val dark: Long, val onBase: Long = WHITE) {
    companion object {
        const val WHITE = 0xFFFFFFFF
        private const val INK = 0xFF1A1A1A

        /** Tint and letter tones derived from [base]; letters on the base turn dark when the base is light. */
        fun derive(base: Long) = GripColor(base, ColorMath.mix(base, 0xFFFFFFFF, 0.78), ColorMath.mix(base, 0xFF000000, 0.5), if (ColorMath.luminance(base) > 0.5) INK else WHITE)
    }
}

/** Colour maths shared by the palette, the generator and the editor validation. */
object ColorMath {
    /** Smallest CIE76 distance two grips' colours may have. */
    const val MIN_DELTA_E = 20.0

    private fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()

    fun mix(a: Long, b: Long, t: Double): Long {
        fun m(shift: Int) = (channel(a, shift) + (channel(b, shift) - channel(a, shift)) * t).toInt().coerceIn(0, 255)
        return 0xFF000000 or (m(16).toLong() shl 16) or (m(8).toLong() shl 8) or m(0).toLong()
    }

    private fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    fun luminance(c: Long): Double = 0.2126 * lin(channel(c, 16)) + 0.7152 * lin(channel(c, 8)) + 0.0722 * lin(channel(c, 0))

    private fun lab(c: Long): DoubleArray {
        val r = lin(channel(c, 16)); val g = lin(channel(c, 8)); val b = lin(channel(c, 0))
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    fun deltaE(a: Long, b: Long): Double {
        val p = lab(a); val q = lab(b)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    fun farFrom(c: Long, others: Collection<Long>) = others.all { deltaE(c, it) >= MIN_DELTA_E }

    /** HSL (hue 0..360, saturation and lightness 0..1) to opaque ARGB. */
    fun hsl(h: Double, s: Double, l: Double): Long {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = ((h % 360 + 360) % 360) / 60
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) { 0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x); 3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x) }
        val m = l - c / 2
        fun v(d: Double) = ((d + m) * 255).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000 or (v(r1) shl 16) or (v(g1) shl 8) or v(b1)
    }

    fun hueOf(c: Long): Double {
        val r = channel(c, 16) / 255.0; val g = channel(c, 8) / 255.0; val b = channel(c, 0) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        if (d == 0.0) return 0.0
        val h = when (mx) { r -> ((g - b) / d) % 6; g -> (b - r) / d + 2; else -> (r - g) / d + 4 }
        return (h * 60 + 360) % 360
    }
}

/** Sixteen curated colours (the first eight are the agreed predefined ones, in order) and a deterministic generator for more. */
object GripPalette {
    val colors = listOf(
        GripColor(0xFF2A78D6, 0xFFC6DCF8, 0xFF14407C),
        GripColor(0xFFEB6834, 0xFFFBD2C0, 0xFF8F3A0E),
        GripColor(0xFF1BAF7A, 0xFFBFEAD9, 0xFF0B5E41),
        GripColor(0xFFEDA100, 0xFFFBEAC7, 0xFF775100),
        GripColor(0xFFE87BA4, 0xFFFAE2EB, 0xFF743E52),
        GripColor(0xFF008300, 0xFFC7E4C7, 0xFF004200),
        GripColor(0xFF6250D6, 0xFFDCD9F6, 0xFF31286B),
        GripColor(0xFFE34948, 0xFFF9D7D7, 0xFF722524),
    ) + listOf(0xFF00A6B5, 0xFF8D5A3B, 0xFFA6C800, 0xFF8AD8F8, 0xFFD6197A, 0xFF1F2A6B, 0xFF6B7A1F, 0xFF5F7388).map { GripColor.derive(it) }
    val size get() = colors.size
    val bases: List<Long> get() = colors.map { it.base }

    /** Style colours of any base: a palette entry keeps its hand-made tones, the rest are derived. */
    fun of(base: Long): GripColor = colors.firstOrNull { it.base == base } ?: GripColor.derive(base)

    /**
     * The next colour that keeps at least [ColorMath.MIN_DELTA_E] from every colour in [taken]: first the palette,
     * then golden-angle hues with varying lightness. Deterministic, so the same library always gives the same colours.
     */
    fun nextFree(taken: Collection<Long>): Long {
        colors.firstOrNull { ColorMath.farFrom(it.base, taken) }?.let { return it.base }
        val lightness = doubleArrayOf(0.5, 0.38, 0.62, 0.3, 0.7)
        for (i in 0 until 5000) {
            val c = ColorMath.hsl(i * 137.508, 0.65, lightness[(i / 7) % lightness.size])
            if (ColorMath.farFrom(c, taken)) return c
        }
        return ColorMath.hsl(taken.size * 137.508, 0.65, 0.5)
    }
}

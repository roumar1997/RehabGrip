package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.progress.ChartLabels
import com.rehabgrip.core.domain.progress.ChartScroll
import com.rehabgrip.core.domain.progress.KgText
import com.rehabgrip.core.domain.progress.LabelPoint
import com.rehabgrip.core.domain.progress.LabelSpec
import com.rehabgrip.core.domain.progress.YScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartLabelsTest {
    private val spec = LabelSpec(height = 20f, gap = 2f, nudge = 5f, padX = 6f, minX = 0f, maxX = 1000f, unlimited = false, textWidth = { it.length * 8f })
    private fun yOf(kg: Float) = 400f - kg * 8f

    private fun pt(key: Int, line: Int, right: Boolean, day: Int, kg: Float, order: Int, count: Int) =
        LabelPoint(key, line, 0, day.toFloat(), right, 60f + day * 90f, yOf(kg), kg, 4f, order, count, true)

    @Test fun kgTextDropsUselessDecimals() {
        assertEquals("32", KgText.of(32.0f))
        assertEquals("31,5", KgText.of(31.5f))
        assertEquals("8", KgText.of(7.98f))
    }

    @Test fun higherHandAboveLowerBelow() {
        val l = ChartLabels.layout(listOf(pt(0, 0, true, 0, 20f, 0, 1), pt(1, 1, false, 0, 38f, 0, 1)), spec)
        assertFalse(l.labels.first { it.keys == listOf(0) }.above)
        assertTrue(l.labels.first { it.keys == listOf(1) }.above)
    }

    @Test fun crossedLinesSwapThatDay() {
        val l = ChartLabels.layout(
            listOf(pt(0, 0, true, 0, 20f, 0, 2), pt(1, 1, false, 0, 30f, 0, 2), pt(2, 0, true, 1, 40f, 1, 2), pt(3, 1, false, 1, 30f, 1, 2)),
            spec,
        )
        assertTrue(l.labels.first { it.keys == listOf(2) }.above)
        assertFalse(l.labels.first { it.keys == listOf(3) }.above)
        assertFalse(l.labels.first { it.keys == listOf(0) }.above)
        assertTrue(l.labels.first { it.keys == listOf(1) }.above)
    }

    @Test fun equalValuesPutLeftAbove() {
        val l = ChartLabels.layout(listOf(pt(0, 0, true, 0, 32f, 0, 1), pt(1, 1, false, 0, 32f, 0, 1)), spec)
        assertFalse(l.labels.first { it.keys == listOf(0) }.above)
        assertTrue(l.labels.first { it.keys == listOf(1) }.above)
    }

    @Test fun nearlyEqualPointsAreNudgedApart() {
        val l = ChartLabels.layout(listOf(pt(0, 0, true, 0, 32f, 0, 1), pt(1, 1, false, 0, 32f, 0, 1)), spec)
        assertEquals(55f, l.xs.getValue(0))
        assertEquals(65f, l.xs.getValue(1))
    }

    @Test fun collidingLabelsFuse() {
        val tight = LabelSpec(height = 20f, gap = -14f, nudge = 5f, padX = 6f, minX = 0f, maxX = 1000f, unlimited = false, textWidth = { it.length * 8f })
        val l = ChartLabels.layout(listOf(pt(0, 0, true, 0, 32f, 0, 1), pt(1, 1, false, 0, 32f, 0, 1)), tight)
        assertEquals(1, l.labels.size)
        assertTrue(l.labels[0].fused)
        assertEquals("32 · 32", l.labels[0].text)
        assertTrue(l.labels[0].above)
    }

    @Test fun firstAndLastAlwaysKeepTheirLabelThenMax() {
        val kgs = listOf(20f, 21f, 25f, 22f, 23f)
        val pts = kgs.mapIndexed { i, kg -> LabelPoint(i, 0, 0, i.toFloat(), true, 100f + i * 20f, 100f, kg, 4f, i, 5, true) }
        val keys = ChartLabels.layout(pts, spec).labels.flatMap { it.keys }
        assertTrue(0 in keys && 4 in keys && 2 in keys)
        assertFalse(1 in keys || 3 in keys)
    }

    @Test fun unlimitedShowsEverything() {
        val pts = (0 until 5).map { i -> LabelPoint(i, 0, 0, i.toFloat(), true, 100f + i * 20f, 100f, 20f + i, 4f, i, 5, true) }
        assertEquals(5, ChartLabels.layout(pts, LabelSpec(20f, 2f, 5f, 6f, 0f, 1000f, true) { 16f }).labels.size)
    }

    @Test fun scrollOnlyWhenDaysDoNotFit() {
        val fits = ChartScroll.width(300f, 10f, listOf(0f, 0.5f, 1f), 40f)
        assertFalse(fits.scrolls)
        assertEquals(300f, fits.contentPx)
        val many = ChartScroll.width(300f, 10f, (0..14).map { it / 14f }, 40f)
        assertTrue(many.scrolls)
        assertEquals(40f * 14 + 20f, many.contentPx, 0.01f)
        assertEquals(300f * ChartScroll.MAX_FACTOR, ChartScroll.width(300f, 10f, listOf(0f, 0.001f, 1f), 40f).contentPx)
        assertFalse(ChartScroll.width(300f, 10f, listOf(0f), 40f).scrolls)
    }

    @Test fun scaleAndGridEveryFive() {
        val s = YScale.of(11f, 38f)
        assertEquals(10f, s.min)
        assertEquals(40f, s.max)
        assertEquals(listOf(10f, 15f, 20f, 25f, 30f, 35f, 40f), s.grid().map { it.value })
        assertEquals(listOf(true, false, true, false, true, false, true), s.grid().map { it.labelled })
        assertEquals(50f, YScale.of(31f, 33f).max)
    }
}

package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.grip.GripPalette
import com.rehabgrip.core.domain.grip.GripStyles
import com.rehabgrip.core.domain.progress.ChartMode
import com.rehabgrip.core.domain.progress.ChartModeRules
import com.rehabgrip.core.domain.progress.DayAxis
import com.rehabgrip.core.domain.progress.Dodge
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Round9Test {
    private val library = GripSpec.predefined().mapIndexed { i, g -> g.copy(id = i + 1L) }

    @Test fun predefinedGripsKeepTheirAgreedColourAndLetters() {
        val s = GripStyles.resolve(library)
        val byName = library.associate { it.name to s.getValue(it.id) }
        assertEquals("SA" to 0, byName.getValue("Semiarqueo 4 dedos").let { it.code to it.colorIndex })
        assertEquals("E" to 1, byName.getValue("Extensión 3 dedos").let { it.code to it.colorIndex })
        assertEquals("SE" to 2, byName.getValue("Semiestirado 4 dedos").let { it.code to it.colorIndex })
        assertEquals("BE" to 3, byName.getValue("Bidedo estirado").let { it.code to it.colorIndex })
        assertEquals("BA" to 4, byName.getValue("Bidedo arqueado").let { it.code to it.colorIndex })
        assertEquals("BP" to 6, byName.getValue("Bidedo pistola").let { it.code to it.colorIndex })
        assertEquals("PA" to 7, byName.getValue("Bidedo pistola arqueado").let { it.code to it.colorIndex })
        assertEquals("DM" to 5, s.getValue(GripStyles.DYNAMOMETER_ID).let { it.code to it.colorIndex })
    }

    @Test fun paletteHasEightDistinctColours() {
        assertEquals(8, GripPalette.size)
        assertEquals(8, GripPalette.colors.map { it.base }.distinct().size)
        assertEquals(0xFF2A78D6, GripPalette.colors[0].base)
        assertEquals(0xFFE34948, GripPalette.colors[7].base)
    }

    @Test fun colourNeverDependsOnTheSelection() {
        val own = GripSpec(id = 20, name = "Regleta mala", edgeMm = 15)
        val a = GripStyles.resolve(library + own)
        val b = GripStyles.resolve((library + own).reversed())
        assertEquals(a, b)
        assertEquals(a, GripStyles.resolve(library + own))
    }

    @Test fun ownGripTakesFirstFreeColourAndKeepsTheStoredOne() {
        val own = GripSpec(id = 20, name = "Mala", edgeMm = 15)
        assertEquals(5, GripStyles.resolve(library + own).getValue(20).colorIndex) // the only slot no library grip uses
        assertEquals(5, GripStyles.nextFreeColor(library))
        assertEquals(2, GripStyles.resolve(library + own.copy(colorIndex = 2)).getValue(20).colorIndex)
    }

    @Test fun whenAllEightAreTakenColoursRepeatButLettersDiffer() {
        val eight = (1..8).map { GripSpec(id = it.toLong(), name = "Grip $it", colorIndex = it - 1) }
        val ninth = GripSpec(id = 9, name = "Nueve zeta")
        val s = GripStyles.resolve(eight + ninth)
        assertTrue(s.getValue(9).colorIndex in 0..7)
        assertEquals("NZ", s.getValue(9).code)
    }

    @Test fun initialsOfOwnGrips() {
        assertEquals("RM", GripStyles.codeOf(GripSpec(name = "Regleta mala")))
        assertEquals("ABG", GripStyles.codeOf(GripSpec(name = "alfa beta gamma delta")))
        assertEquals("20", GripStyles.codeOf(GripSpec(name = "", edgeMm = 20)))
        assertEquals("XY", GripStyles.codeOf(GripSpec(name = "Regleta mala", initials = "xy")))
        assertEquals("ABC", GripStyles.sanitize(" a-b c d"))
    }

    @Test fun dodgeSeparatesHandsAndGrips() {
        val r = Dodge.offsetDp(true, 0, 1)
        val l = Dodge.offsetDp(false, 0, 1)
        assertTrue(r < 0 && l > 0)
        val offsets = (0 until 3).flatMap { g -> listOf(true, false).map { Dodge.offsetDp(it, g, 3) } }
        assertEquals(6, offsets.distinct().size)
        assertEquals(0f, offsets.average().toFloat(), 0.001f)
    }

    @Test fun dodgeStaysInsideTheArea() {
        val offsets = (0 until 3).flatMap { g -> listOf(true, false).map { Dodge.offsetDp(it, g, 3) } }
        val placed = Dodge.place(base = 30f, offsets = offsets, min = 20f, max = 300f)
        assertTrue(placed.all { it in 20f..300f })
        assertEquals(offsets.size, placed.distinct().size)
        val right = Dodge.place(290f, offsets, 20f, 300f)
        assertTrue(right.all { it in 20f..300f })
    }

    @Test fun markersOfDifferentGripsLandOnDifferentPoints() {
        assertEquals(listOf(0, 1, 2), (0..2).map { Dodge.markerIndex(it, 4) })
        assertEquals(1, Dodge.markerIndex(3, 2))
        assertEquals(0, Dodge.markerIndex(5, 1))
    }

    @Test fun axisRunsFromFirstRecordToToday() {
        val now = Instant.parse("2026-11-26T10:00:00Z").toEpochMilliseconds()
        val first = LocalDate(2026, 11, 25)
        val axis = DayAxis.build(listOf(first), now, TimeZone.UTC)!!
        assertEquals(first, axis.first)
        assertEquals(LocalDate(2026, 11, 26), axis.last)
        assertEquals(0f, axis.fraction(first))
        assertEquals(1f, axis.fraction(axis.last))
        assertEquals(listOf(first, axis.last), axis.ticks().map { it.second })
    }

    @Test fun singleDayToday_pointStaysLeftWithOneTick() {
        val now = Instant.parse("2026-11-26T10:00:00Z").toEpochMilliseconds()
        val axis = DayAxis.build(listOf(LocalDate(2026, 11, 26)), now, TimeZone.UTC)!!
        assertEquals(0f, axis.fraction(LocalDate(2026, 11, 26)))
        assertEquals(1, axis.ticks().size)
    }

    @Test fun emptyRangeHasNoAxis() = assertEquals(null, DayAxis.build(emptyList(), 0, TimeZone.UTC))

    @Test fun defaultModeDependsOnHowManyGrips() {
        assertEquals(ChartMode.OVERLAID, ChartModeRules.default(1))
        assertEquals(ChartMode.OVERLAID, ChartModeRules.default(2))
        assertEquals(ChartMode.SEPARATE, ChartModeRules.default(3))
        assertEquals(ChartMode.SEPARATE, ChartModeRules.default(9))
    }

    @Test fun overlaidAllowsAtMostEight() {
        assertTrue(ChartModeRules.canAdd(ChartMode.OVERLAID, 7))
        assertTrue(!ChartModeRules.canAdd(ChartMode.OVERLAID, 8))
        assertTrue(ChartModeRules.canAdd(ChartMode.SEPARATE, 20))
        assertTrue(!ChartModeRules.canSwitchTo(ChartMode.OVERLAID, 9))
        assertTrue(ChartModeRules.canSwitchTo(ChartMode.OVERLAID, 8))
    }
}

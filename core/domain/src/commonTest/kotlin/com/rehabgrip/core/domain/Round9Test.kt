package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.grip.ColorMath
import com.rehabgrip.core.domain.grip.GripPalette
import com.rehabgrip.core.domain.grip.GripStyles
import com.rehabgrip.core.domain.progress.DayAxis
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Round9Test {
    private val library = GripSpec.predefined().mapIndexed { i, g -> g.copy(id = i + 1L) }

    private val P = GripPalette.bases

    @Test fun predefinedGripsKeepTheirAgreedColourAndLetters() {
        val s = GripStyles.resolve(library)
        val byName = library.associate { it.name to s.getValue(it.id) }
        listOf(
            Triple("Semiarqueo 4 dedos", "SA", 0), Triple("Extensión 3 dedos", "E", 1), Triple("Semiestirado 4 dedos", "SE", 2),
            Triple("Bidedo estirado", "BE", 3), Triple("Bidedo arqueado", "BA", 4), Triple("Bidedo pistola", "BP", 6),
            Triple("Bidedo pistola arqueado", "PA", 7),
        ).forEach { (n, code, i) -> assertEquals(code to P[i], byName.getValue(n).let { it.code to it.base }, n) }
        assertEquals("DM" to P[5], s.getValue(GripStyles.DYNAMOMETER_ID).let { it.code to it.base })
    }

    @Test fun paletteHasSixteenColoursFarApart() {
        assertEquals(16, GripPalette.size)
        assertEquals(0xFF2A78D6, P[0])
        assertEquals(0xFFE34948, P[7])
        for (i in P.indices) for (j in i + 1 until P.size) assertTrue(ColorMath.deltaE(P[i], P[j]) >= ColorMath.MIN_DELTA_E, "$i-$j")
    }

    @Test fun colourNeverDependsOnTheSelectionOrOrder() {
        val own = GripSpec(id = 20, name = "Regleta mala", edgeMm = 15)
        val a = GripStyles.resolve(library + own)
        assertEquals(a, GripStyles.resolve((library + own).reversed()))
        assertEquals(a, GripStyles.resolve(library + own))
    }

    @Test fun ownGripNeverTakesAReservedColour() {
        val own = GripSpec(id = 20, name = "Mala", edgeMm = 15)
        val c = GripStyles.resolve(library + own).getValue(20).base
        assertTrue(c !in GripStyles.reserved)
        assertEquals(P[8], c) // first palette colour that is not reserved
        assertEquals(P[8], GripStyles.nextFreeColor(library))
    }

    @Test fun fortyGripsAllHaveDifferentFarApartColours() {
        val own = (1..40).map { GripSpec(id = 100L + it, name = "Grip numero $it", edgeMm = 10 + it % 40) }
        val colours = GripStyles.resolve(library + own).filterKeys { it != GripStyles.DYNAMOMETER_ID }.values.map { it.base } + GripStyles.dynamometer().base
        assertEquals(colours.size, colours.distinct().size)
        for (i in colours.indices) for (j in i + 1 until colours.size)
            assertTrue(ColorMath.deltaE(colours[i], colours[j]) >= ColorMath.MIN_DELTA_E, "$i-$j")
    }

    @Test fun duplicatesAreRepairedAndTheFirstKeepsItsColour() {
        val green = P[5] // the dynamometer one
        val a = GripSpec(id = 30, name = "Uno", color = P[9])
        val b = GripSpec(id = 31, name = "Dos", color = P[9])
        val c = GripSpec(id = 32, name = "Tres", color = green)
        val d = GripSpec(id = 33, name = "Cuatro")
        val lib = library + a + b + c + d
        val repairs = GripStyles.repairs(lib)
        assertTrue(30L !in repairs)
        assertEquals(setOf(31L, 32L, 33L), repairs.keys)
        val fixed = lib.map { g -> repairs[g.id]?.let { g.copy(color = it) } ?: g }
        assertEquals(emptyMap(), GripStyles.repairs(fixed))
        val cols = GripStyles.resolve(fixed).filterKeys { it != GripStyles.DYNAMOMETER_ID }.values.map { it.base }
        assertEquals(cols.size, cols.distinct().size)
        assertTrue(green !in repairs.values)
    }

    @Test fun editorRejectsATooCloseColour() {
        val own = GripSpec(id = 40, name = "Mi agarre")
        val lib = library + own
        assertEquals("Semiarqueo 4 dedos", GripStyles.conflict(P[0], lib, 40))
        assertEquals("Dinamómetro de mano", GripStyles.conflict(P[5], lib, 40))
        assertEquals(null, GripStyles.conflict(P[9], lib, 40))
        assertEquals(null, GripStyles.conflict(P[9], lib, 0))
    }

    @Test fun generatorKeepsItsDistanceBeyondThePalette() {
        val taken = P.toMutableList()
        repeat(30) { val c = GripPalette.nextFree(taken); assertTrue(ColorMath.farFrom(c, taken)); taken += c }
        assertEquals(46, taken.distinct().size)
    }

    @Test fun initialsOfOwnGrips() {
        assertEquals("RM", GripStyles.codeOf(GripSpec(name = "Regleta mala")))
        assertEquals("ABG", GripStyles.codeOf(GripSpec(name = "alfa beta gamma delta")))
        assertEquals("20", GripStyles.codeOf(GripSpec(name = "", edgeMm = 20)))
        assertEquals("XY", GripStyles.codeOf(GripSpec(name = "Regleta mala", initials = "xy")))
        assertEquals("ABC", GripStyles.sanitize(" a-b c d"))
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
}

package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.progress.AxisTicks
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AxisTicksTest {
    private val d = LocalDate(2026, 11, 25)

    @Test fun oneDayGivesOneTick() = assertEquals(listOf(d), AxisTicks.days(d, 0))

    @Test fun neverRepeatsALabel() {
        listOf(1, 2, 3, 6, 7, 28, 84, 200).forEach { span ->
            val t = AxisTicks.days(d, span)
            assertEquals(t.distinct(), t, "span $span")
            assertEquals(minOf(4, span + 1), t.size, "span $span")
            assertEquals(d, t.first())
            assertEquals(LocalDate.fromEpochDays(d.toEpochDays() + span), t.last())
        }
    }

    @Test fun twoDaysGiveTwoTicksNotFourEqualOnes() = assertEquals(listOf(d, LocalDate(2026, 11, 26)), AxisTicks.days(d, 1))
}

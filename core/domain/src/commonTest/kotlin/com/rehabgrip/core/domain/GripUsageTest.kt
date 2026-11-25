package com.rehabgrip.core.domain

import com.rehabgrip.core.domain.grip.GripUsage
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class GripUsageTest {
    private val a = GripSpec(id = 1, name = "A")
    private val b = GripSpec(id = 2, name = "B")

    @Test
    fun listsOnlyTheSessionsThatUseTheGrip() {
        val withA = WorkoutTemplate(id = 1, name = "Con A", sets = listOf(WorkoutSet(2, 5, 5, Grip.Library(a)), WorkoutSet(2, 5, 5, Grip.Library(b))))
        val withB = WorkoutTemplate(id = 2, name = "Con B", sets = listOf(WorkoutSet(2, 5, 5, Grip.Library(b))))
        assertEquals(listOf("Con A"), GripUsage.usedBy(a, listOf(withA, withB)).map { it.name })
        assertEquals(listOf("Con A", "Con B"), GripUsage.usedBy(b, listOf(withA, withB)).map { it.name })
        assertEquals(emptyList(), GripUsage.usedBy(GripSpec(id = 9), listOf(withA, withB)))
    }

    @Test
    fun replacementsExcludeTheDeletedGrip() {
        assertEquals(listOf(2L), GripUsage.replacements(a, listOf(a, b)).map { it.id })
    }
}

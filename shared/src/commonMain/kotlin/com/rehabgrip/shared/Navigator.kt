package com.rehabgrip.shared

import androidx.compose.runtime.mutableStateListOf
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.session.Attempt
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.HandWorkoutResult
import com.rehabgrip.core.domain.workout.WorkoutTemplate

enum class Tab { HOME, PROGRESS, WORKOUTS, HISTORY, PROFILE }

sealed interface Route {
    data class Tabs(val tab: Tab) : Route
    data class Training(val exercise: Exercise) : Route
    data class Live(val exercise: Exercise, val hand: Hand) : Route
    data class SaveSession(val exercise: Exercise, val hand: Hand, val attempts: List<Attempt>, val peakKg: Double) : Route
    data class Saved(
        val exercise: Exercise,
        val hand: Hand,
        val kg: Double,
        val note: String,
        val epochMillis: Long,
        val discomforts: List<Discomfort> = emptyList(),
    ) : Route
    data object Reminders : Route
    data object GripLibrary : Route
    data class GripEvaluate(val grip: GripSpec) : Route
    data class WorkoutEditor(val template: WorkoutTemplate) : Route
    data class WorkoutRun(val template: WorkoutTemplate) : Route
    data class WorkoutSummary(val template: WorkoutTemplate, val results: List<HandWorkoutResult>) : Route
}

/** Minimal back stack; the platform wires its system back to [pop]. */
class Navigator {
    private val stack = mutableStateListOf<Route>(Route.Tabs(Tab.HOME))
    val current: Route get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop(): Boolean = if (canPop) {
        stack.removeAt(stack.lastIndex)
        true
    } else false

    fun selectTab(tab: Tab) {
        stack.clear()
        stack.add(Route.Tabs(tab))
    }

    /** Replaces the top (e.g. run -> summary) so back does not return to it. */
    fun replace(route: Route) {
        stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    /** Replaces everything after the tabs root (e.g. after saving). */
    fun resetTo(route: Route, tab: Tab = Tab.HOME) {
        stack.clear()
        stack.add(Route.Tabs(tab))
        if (route != stack.first()) stack.add(route)
    }
}

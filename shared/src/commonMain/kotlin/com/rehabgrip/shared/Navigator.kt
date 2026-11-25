package com.rehabgrip.shared

import androidx.compose.runtime.mutableStateListOf
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.session.Attempt

enum class Tab { HOME, PROGRESS, HISTORY, PROFILE }

sealed interface Route {
    data class Tabs(val tab: Tab) : Route
    data class Training(val exercise: Exercise) : Route
    data class Live(val exercise: Exercise, val hand: Hand) : Route
    data class SaveSession(val exercise: Exercise, val hand: Hand, val attempts: List<Attempt>, val peakKg: Double) : Route
    data class Saved(val exercise: Exercise, val hand: Hand, val kg: Double, val note: String, val epochMillis: Long) : Route
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

    /** Replaces everything after the tabs root (e.g. after saving). */
    fun resetTo(route: Route) {
        stack.clear()
        stack.add(Route.Tabs(Tab.HOME))
        if (route != stack.first()) stack.add(route)
    }
}

package com.rehabgrip.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rehabgrip.feature.reminders.RemindersScreen
import com.rehabgrip.feature.workouts.WorkoutEditorScreen
import com.rehabgrip.feature.workouts.WorkoutRunPresenter
import com.rehabgrip.feature.workouts.WorkoutRunScreen
import com.rehabgrip.feature.workouts.WorkoutSummaryScreen
import kotlinx.coroutines.launch

/** Reminders, session editor, guided run and its summary. */
@Composable
internal fun WorkoutAndReminderRoutes(route: Route, container: AppContainer, navigator: Navigator, session: AppSession, data: AppData) {
    val scope = rememberCoroutineScope()
    when (route) {
        Route.Reminders -> {
            val reminders by remember { container.reminders.observeAll() }.collectAsState(emptyList())
            RemindersScreen(
                reminders = reminders,
                onBack = { navigator.pop() },
                onSave = { scope.launch { container.saveReminder(it) } },
                onDelete = { scope.launch { container.deleteReminder(it) } },
                onTest = {
                    scope.launch {
                        if (container.platform.reminderScheduler.ensurePermission()) container.platform.reminderScheduler.showTest()
                    }
                },
            )
        }
        is Route.WorkoutEditor -> WorkoutEditorScreen(
            initial = route.template,
            library = data.grips,
            injury = session.injury,
            recordOf = { grip, hand -> data.record(hand, grip) },
            onBack = { navigator.pop() },
            onSave = { t -> scope.launch { container.workouts.upsert(t); navigator.pop() } },
            onDelete = if (route.template.id == 0L) null else ({ scope.launch { container.workouts.delete(route.template.id); navigator.pop() } }),
            onSaveGrip = { g -> scope.launch { container.grips.upsert(g) } },
            evaluateContent = { grip, close -> GripEvaluateRoute(grip, container, session, data, close) },
        )
        is Route.WorkoutRun -> WorkoutRunRoute(route, container, navigator, data)
        is Route.WorkoutSummary -> WorkoutSummaryScreen(
            name = route.template.name,
            results = route.results,
            recentZones = data.recentZones,
            defaultHand = session.injury.focusHand,
            onDiscard = { navigator.selectTab(Tab.WORKOUTS) },
            onSave = { d ->
                scope.launch {
                    container.saveWorkout(route.template, route.results, d)
                    navigator.selectTab(Tab.HISTORY)
                }
            },
        )
        else -> Unit
    }
}

@Composable
private fun WorkoutRunRoute(route: Route.WorkoutRun, container: AppContainer, navigator: Navigator, data: AppData) {
    val scope = rememberCoroutineScope()
    val presenter = remember(route) {
        WorkoutRunPresenter(
            template = route.template,
            source = container.forceSource(data.simulated),
            cues = container.platform.cuePlayer,
            recordKg = { hand, grip -> data.record(hand, grip)?.kg },
            scope = scope,
        )
    }
    DisposableEffect(presenter) {
        presenter.start()
        onDispose { presenter.dispose() }
    }
    val state by presenter.state.collectAsState()
    WorkoutRunScreen(
        template = route.template,
        state = state,
        onTogglePause = presenter::togglePause,
        onSkip = presenter::skipTurn,
        onFinish = { navigator.replace(Route.WorkoutSummary(route.template, presenter.results())) },
    )
}

package com.rehabgrip.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.feature.exercises.TrainingScreen
import com.rehabgrip.feature.measure.LiveMeasurementPresenter
import com.rehabgrip.feature.measure.LiveMeasurementScreen
import com.rehabgrip.feature.measure.SaveSessionScreen
import com.rehabgrip.feature.measure.SavedScreen
import kotlinx.coroutines.launch

/** Training -> live measurement -> choose attempt -> saved. */
@Composable
internal fun MeasureRoutes(route: Route, container: AppContainer, navigator: Navigator, session: AppSession, data: AppData) {
    val scope = rememberCoroutineScope()

    fun save(exercise: Exercise, hand: Hand, kg: Double, note: String, discomforts: List<Discomfort>, source: MeasurementSource) =
        scope.launch {
            val at = container.now()
            container.saveMeasurement(exercise, hand, kg, source, at, note, discomforts)
                .onSuccess { navigator.resetTo(Route.Saved(exercise, hand, kg, note, at, discomforts)) }
        }

    when (route) {
        is Route.Training -> TrainingScreen(
            exercise = route.exercise,
            history = data.measurements,
            onBack = { navigator.pop() },
            onConnectScale = { hand ->
                scope.launch {
                    if (data.simulated || container.platform.ensureBluetoothPermission()) {
                        navigator.push(Route.Live(route.exercise, hand))
                    }
                }
            },
            onManualSave = { hand, kg, note, discomforts -> save(route.exercise, hand, kg, note, discomforts, MeasurementSource.MANUAL) },
            injury = session.injury,
            recentZones = data.recentZones,
        )
        is Route.Live -> LiveRoute(route, container, navigator, data.simulated)
        is Route.SaveSession -> SaveSessionScreen(
            exercise = route.exercise,
            hand = route.hand,
            attempts = route.attempts,
            sessionPeakKg = route.peakKg,
            onBack = { navigator.pop() },
            onSave = { kg, note, discomforts -> save(route.exercise, route.hand, kg, note, discomforts, MeasurementSource.SCALE) },
            recentZones = data.recentZones,
        )
        is Route.Saved -> SavedScreen(
            exercise = route.exercise,
            hand = route.hand,
            kg = route.kg,
            note = route.note,
            discomforts = route.discomforts,
            dateText = formatDateTime(route.epochMillis),
            onAccept = { navigator.selectTab(Tab.HOME) },
        )
        else -> Unit
    }
}

@Composable
private fun LiveRoute(route: Route.Live, container: AppContainer, navigator: Navigator, simulated: Boolean) {
    val scope = rememberCoroutineScope()
    val presenter = remember(route) { LiveMeasurementPresenter(container.forceSource(simulated), scope) }
    DisposableEffect(presenter) {
        presenter.start()
        onDispose { presenter.dispose() }
    }
    val state by presenter.state.collectAsState()
    LiveMeasurementScreen(
        exercise = route.exercise,
        hand = route.hand,
        state = state,
        onBack = { navigator.pop() },
        onToggleRecording = presenter::toggleRecording,
        onFinish = {
            navigator.push(Route.SaveSession(route.exercise, route.hand, state.attempts, state.peak?.kg ?: 0.0))
        },
    )
}

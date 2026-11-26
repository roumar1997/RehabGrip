package com.rehabgrip.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rehabgrip.core.domain.grip.GripRecords
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.feature.grips.EvaluateGripScreen
import com.rehabgrip.feature.grips.GripEvaluationPresenter
import com.rehabgrip.feature.grips.GripLibraryScreen
import kotlinx.coroutines.launch

/** «Mis agarres» (library) and the evaluation flow of one grip. */
@Composable
internal fun GripRoutes(route: Route, container: AppContainer, navigator: Navigator, session: AppSession, data: AppData) {
    val scope = rememberCoroutineScope()
    when (route) {
        Route.GripLibrary -> GripLibraryScreen(
            rows = GripRecords.rows(data.gripEvaluations, data.measurements, data.grips),
            injury = session.injury,
            onBack = { navigator.pop() },
            onSave = { g -> scope.launch { container.grips.upsert(g) } },
            templates = data.workouts,
            onDelete = { g, replacement -> scope.launch { container.grips.delete(g.id, replacement) } },
            onEvaluate = { navigator.push(Route.GripEvaluate(it)) },
        )
        is Route.GripEvaluate -> GripEvaluateRoute(route.grip, container, session, data, onClose = { navigator.pop() })
        else -> Unit
    }
}

/** Runs the evaluation of [grip]; also hosted inside the workout editor so the draft is not lost. */
@Composable
internal fun GripEvaluateRoute(grip: GripSpec, container: AppContainer, session: AppSession, data: AppData, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val presenter = remember(grip.id) { GripEvaluationPresenter(container.forceSource(data.simulated), scope) }
    DisposableEffect(presenter) {
        val starter = scope.launch {
            if (data.simulated || container.platform.ensureBluetoothPermission()) presenter.start()
        }
        onDispose { starter.cancel(); presenter.dispose() }
    }
    val state by presenter.state.collectAsState()
    val previous = Hand.entries.associateWith { GripRecords.record(data.gripEvaluations, data.measurements, grip, it) }
    EvaluateGripScreen(
        grip = grip,
        injury = session.injury,
        state = state,
        trace = presenter.trace,
        previous = previous,
        onBack = onClose,
        onMeasure = presenter::beginMeasure,
        onStop = presenter::finishMeasure,
        onRestart = presenter::restartMeasure,
        onManual = presenter::setManual,
        onClear = presenter::clear,
        onSave = {
            scope.launch {
                val at = container.now()
                state.results.forEach { (hand, r) ->
                    container.saveGripEvaluation(
                        grip, hand, r.maxKg, r.minKg, r.avgKg,
                        if (r.fromScale) MeasurementSource.SCALE else MeasurementSource.MANUAL, at,
                    )
                }
                onClose()
            }
        },
    )
}

package com.rehabgrip.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rehabgrip.core.domain.grip.GripRecords
import com.rehabgrip.core.domain.grip.MaxRecords
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import com.rehabgrip.feature.exercises.ExercisesScreen
import com.rehabgrip.feature.history.HistoryScreen
import com.rehabgrip.feature.profile.ProfileScreen
import com.rehabgrip.feature.profile.ProfileUiState
import com.rehabgrip.feature.progress.ProgressScreen
import com.rehabgrip.feature.workouts.WorkoutsScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun TabContent(tab: Tab, container: AppContainer, navigator: Navigator, session: AppSession, data: AppData) {
    val scope = rememberCoroutineScope()
    when (tab) {
        Tab.HOME -> ExercisesScreen(
            session.profile.name, data.measurements, session.injury, container.now(),
            onExercise = { navigator.push(Route.Training(it)) },
            grips = data.grips,
            onGrip = { navigator.push(Route.GripEvaluate(it)) },
            onMeter = { scope.launch { if (data.simulated || container.platform.ensureBluetoothPermission()) navigator.push(Route.Meter) } },
        )
        Tab.PROGRESS -> ProgressScreen(data.measurements, container.now(), session.injury, data.grips)
        Tab.WORKOUTS -> WorkoutsScreen(
            templates = data.workouts,
            onNew = {
                navigator.push(Route.WorkoutEditor(WorkoutTemplate(name = "", sets = listOf(WorkoutSet(reps = 6, workSeconds = 10, restSeconds = 20)))))
            },
            onEdit = { navigator.push(Route.WorkoutEditor(it)) },
            onDelete = { t -> scope.launch { container.workouts.delete(t.id) } },
            missingBases = { t -> GripRecords.missingBases(t) { hand, grip -> data.record(hand, grip)?.kg } },
            onEvaluateGrips = { navigator.push(Route.GripLibrary) },
            onEvaluate = { navigator.push(Route.GripEvaluate(it)) },
            onMeter = { scope.launch { if (data.simulated || container.platform.ensureBluetoothPermission()) navigator.push(Route.Meter) } },
            onPlay = { t ->
                scope.launch {
                    if (data.simulated || container.platform.ensureBluetoothPermission()) navigator.push(Route.WorkoutRun(t))
                }
            },
        )
        Tab.HISTORY -> HistoryScreen(
            measurements = data.measurements,
            onExport = container::export,
            onDelete = { id -> scope.launch { container.measurements.delete(id) } },
            workouts = data.workoutResults,
            onDeleteWorkout = { at -> scope.launch { container.workoutResults.delete(at) } },
            onEdit = { m -> scope.launch { container.updateMeasurement(m, container.now()) } },
            onChangeWorkoutDate = { old, new -> scope.launch { container.workoutResults.changeDate(old, com.rehabgrip.core.domain.model.RecordDate.resolve(new, container.now())) } },
            grips = data.grips,
        )
        Tab.PROFILE -> ProfileTab(container, navigator, session, data)
    }
}

@Composable
private fun ProfileTab(container: AppContainer, navigator: Navigator, session: AppSession, data: AppData) {
    val scope = rememberCoroutineScope()
    val focus = session.injury.focusHand
    val summary = remember(data.measurements, session.injury) {
        ProgressCalculator().summarize(data.measurements, Exercise.HALF_CRIMP, ProgressRange.ALL, container.now())
    }
    ProfileScreen(
        state = ProfileUiState(
            name = session.profile.name,
            focusImprovementPercent = summary.of(focus).improvementPercent,
            symmetryPercent = summary.symmetryPercent(focus),
            simulatedScale = data.simulated,
            showDeveloperOptions = container.platform.isDebug,
            injury = session.injury,
            maxRows = MaxRecords.mine(data.allMeasurements, data.grips),
            scaleName = data.scaleName,
            scaleLocked = data.scaleId != null,
            simContaminated = data.contaminated,
            friends = MaxRecords.friends(data.allMeasurements, data.grips),
        ),
        onRename = { scope.launch { container.settings.save(com.rehabgrip.core.domain.model.UserProfile(it)) } },
        onSimulatedScale = { scope.launch { container.settings.setFlag(SIM_KEY, it) } },
        onLoadSampleData = { scope.launch { container.loadSampleData() } },
        onInjury = { scope.launch { container.settings.saveInjury(it) } },
        onReminders = { navigator.push(Route.Reminders) },
        onGrips = { navigator.push(Route.GripLibrary) },
        onEvaluate = { navigator.push(Route.GripEvaluate(it)) },
        onDiagnostics = { navigator.push(Route.Diagnostics) },
        onForgetScale = { scope.launch { container.forgetScale() } },
        onSimContaminated = { scope.launch { container.settings.setFlag(SIM_CONTAMINATED_KEY, it) } },
    )
}

internal fun formatDateTime(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    fun Int.p() = toString().padStart(2, '0')
    return "${dt.dayOfMonth.p()}/${dt.monthNumber.p()}/${dt.year} · ${dt.hour.p()}:${dt.minute.p()}"
}

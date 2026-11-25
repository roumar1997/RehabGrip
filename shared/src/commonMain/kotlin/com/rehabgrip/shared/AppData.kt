package com.rehabgrip.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.InjuryForm
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.progress.DiscomfortCalculator
import com.rehabgrip.core.domain.grip.GripEvaluation
import com.rehabgrip.core.domain.grip.GripRecord
import com.rehabgrip.core.domain.grip.GripRecords
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import com.rehabgrip.core.domain.workout.WorkoutTemplate

/** Live data from the repositories, collected once for all screens. */
internal data class AppData(
    /** Everything stored, friends included. Use [measurements] for the user's own. */
    val allMeasurements: List<Measurement>,
    val simulated: Boolean,
    val workouts: List<WorkoutTemplate>,
    val grips: List<GripSpec>,
    val gripEvaluations: List<GripEvaluation>,
    val workoutResults: List<WorkoutResultEntry>,
) {
    val measurements: List<Measurement> = allMeasurements.filter { it.isMine }

    /** Evaluated max of a hand for a grip (falls back to old records of the predefined grips). */
    fun record(hand: Hand, grip: Grip): GripRecord? = GripRecords.record(gripEvaluations, measurements, grip, grips, hand)

    val recentZones: List<String> get() = DiscomfortCalculator().recentZones(measurements)

    val recentPeople: List<String> get() = com.rehabgrip.core.domain.grip.MaxRecords.recentPeople(allMeasurements)
}

@Composable
internal fun rememberAppData(container: AppContainer): AppData {
    val measurements by remember { container.measurements.observeAll() }.collectAsState(emptyList())
    val simulated by remember { container.settings.observeFlag(SIM_KEY, container.platform.isDebug) }
        .collectAsState(container.platform.isDebug)
    val workouts by remember { container.workouts.observeAll() }.collectAsState(emptyList())
    val grips by remember { container.grips.observeAll() }.collectAsState(emptyList())
    val evaluations by remember { container.gripEvaluations.observeAll() }.collectAsState(emptyList())
    val results by remember { container.workoutResults.observeAll() }.collectAsState(emptyList())
    return AppData(measurements, simulated, workouts, grips, evaluations, results)
}

/** Users who onboarded before injuries existed answer it once. */
@Composable
internal fun InjuryQuestionScreen(onSave: (Injury) -> Unit) {
    var injury by remember { mutableStateOf(Injury(Hand.RIGHT)) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Una pregunta rápida", style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy)
        InjuryForm(injury, { injury = it })
        RgPrimaryButton("Guardar", { onSave(injury) }, icon = Icons.Rounded.Check)
    }
}

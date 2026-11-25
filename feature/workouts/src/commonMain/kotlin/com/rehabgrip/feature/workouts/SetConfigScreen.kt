package com.rehabgrip.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.designsystem.components.GripChip
import com.rehabgrip.core.designsystem.components.GripEditorDialog
import com.rehabgrip.core.designsystem.components.GripRecordsLines
import com.rehabgrip.core.domain.grip.GripRecord
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.SetHand
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.StartMode
import com.rehabgrip.core.domain.workout.ToFailure
import com.rehabgrip.core.domain.workout.WorkoutSet

/** Per-set configuration opened from the GRIP icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetConfigScreen(
    index: Int,
    set: WorkoutSet,
    library: List<GripSpec>,
    injury: Injury,
    recordOf: (Grip, Hand) -> GripRecord?,
    onBack: () -> Unit,
    onChange: (WorkoutSet) -> Unit,
    onDelete: () -> Unit,
    onSaveGrip: (GripSpec) -> Unit,
    onEvaluate: (GripSpec) -> Unit,
) {
    var creatingGrip by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Serie ${index + 1}", onBack = onBack) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Borrar serie", tint = RgColors.TextSecondary) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Agarre") {
                val current = (set.grip as? Grip.Library)?.grip
                    ?: library.firstOrNull { it.builtIn == (set.grip as? Grip.Standard)?.exercise }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    library.forEach { g -> GripChip(g.label, current?.id == g.id) { onChange(set.copy(grip = Grip.Library(g))) } }
                    GripChip("+ Crear agarre", false) { creatingGrip = true }
                }
                if (current != null) {
                    Help(current.summary)
                    val grip = Grip.Library(current)
                    GripRecordsLines(recordOf(grip, Hand.RIGHT), recordOf(grip, Hand.LEFT), injury)
                    val needsBase = set.target is SetTarget.PercentOfRecord && Hand.entries.any { recordOf(grip, it) == null }
                    if (needsBase) {
                        Text(
                            "El objetivo usa el registro de este agarre y falta evaluarlo: sin él no hay zona.",
                            style = MaterialTheme.typography.bodySmall, color = RgColors.Warning,
                        )
                    }
                    TextButton(onClick = { onEvaluate(current) }) { Text(if (needsBase) "Evaluar agarre ahora" else "Evaluar de nuevo") }
                }
            }
            Section("Mano de esta serie") {
                RgSegmented(SetHand.entries, set.hand, { WorkoutLabels.setHand(it) }, { onChange(set.copy(hand = it)) })
            }
            TargetSection(set, onChange)
            Section("Hasta el fallo") {
                RgSegmented(ToFailure.entries, set.toFailure, { WorkoutLabels.failure(it) }, { onChange(set.copy(toFailure = it)) })
                Help(WorkoutLabels.failureHelp(set.toFailure))
            }
            Section("Inicio de la repetición") {
                RgSegmented(StartMode.entries, set.startMode, { WorkoutLabels.start(it) }, { onChange(set.copy(startMode = it)) })
                Help(WorkoutLabels.startHelp(set.startMode))
            }
            Section("Umbral inicio/parada") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onChange(set.copy(thresholdKg = (set.thresholdKg - 0.5).coerceAtLeast(0.5))) }) { Icon(Icons.Rounded.Remove, "Menos") }
                    Text(
                        "${WorkoutLabels.kg(set.thresholdKg)} kg",
                        style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    FilledTonalIconButton(onClick = { onChange(set.copy(thresholdKg = (set.thresholdKg + 0.5).coerceAtMost(50.0))) }) { Icon(Icons.Rounded.Add, "Más") }
                }
                Help("Por debajo de este peso se considera que no estás tirando (parada y fallo).")
            }
        }
        RgPrimaryButton("Hecho", onBack, Modifier.padding(16.dp).navigationBarsPadding())
    }
    if (creatingGrip) {
        GripEditorDialog(GripSpec(), onDismiss = { creatingGrip = false }, onSave = {
            onSaveGrip(it)
            creatingGrip = false
        })
    }
}

@Composable
private fun TargetSection(set: WorkoutSet, onChange: (WorkoutSet) -> Unit) {
    val t = set.target
    Section("Objetivo") {
        RgSegmented(listOf(false, true), t != SetTarget.Peak, { if (it) "Zona" else "Máximo (Peak)" }, { zone ->
            onChange(set.copy(target = if (zone) SetTarget.Kg(15.0, 25.0) else SetTarget.Peak))
        })
        if (t == SetTarget.Peak) {
            Help("Tira lo más fuerte que puedas; se guarda tu máximo.")
            return@Section
        }
        val byRecord = t is SetTarget.PercentOfRecord
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Umbral basado en registro", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                Text("Zona en % de tu máximo evaluado de esa mano y agarre", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
            }
            Switch(byRecord, { on ->
                onChange(set.copy(target = if (on) SetTarget.PercentOfRecord(70, 85) else SetTarget.Kg(15.0, 25.0)))
            }, colors = SwitchDefaults.colors(checkedTrackColor = RgColors.Primary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (t) {
                is SetTarget.Kg -> {
                    DecimalField("Mín (kg)", t.minKg, Modifier.weight(1f)) { v -> onChange(set.copy(target = (set.target as SetTarget.Kg).copy(minKg = v))) }
                    DecimalField("Máx (kg)", t.maxKg, Modifier.weight(1f)) { v -> onChange(set.copy(target = (set.target as SetTarget.Kg).copy(maxKg = v))) }
                }
                is SetTarget.PercentOfRecord -> {
                    IntField("Mín (%)", t.minPercent, Modifier.weight(1f)) { v ->
                        onChange(set.copy(target = (set.target as SetTarget.PercentOfRecord).copy(minPercent = v)))
                    }
                    IntField("Máx (%)", t.maxPercent, Modifier.weight(1f)) { v ->
                        onChange(set.copy(target = (set.target as SetTarget.PercentOfRecord).copy(maxPercent = v)))
                    }
                }
                SetTarget.Peak -> Unit
            }
        }
        Help("Pitido y vibración si pasas del máximo; aviso suave si te quedas por debajo del mínimo.")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    RgCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy, modifier = Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Help(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = if (selected) Color.White else RgColors.Navy,
    modifier = Modifier.clip(CircleShape).background(if (selected) RgColors.Primary else RgColors.SurfaceTint)
        .clickableNoRipple(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
)

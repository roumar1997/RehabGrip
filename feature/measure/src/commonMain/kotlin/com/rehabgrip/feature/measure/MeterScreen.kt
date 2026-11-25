package com.rehabgrip.feature.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.LiveForceChart
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
import com.rehabgrip.core.designsystem.components.GripChip
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.GripSpec

/** What «Guardar» stores: [grip] null = hand dynamometer. */
data class MeterSave(val grip: GripSpec?, val hand: Hand, val person: String, val kg: Double)

/** «Medir»: free measuring without a workout; live graph, held maximum, attempts, friends. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeterScreen(
    state: LiveState,
    trace: LiveForceTrace,
    grips: List<GripSpec>,
    recentPeople: List<String>,
    onBack: () -> Unit,
    onResetMax: () -> Unit,
    onSave: (MeterSave) -> Unit,
) {
    var who by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val max = state.peak?.kg ?: 0.0
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Medir", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                state.signal.label(), style = MaterialTheme.typography.titleSmall,
                color = if (state.signal.connected) RgColors.Success else RgColors.Warning,
            )
            OutlinedTextField(
                who, { who = it.take(30) }, label = { Text("¿Quién?") }, singleLine = true,
                placeholder = { Text("Yo") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            if (recentPeople.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GripChip("Yo", who.isBlank()) { who = "" }
                    recentPeople.forEach { p -> GripChip(p, who == p) { who = p } }
                }
            }
            RgCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("Ahora", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
                        Text("${formatKg(state.currentKg)} kg", style = MaterialTheme.typography.headlineMedium, color = RgColors.Navy, maxLines = 1)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("MÁXIMO", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
                        Text("${formatKg(max)} kg", style = MaterialTheme.typography.displayMedium, color = RgColors.Primary, maxLines = 1)
                    }
                }
            }
            RgCard(Modifier.fillMaxWidth().height(230.dp)) { LiveForceChart(trace, Modifier.fillMaxWidth().weight(1f)) }
            RgCard(Modifier.fillMaxWidth()) {
                Text("Intentos de esta medición", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                if (state.attempts.isEmpty()) {
                    Text("Tira cuando quieras: cada tirón es un intento.", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                }
                val best = state.attempts.maxByOrNull { it.peakKg }?.index
                state.attempts.asReversed().forEach { a ->
                    Text(
                        "#${a.index} · ${formatKg(a.peakKg)} kg" + if (a.index == best) "  ★" else "",
                        style = MaterialTheme.typography.bodyLarge, color = if (a.index == best) RgColors.Primary else RgColors.Navy,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RgSecondaryButton("Reiniciar máximo", onResetMax, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
            RgPrimaryButton("Guardar", { saving = true }, Modifier.weight(1f), icon = Icons.Rounded.Save, enabled = max > 0.0)
        }
    }
    if (saving) SaveDialog(who, max, grips, { saving = false }) { onSave(it); saving = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveDialog(who: String, kg: Double, grips: List<GripSpec>, onDismiss: () -> Unit, onSave: (MeterSave) -> Unit) {
    var grip by remember { mutableStateOf<GripSpec?>(grips.firstOrNull()) }
    var hand by remember { mutableStateOf(Hand.RIGHT) }
    var name by remember { mutableStateOf(who) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Guardar ${formatKg(kg)} kg") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Agarre", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grips.forEach { g -> GripChip(g.label, grip?.id == g.id) { grip = g } }
                    GripChip("Dinamómetro de mano", grip == null) { grip = null }
                }
                Text("Mano", style = MaterialTheme.typography.titleSmall)
                RgSegmented(Hand.entries, hand, { Labels.hand(it) }, { hand = it })
                OutlinedTextField(
                    name, { name = it.take(30) }, label = { Text("Nombre (vacío = yo)") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(MeterSave(grip, hand, name.trim(), kg)) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

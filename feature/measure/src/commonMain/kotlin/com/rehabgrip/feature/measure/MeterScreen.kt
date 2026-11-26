package com.rehabgrip.feature.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.LiveForceChart
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
import com.rehabgrip.core.designsystem.components.GripChip
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgStackedButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.GripSpec

/** What «Guardar» stores: [grip] null = hand dynamometer. */
data class MeterSave(val grip: GripSpec?, val hand: Hand, val person: String, val kg: Double)

/**
 * «Medir»: free measuring without a workout. Most of the screen is the live graph; the maximum
 * is held, every pull is listed with its own «Guardar…», and «A cero» tares the scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeterScreen(
    state: LiveState,
    trace: LiveForceTrace,
    grips: List<GripSpec>,
    recentPeople: List<String>,
    onBack: () -> Unit,
    onResetMax: () -> Unit,
    onTare: () -> Unit,
    onSave: (MeterSave) -> Unit,
) {
    var who by remember { mutableStateOf("") }
    var savingKg by remember { mutableStateOf<Double?>(null) }
    val max = state.peak?.kg ?: 0.0
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Medir", onBack = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                state.signal.label(), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                color = if (state.signal.connected) RgColors.Success else RgColors.Warning,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    who, { who = it.take(30) }, label = { Text("¿Quién?") }, singleLine = true,
                    placeholder = { Text("Yo") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                )
            }
            if (recentPeople.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GripChip("Yo", who.isBlank()) { who = "" }
                    recentPeople.forEach { p -> GripChip(p, who == p) { who = p } }
                }
            }
            RgCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ahora", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
                        Text("${formatKg(state.currentKg)} kg", style = MaterialTheme.typography.headlineMedium, color = RgColors.Navy, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MÁXIMO", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
                        Text("${formatKg(max)} kg", style = MaterialTheme.typography.displayMedium, color = RgColors.Primary, maxLines = 1)
                    }
                }
            }
            RgCard(Modifier.fillMaxWidth().weight(1f)) { LiveForceChart(trace, Modifier.fillMaxWidth().weight(1f)) }
            Attempts(state, onSave = { savingKg = it })
        }
        Row(Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RgStackedButton("Reiniciar", Icons.Rounded.Refresh, onResetMax, Modifier.weight(1f), color = RgColors.Navy)
            RgStackedButton("A cero", Icons.Rounded.Remove, onTare, Modifier.weight(1f), color = RgColors.Navy)
            RgStackedButton("Guardar máx.", Icons.Rounded.Save, { savingKg = max }, Modifier.weight(1.3f), enabled = max > 0.0)
        }
    }
    savingKg?.let { kg -> SaveDialog(who, kg, grips, { savingKg = null }) { onSave(it); savingKg = null } }
}

@Composable
private fun Attempts(state: LiveState, onSave: (Double) -> Unit) {
    RgCard(Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Intentos", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
            if (state.attempts.isEmpty()) {
                Text("Tira cuando quieras: cada tirón es un intento.", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
            }
            val best = state.attempts.maxByOrNull { it.peakKg }?.index
            state.attempts.asReversed().forEach { a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${a.index} · ${formatKg(a.peakKg)} kg" + if (a.index == best) "  ★" else "",
                        style = MaterialTheme.typography.bodyLarge, color = if (a.index == best) RgColors.Primary else RgColors.Navy,
                        modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    TextButton(onClick = { onSave(a.peakKg) }) { Text("Guardar…", maxLines = 1) }
                }
            }
        }
    }
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

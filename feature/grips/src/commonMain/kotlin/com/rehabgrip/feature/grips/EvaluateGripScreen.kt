package com.rehabgrip.feature.grips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.designsystem.chart.LiveForceChart
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
import com.rehabgrip.core.designsystem.components.InjuryLabels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgInfoBanner
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatShortDate
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.grip.GripRecord
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.workout.GripSpec

/** «Evaluar agarre»: max (and min/average of the hold) of each hand, with the scale or by hand. */
@Composable
fun EvaluateGripScreen(
    grip: GripSpec,
    injury: Injury,
    state: EvaluationState,
    trace: LiveForceTrace,
    previous: Map<Hand, GripRecord?>,
    onBack: () -> Unit,
    onMeasure: (Hand) -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onManual: (Hand, Double, Double?, Double?) -> Unit,
    onClear: (Hand) -> Unit,
    onSave: (Long?) -> Unit,
) {
    var at by remember { mutableStateOf<Long?>(null) }
    var manualHand by remember { mutableStateOf<Hand?>(null) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Evaluar agarre", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RgCard(Modifier.fillMaxWidth()) {
                Text(grip.label, style = MaterialTheme.typography.titleLarge, color = RgColors.Navy)
                Text(grip.summary, style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
            }
            val measuring = state.measuring
            if (measuring != null) {
                LivePanel(measuring, injury, state, trace, onStop, onRestart)
            } else {
                RgInfoBanner(
                    "Recomendado antes de entrenar: tira lo más fuerte que puedas y mantén unos segundos. " +
                        "Se guarda el máximo y, si mides con la báscula, también el mínimo y la media del tirón.",
                    Icons.Rounded.Info,
                )
                listOf(injury.focusHand, injury.focusHand.other).forEach { hand ->
                    HandCard(hand, injury, state, previous[hand], onMeasure, { manualHand = hand }, onClear)
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = RgColors.Danger) }
                com.rehabgrip.core.designsystem.components.RgDateField(at, { at = it })
            }
        }
        if (state.measuring == null) {
            RgPrimaryButton(
                "Guardar evaluación", { onSave(at) }, Modifier.padding(16.dp).navigationBarsPadding(),
                icon = Icons.Rounded.Check, enabled = state.results.isNotEmpty(),
            )
        }
    }
    manualHand?.let { hand ->
        ManualEvaluationDialog(hand, { manualHand = null }) { max, min, avg ->
            onManual(hand, max, min, avg)
            manualHand = null
        }
    }
}

@Composable
private fun HandCard(
    hand: Hand,
    injury: Injury,
    state: EvaluationState,
    previous: GripRecord?,
    onMeasure: (Hand) -> Unit,
    onManual: () -> Unit,
    onClear: (Hand) -> Unit,
) {
    val result = state.results[hand]
    RgCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                InjuryLabels.handRole(hand, injury),
                style = MaterialTheme.typography.titleMedium,
                color = if (hand == injury.hand) RgColors.Danger else RgColors.Navy,
                modifier = Modifier.weight(1f),
            )
            if (result != null) {
                IconButton(onClick = { onClear(hand) }) { Icon(Icons.Rounded.Close, "Quitar valor", tint = RgColors.TextSecondary) }
            }
        }
        Text(
            previous?.let { "Anterior: ${formatKg(it.kg)} kg · ${formatShortDate(it.epochMillis)}" } ?: "Sin evaluar todavía",
            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
        )
        if (result != null) {
            Text("Máximo ${formatKg(result.maxKg)} kg", style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy, modifier = Modifier.padding(top = 6.dp))
            val extra = listOfNotNull(
                result.minKg?.let { "Mínimo ${formatKg(it)} kg" },
                result.avgKg?.let { "Media ${formatKg(it)} kg" },
            )
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RgPrimaryButton(
                if (result == null) "Medir con báscula" else "Repetir", { onMeasure(hand) }, Modifier.weight(1.4f),
                icon = Icons.Rounded.Bluetooth,
            )
            RgSecondaryButton("A mano", onManual, Modifier.weight(1f), icon = Icons.Rounded.Edit)
        }
    }
}

@Composable
private fun LivePanel(hand: Hand, injury: Injury, state: EvaluationState, trace: LiveForceTrace, onStop: () -> Unit, onRestart: () -> Unit) {
    RgCard(Modifier.fillMaxWidth()) {
        Text(
            "Mano ${InjuryLabels.handRole(hand, injury).lowercase()}: ¡tira lo más fuerte que puedas!",
            style = MaterialTheme.typography.titleMedium, color = RgColors.Navy,
        )
        Text("${formatKg(state.currentKg)} kg", style = MaterialTheme.typography.displayMedium, color = RgColors.Navy)
        Text("Máximo: ${formatKg(state.peakKg)} kg", style = MaterialTheme.typography.titleSmall, color = RgColors.Primary)
        LiveForceChart(trace, Modifier.fillMaxWidth().height(200.dp))
        Text(
            state.signal.label(), style = MaterialTheme.typography.bodySmall,
            color = if (state.signal.connected) RgColors.Success else RgColors.Warning, maxLines = 1,
        )
        RgSecondaryButton("Repetir / empezar de cero", onRestart, Modifier.padding(top = 8.dp), icon = Icons.Rounded.Refresh)
        RgPrimaryButton("Parar y guardar valor", onStop, Modifier.padding(top = 10.dp), icon = Icons.Rounded.Stop, color = RgColors.Navy)
    }
}

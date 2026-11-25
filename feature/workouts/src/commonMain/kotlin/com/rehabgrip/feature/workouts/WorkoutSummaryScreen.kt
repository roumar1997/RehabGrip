package com.rehabgrip.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.DiscomfortEditor
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.HandWorkoutResult

/** End of a guided session: per hand max, average and % in zone; save to history. */
@Composable
fun WorkoutSummaryScreen(
    name: String,
    results: List<HandWorkoutResult>,
    recentZones: List<String>,
    defaultHand: Hand,
    onDiscard: () -> Unit,
    onSave: (List<Discomfort>) -> Unit,
) {
    var discomforts by remember { mutableStateOf(emptyList<Discomfort>()) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Resumen · $name", onBack = onDiscard)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (results.isEmpty()) {
                Text("No se registró ningún tirón.", style = MaterialTheme.typography.bodyLarge, color = RgColors.TextSecondary)
            }
            results.forEach { r ->
                RgCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(handColor(r.hand)))
                        Spacer(Modifier.width(8.dp))
                        Text("Mano ${Labels.hand(r.hand).lowercase()}", style = MaterialTheme.typography.titleLarge, color = RgColors.Navy)
                        Spacer(Modifier.weight(1f))
                        Text("${r.reps} reps", style = MaterialTheme.typography.titleSmall, color = RgColors.TextSecondary)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Máximo", "${formatKg(r.maxKg)} kg")
                        Metric("Media tirando", "${formatKg(r.avgPullKg)} kg")
                        Metric("En zona", r.inZonePercent?.let { formatPercent(it, false) } ?: "—")
                    }
                }
            }
            DiscomfortEditor(discomforts, { discomforts = it }, recentZones, defaultHand)
        }
        RgPrimaryButton("Guardar en el historial", { onSave(discomforts) }, Modifier.padding(16.dp).navigationBarsPadding(), enabled = results.isNotEmpty())
    }
}

@Composable
private fun Metric(label: String, value: String) = Column {
    Text(label, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
    Text(value, style = MaterialTheme.typography.titleLarge, color = RgColors.Navy)
}

package com.rehabgrip.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.workout.WorkoutTemplate

/**
 * Screen shown before a session: summary, scale state and a big EMPEZAR. It starts the
 * 5 s «Prepárate…» countdown and from there the session runs by itself.
 */
@Composable
internal fun RunIntro(template: WorkoutTemplate, state: RunState, onBegin: () -> Unit, onBack: () -> Unit) {
    val ready = state.canStart
    Column(Modifier.fillMaxSize()) {
        RgTopBar(template.name, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RgCard(Modifier.fillMaxWidth()) {
                Text(
                    "${template.sets.size} series · ${template.sets.sumOf { it.reps }} reps · ${WorkoutLabels.handMode(template.handMode)}",
                    style = MaterialTheme.typography.titleMedium, color = RgColors.Navy,
                )
                Text("Duración estimada: ${WorkoutLabels.estimate(template)}", style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
                template.sets.forEachIndexed { i, s ->
                    Text(
                        "${i + 1} · ${WorkoutLabels.grip(s.grip)} — ${s.reps} × ${s.workSeconds} s",
                        style = MaterialTheme.typography.bodyMedium, color = RgColors.Navy,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            RgCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        if (state.signal.connected) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothSearching, null,
                        tint = if (ready) RgColors.Success else RgColors.Warning,
                    )
                    Text(
                        if (state.simulated && !state.signal.connected) "${state.sourceName} (simulada)" else state.signal.label(),
                        style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f),
                    )
                }
                if (!ready) {
                    Text(
                        "Enciende la báscula y acércala: no se puede empezar hasta recibir lecturas.",
                        style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Button(
            onClick = onBegin,
            enabled = ready,
            modifier = Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth().heightIn(min = 76.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RgColors.Primary),
        ) {
            Text(
                if (ready) "EMPEZAR" else "Esperando báscula…",
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center,
            )
        }
    }
}

package com.rehabgrip.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.workout.WorkoutTemplate

/**
 * Laid over the execution screen until the user taps EMPEZAR (big, in the middle). The 5 s
 * «Prepárate…» countdown starts then and the session runs by itself. The real scale must be
 * sending readings first; the simulated one may start any time.
 */
@Composable
internal fun StartOverlay(template: WorkoutTemplate, state: RunState, onBegin: () -> Unit, modifier: Modifier = Modifier) {
    val ready = state.canStart
    Box(modifier.fillMaxSize().background(RgColors.Background.copy(alpha = 0.93f)).clickable(enabled = false) {}) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            Text(template.name, style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy, textAlign = TextAlign.Center, maxLines = 2)
            Text(
                "${template.sets.size} series · ${template.sets.sumOf { it.reps }} reps · ${WorkoutLabels.handMode(template.handMode)}\nDuración estimada: ${WorkoutLabels.estimate(template)}",
                style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary, textAlign = TextAlign.Center,
            )
            Button(
                onClick = onBegin,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RgColors.Primary),
            ) {
                Text(
                    if (ready) "EMPEZAR" else "Esperando báscula…",
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center,
                )
            }
            Text(
                if (state.simulated && !state.signal.connected) "${state.sourceName} (simulada)" else state.signal.label(),
                style = MaterialTheme.typography.titleSmall, color = if (ready) RgColors.Success else RgColors.Warning, textAlign = TextAlign.Center,
            )
            if (!ready) {
                Text(
                    "Enciende la báscula y acércala: no se puede empezar hasta recibir lecturas.",
                    style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

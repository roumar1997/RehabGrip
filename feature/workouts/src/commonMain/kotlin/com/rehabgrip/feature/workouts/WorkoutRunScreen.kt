package com.rehabgrip.feature.workouts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.designsystem.chart.LiveForceChart
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.EnginePhase
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import com.rehabgrip.core.domain.workout.ZoneStatus

/** Each hand has its own strong colour so the turn is obvious at a glance. */
internal fun handColor(hand: Hand): Color = if (hand == Hand.RIGHT) RgColors.Primary else Color(0xFF7B4FD6)

internal fun handName(hand: Hand) = if (hand == Hand.RIGHT) "DERECHA" else "IZQUIERDA"

@Composable
fun WorkoutRunScreen(
    template: WorkoutTemplate,
    state: RunState,
    trace: LiveForceTrace,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val snap = state.snapshot
    val slot = snap.slot
    val phase = snap.phase
    val bg by animateColorAsState(
        when {
            phase == EnginePhase.DONE -> RgColors.Success
            slot == null -> RgColors.Navy
            phase == EnginePhase.PULL || phase == EnginePhase.WAITING -> handColor(slot.hand)
            else -> handColor(slot.hand).copy(alpha = 0.55f)
        },
    )
    Column(Modifier.fillMaxSize()) {
        RgTopBar(
            if (slot == null) "Completado" else "Serie ${slot.setIndex + 1}/${template.sets.size} · Rep ${slot.rep}/${template.sets[slot.setIndex].reps}",
            onBack = onFinish,
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (snap.panels.size > 1 && phase != EnginePhase.DONE) {
                HandColumns(snap.panels)
                Text(
                    if (slot == null) "" else "Te toca: ${handName(slot.hand)} · ${phaseText(phase, state)}",
                    style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (slot == null) "¡Sesión terminada!" else "Te toca: ${handName(slot.hand)}",
                    color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(phaseText(phase, state), color = Color.White, style = MaterialTheme.typography.titleLarge)
                if (phase != EnginePhase.DONE && phase != EnginePhase.WAITING) {
                    Text(
                        "${(snap.displayMs + 999) / 1000}",
                        color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
                if (snap.otherHandReady && snap.otherHand != null && phase != EnginePhase.PULL) {
                    Text(
                        "${handName(snap.otherHand!!).lowercase().replaceFirstChar { it.uppercase() }} lista, después",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Progress(state)
            RgCard(Modifier.fillMaxWidth().weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatKg(state.currentKg)} kg", style = MaterialTheme.typography.headlineMedium, color = RgColors.Navy, modifier = Modifier.weight(1f))
                    ZoneChip(phase, state.zoneStatus, snap.zone != null)
                }
                LiveForceChart(trace, Modifier.fillMaxWidth().weight(1f), zone = snap.zone)
                if (state.status != ScaleStatus.RECEIVING) {
                    Text("${state.sourceName}: buscando señal…", style = MaterialTheme.typography.bodySmall, color = RgColors.Warning)
                }
            }
        }
        Row(Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (phase == EnginePhase.DONE) {
                RgPrimaryButton("Ver resumen", onFinish, color = RgColors.Success)
            } else {
                RgPrimaryButton(
                    if (state.paused) "Seguir" else "Pausa", onTogglePause, Modifier.weight(1f),
                    icon = if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                )
                RgPrimaryButton(
                    "Saltar", onSkip, Modifier.weight(1f), icon = Icons.Rounded.SkipNext,
                    color = RgColors.Navy, enabled = phase != EnginePhase.PULL && phase != EnginePhase.PREPARE,
                )
                RgPrimaryButton("Fin", onFinish, Modifier.weight(0.8f), icon = Icons.Rounded.Stop, color = RgColors.Danger)
            }
        }
    }
}

private fun phaseText(phase: EnginePhase, state: RunState): String = when (phase) {
    EnginePhase.PREPARE -> "Prepárate"
    EnginePhase.REST -> "Descansa"
    EnginePhase.WAITING -> "¡Tira! El contador empieza al superar el umbral"
    EnginePhase.PULL -> if (state.paused) "En pausa" else "¡Tira!"
    EnginePhase.DONE -> "Buen trabajo"
}

@Composable
private fun Progress(state: RunState) {
    val done = state.snapshot.results.size
    LinearProgressIndicator(
        progress = { if (state.totalSlots == 0) 1f else done.toFloat() / state.totalSlots },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = RgColors.Primary, trackColor = RgColors.SurfaceTint,
    )
}

@Composable
private fun ZoneChip(phase: EnginePhase, status: ZoneStatus, hasZone: Boolean) {
    if (!hasZone || phase != EnginePhase.PULL) return
    val (text, color) = when (status) {
        ZoneStatus.ABOVE -> "¡Pasas del máximo!" to RgColors.Danger
        ZoneStatus.BELOW -> "Por debajo" to RgColors.Warning
        ZoneStatus.IN -> "En zona" to RgColors.Success
    }
    Text(
        text, color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

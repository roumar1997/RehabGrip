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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.chart.LiveForceChart
import com.rehabgrip.core.designsystem.chart.LiveForceTrace
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgStackedButton
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
    onBegin: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    if (!state.started) {
        RunIntro(template, state, onBegin, onFinish)
        return
    }
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
    val set = slot?.let { template.sets[it.setIndex] }
    Column(Modifier.fillMaxSize()) {
        RgTopBar(
            if (slot == null) "Completado" else "Serie ${slot.setIndex + 1}/${template.sets.size} · Rep ${slot.rep}/${template.sets[slot.setIndex].reps}",
            onBack = onFinish,
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (set != null) {
                Text(
                    WorkoutLabels.grip(set.grip), style = MaterialTheme.typography.titleMedium, color = RgColors.Navy,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
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
                    color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(phaseText(phase, state), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (phase != EnginePhase.DONE && phase != EnginePhase.WAITING) {
                    Text(
                        "${(snap.displayMs + 999) / 1000}",
                        color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
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
            FailureNotice(state, set?.thresholdKg)
            Progress(state)
            RgCard(Modifier.fillMaxWidth().weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatKg(state.currentKg)} kg", style = MaterialTheme.typography.headlineMedium, color = RgColors.Navy, modifier = Modifier.weight(1f), maxLines = 1)
                    ZoneChip(phase, state.zoneStatus, snap.zone != null)
                }
                LiveForceChart(trace, Modifier.fillMaxWidth().weight(1f), zone = snap.zone)
                if (!state.signal.connected) {
                    Text(state.signal.label(), style = MaterialTheme.typography.bodySmall, color = RgColors.Warning)
                }
            }
        }
        Row(
            Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (phase == EnginePhase.DONE) {
                RgPrimaryButton("Ver resumen", onFinish, color = RgColors.Success)
            } else {
                RgStackedButton(
                    if (state.paused) "Reanudar" else "Pausa", if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    onTogglePause, Modifier.weight(1f),
                )
                RgStackedButton(
                    "Saltar", Icons.Rounded.SkipNext, onSkip, Modifier.weight(1f),
                    color = RgColors.Navy, enabled = phase != EnginePhase.PULL && phase != EnginePhase.PREPARE,
                )
                RgStackedButton("Fin", Icons.Rounded.Stop, onFinish, Modifier.weight(1f), color = RgColors.Danger)
            }
        }
    }
}

private fun phaseText(phase: EnginePhase, state: RunState): String = when {
    state.paused -> "En pausa"
    phase == EnginePhase.PREPARE -> "Prepárate…"
    phase == EnginePhase.REST -> "Descansa"
    phase == EnginePhase.WAITING -> "¡Tira! El tiempo empieza al superar el umbral"
    phase == EnginePhase.PULL -> "¡Tira!"
    else -> "Buen trabajo"
}

/** «Parada: fallo» after a rep that dropped under the threshold; a hint while it is dropping. */
@Composable
private fun FailureNotice(state: RunState, thresholdKg: Double?) {
    val snap = state.snapshot
    val failed = snap.phase == EnginePhase.REST && snap.results.lastOrNull()?.failed == true
    val dropping = snap.phase == EnginePhase.PULL && thresholdKg != null && state.currentKg < thresholdKg
    val text = when {
        failed -> "Parada: fallo" to RgColors.Danger
        dropping -> "Por debajo de ${formatKg(thresholdKg!!)} kg: el tiempo está parado" to RgColors.Warning
        else -> return
    }
    Text(
        text.first, color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(text.second).padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
        text, color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

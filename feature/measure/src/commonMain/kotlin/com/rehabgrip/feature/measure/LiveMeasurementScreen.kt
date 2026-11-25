package com.rehabgrip.feature.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.designsystem.chart.LiveForceChart
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand

@Composable
fun LiveMeasurementScreen(
    exercise: Exercise,
    hand: Hand,
    state: LiveState,
    onBack: () -> Unit,
    onToggleRecording: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Medición en directo", onBack = onBack) {
            Icon(Icons.Rounded.Bluetooth, null, tint = RgColors.Primary, modifier = Modifier.padding(end = 12.dp))
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state)
            RgCard(Modifier.fillMaxWidth()) {
                Text(
                    "${Labels.exercise(exercise)} · Mano ${Labels.hand(hand).lowercase()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = RgColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Text(formatKg(state.currentKg), style = MaterialTheme.typography.displayLarge, color = RgColors.Navy)
                    Text(" kg", style = MaterialTheme.typography.headlineMedium, color = RgColors.Navy, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(
                    "Máximo: ${state.peak?.let { formatKg(it.kg) } ?: "0,0"} kg   |   Intentos: ${state.attempts.size}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RgColors.Navy,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            RgCard(Modifier.fillMaxWidth().weight(1f)) {
                LiveForceChart(state.samples, state.peak, Modifier.fillMaxWidth().weight(1f))
                AttemptChips(state)
            }
        }
        Column(
            Modifier.padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RgPrimaryButton(
                    if (state.recording) "Detener" else "Reanudar",
                    onToggleRecording,
                    Modifier.weight(1f),
                    icon = if (state.recording) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    color = if (state.recording) RgColors.Danger else RgColors.Primary,
                )
                RgPrimaryButton(
                    "Guardar", onFinish, Modifier.weight(1f),
                    icon = Icons.Rounded.Save, enabled = state.peak != null && state.peak.kg > 0.0,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: LiveState) {
    val (text, color) = when (state.status) {
        ScaleStatus.RECEIVING -> "Conectado" to RgColors.Success
        ScaleStatus.SCANNING, ScaleStatus.IDLE -> "Buscando báscula…" to RgColors.Warning
        ScaleStatus.LOST -> "Señal perdida, reintentando…" to RgColors.Warning
        ScaleStatus.BLUETOOTH_OFF -> "Activa el Bluetooth" to RgColors.Danger
        ScaleStatus.UNAUTHORIZED -> "Falta permiso de Bluetooth" to RgColors.Danger
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RgColors.SurfaceTint).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.status == ScaleStatus.RECEIVING) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
            } else {
                Icon(Icons.Rounded.BluetoothSearching, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = if (color == RgColors.Success) Color(0xFF1E8A55) else color)
        }
        Text(state.sourceName, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
    }
}

@Composable
private fun AttemptChips(state: LiveState) {
    if (state.attempts.isEmpty()) {
        Text("Tira cuando quieras: cada tirón cuenta como un intento", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
        return
    }
    val best = state.attempts.maxByOrNull { it.peakKg }?.index
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.attempts.forEach { a ->
            val isBest = a.index == best
            Text(
                "#${a.index}  ${formatKg(a.peakKg)} kg",
                style = MaterialTheme.typography.labelMedium,
                color = if (isBest) Color.White else RgColors.Navy,
                modifier = Modifier.clip(CircleShape)
                    .background(if (isBest) RgColors.Primary else RgColors.SurfaceTint)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.DiscomfortZones
import com.rehabgrip.core.domain.model.Finger
import com.rehabgrip.core.domain.model.Hand
import kotlin.math.roundToInt

/** Short finger names that fit five in a row on the narrowest phones. */
private val fingerShort = listOf("Pulg", "Índ", "Cor", "Anul", "Meñ")

/** Colour for a 0-10 level: green -> amber -> red. */
fun discomfortColor(level: Int): Color = when {
    level <= 2 -> RgColors.Success
    level <= 5 -> RgColors.Warning
    else -> RgColors.Danger
}

/**
 * Several discomforts, each with a zone and a 0-10 level. [recentZones] lets the
 * user re-add the last used ones with one tap.
 */
@Composable
fun DiscomfortEditor(
    value: List<Discomfort>,
    onChange: (List<Discomfort>) -> Unit,
    recentZones: List<String>,
    defaultHand: Hand,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Molestias (opcional)", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
        value.forEachIndexed { i, d ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RgColors.SurfaceTint).padding(start = 12.dp, top = 4.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.zone, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f))
                    Text("${d.level}/10", style = MaterialTheme.typography.titleMedium, color = discomfortColor(d.level))
                    IconButton(onClick = { onChange(value.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Rounded.Close, "Quitar", tint = RgColors.TextSecondary)
                    }
                }
                Slider(
                    value = d.level.toFloat(),
                    onValueChange = { v -> onChange(value.mapIndexed { j, x -> if (j == i) x.copy(level = v.roundToInt()) else x }) },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = discomfortColor(d.level), activeTrackColor = discomfortColor(d.level)),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        val quick = recentZones.filter { z -> value.none { it.zone.equals(z, ignoreCase = true) } }
        if (quick.isNotEmpty()) {
            Text("Últimas usadas", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
            ZoneChips(quick) { onChange(value + Discomfort(it, 2)) }
        }
        RgSecondaryButton("Añadir molestia", { picking = true }, icon = Icons.Rounded.Add)
    }
    if (picking) {
        ZonePickerDialog(defaultHand, onDismiss = { picking = false }) { zone ->
            picking = false
            onChange(value + Discomfort(zone, 2))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneChips(zones: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        zones.forEach { z ->
            Text(
                z,
                style = MaterialTheme.typography.labelMedium,
                color = RgColors.Navy,
                modifier = Modifier.clip(CircleShape).background(RgColors.Surface).clickableNoRipple { onPick(z) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ZonePickerDialog(defaultHand: Hand, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var hand by remember { mutableStateOf(defaultHand) }
    var finger by remember { mutableStateOf(Finger.INDEX) }
    var free by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("¿Dónde notas la molestia?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RgSegmented(Hand.entries, hand, { Labels.hand(it) }, { hand = it })
                ZoneChips(DiscomfortZones.general(hand), onPick)
                Text("Dedo y poleas", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
                RgSegmented(Finger.entries, finger, { fingerShort[it.ordinal] }, { finger = it })
                ZoneChips(DiscomfortZones.finger(hand, finger), onPick)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        free, { free = it.take(40) }, singleLine = true, placeholder = { Text("Otra zona") },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                    )
                    Row(Modifier.width(4.dp)) {}
                    TextButton(onClick = { if (free.isNotBlank()) onPick(free.trim()) }, enabled = free.isNotBlank()) { Text("Añadir") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

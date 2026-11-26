package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.grip.ColorMath
import com.rehabgrip.core.domain.grip.GripPalette

/**
 * Colour chooser of a grip: the 16 curated colours (those already in use, or too close to one in use, are crossed out
 * with the name of the grip that has them) and a free hue/lightness chooser that keeps the minimum distance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GripColorPicker(selected: Long, onSelect: (Long) -> Unit, taken: List<Pair<Long, String>>, conflict: String?) {
    var free by remember { mutableStateOf(false) }
    var hue by remember { mutableStateOf(ColorMath.hueOf(selected).toFloat()) }
    var light by remember { mutableStateOf(0.5f) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GripPalette.bases.forEach { c ->
                val user = taken.firstOrNull { ColorMath.deltaE(c, it.first) < ColorMath.MIN_DELTA_E }?.second
                val chosen = c == selected
                Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(34.dp).alpha(if (user != null && !chosen) 0.35f else 1f).clip(CircleShape).background(Color(c))
                            .border(if (chosen) 3.dp else 0.dp, RgColors.Navy, CircleShape)
                            .let { if (user == null || chosen) it.clickableNoRipple { onSelect(c) } else it },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (user != null && !chosen) Canvas(Modifier.size(34.dp)) {
                            drawLine(RgColors.Navy, Offset(size.width * 0.15f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.15f), 3.dp.toPx())
                        }
                    }
                    if (user != null && !chosen) Text(user, fontSize = 9.sp, color = RgColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        TextButton(onClick = { free = !free }) { Text(if (free) "Ocultar selector libre" else "Elegir color") }
        if (free) {
            Text("Matiz", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
            Slider(hue, { hue = it; onSelect(ColorMath.hsl(hue.toDouble(), 0.65, light.toDouble())) }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth())
            Text("Luminosidad", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
            Slider(light, { light = it; onSelect(ColorMath.hsl(hue.toDouble(), 0.65, light.toDouble())) }, valueRange = 0.25f..0.75f, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().size(28.dp).clip(CircleShape).background(Color(selected)))
        }
        conflict?.let { Text("Demasiado parecido a «$it»: elige otro color", style = MaterialTheme.typography.bodySmall, color = RgColors.Danger, modifier = Modifier.padding(top = 2.dp)) }
    }
}

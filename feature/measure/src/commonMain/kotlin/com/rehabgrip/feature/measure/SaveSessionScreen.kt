package com.rehabgrip.feature.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import androidx.compose.runtime.remember
import com.rehabgrip.core.designsystem.components.DiscomfortEditor
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.session.Attempt

/** After a live session: pick which attempt to keep (default: the best one). */
@Composable
fun SaveSessionScreen(
    exercise: Exercise,
    hand: Hand,
    attempts: List<Attempt>,
    sessionPeakKg: Double,
    onBack: () -> Unit,
    onSave: (kg: Double, note: String, discomforts: List<Discomfort>) -> Unit,
    recentZones: List<String> = emptyList(),
) {
    var discomforts by remember { mutableStateOf(emptyList<Discomfort>()) }
    val options = attempts.ifEmpty { listOf(Attempt(1, 0, 0, sessionPeakKg)) }
    val best = options.maxBy { it.peakKg }
    var selected by rememberSaveable { mutableStateOf(best.index) }
    var note by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Guardar intento", onBack = onBack)
        Text(
            "${Labels.exercise(exercise)} · Mano ${Labels.hand(hand).lowercase()}",
            style = MaterialTheme.typography.titleMedium,
            color = RgColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(options, key = { it.index }) { a ->
                val isSel = a.index == selected
                RgCard(
                    Modifier.fillMaxWidth(),
                    color = if (isSel) RgColors.SurfaceTint else RgColors.Surface,
                    onClick = { selected = a.index },
                    padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(isSel, { selected = a.index }, colors = RadioButtonDefaults.colors(selectedColor = RgColors.Primary))
                        Text("Intento ${a.index}", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy, modifier = Modifier.weight(1f))
                        if (a.index == best.index) Icon(Icons.Rounded.EmojiEvents, "Máximo", tint = RgColors.Gold, modifier = Modifier.padding(end = 6.dp))
                        Text("${formatKg(a.peakKg)} kg", style = MaterialTheme.typography.titleLarge, color = RgColors.Navy, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("Notas (opcional)") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { DiscomfortEditor(discomforts, { discomforts = it }, recentZones, hand) }
        }
        RgPrimaryButton(
            "Guardar ${formatKg(options.first { it.index == selected }.peakKg)} kg",
            { onSave(options.first { it.index == selected }.peakKg, note, discomforts) },
            Modifier.padding(16.dp).navigationBarsPadding(),
            icon = Icons.Rounded.Save,
        )
    }
}

package com.rehabgrip.feature.measure

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand

/** "Resumen del entrenamiento": confirmation after storing a record. */
@Composable
fun SavedScreen(
    exercise: Exercise,
    hand: Hand,
    kg: Double,
    note: String,
    dateText: String,
    onAccept: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Resumen del entrenamiento", onBack = onAccept)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RgCard(Modifier.fillMaxWidth(), color = RgColors.SuccessBg) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(RgColors.Success), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("¡Registro guardado!", style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy)
                }
            }
            RgCard(Modifier.fillMaxWidth()) {
                Line(Icons.Rounded.CalendarMonth, "Fecha", dateText)
                HorizontalDivider(color = RgColors.Outline)
                Line(Icons.Rounded.FitnessCenter, "Ejercicio", Labels.exercise(exercise))
                HorizontalDivider(color = RgColors.Outline)
                Line(Icons.Rounded.PanTool, "Mano", Labels.hand(hand))
                HorizontalDivider(color = RgColors.Outline)
                Line(Icons.Rounded.BarChart, "Máximo", "${formatKg(kg)} kg")
                HorizontalDivider(color = RgColors.Outline)
                Line(Icons.Rounded.StickyNote2, "Notas", note.ifBlank { "—" })
            }
            Text(
                "Tu progreso se guardará y podrás verlo en las gráficas.",
                style = MaterialTheme.typography.bodyMedium,
                color = RgColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RgPrimaryButton("Aceptar", onAccept, Modifier.padding(16.dp).navigationBarsPadding())
    }
}

@Composable
private fun Line(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RgColors.Primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        }
    }
}

package com.rehabgrip.feature.exercises

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.illustrations.GripIllustrations
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.designsystem.components.SymmetryOverview
import com.rehabgrip.core.domain.model.Measurement

/** Home: "Mis ejercicios". Shows the latest value per hand under each exercise. */
@Composable
fun ExercisesScreen(
    userName: String,
    measurements: List<Measurement>,
    injury: Injury,
    nowMillis: Long,
    onExercise: (Exercise) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Mis ejercicios")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Hola, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RgColors.Navy,
                )
            }
            if (measurements.isNotEmpty()) {
                item { SymmetryOverview(measurements, injury, nowMillis, onExercise = onExercise) }
            }
            item {
                Text(
                    "Elige un ejercicio para registrar tu fuerza",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RgColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(Exercise.entries) { exercise ->
                ExerciseCard(exercise, measurements.filter { it.exercise == exercise }, injury) { onExercise(exercise) }
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, history: List<Measurement>, injury: Injury, onClick: () -> Unit) {
    RgCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                GripIllustrations.of(exercise), null,
                modifier = Modifier.size(width = 120.dp, height = 80.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Labels.exercise(exercise), style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
                Text(Labels.exerciseDetail(exercise), style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (exercise.usesScale) Icons.Rounded.Bluetooth else Icons.Rounded.EditNote,
                        null, tint = RgColors.Primary, modifier = Modifier.size(16.dp),
                    )
                    LastValue(Hand.RIGHT, history, injury)
                    LastValue(Hand.LEFT, history, injury)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = RgColors.TextSecondary)
        }
    }
}

@Composable
private fun LastValue(hand: Hand, history: List<Measurement>, injury: Injury) {
    val last = history.filter { it.hand == hand }.maxByOrNull { it.epochMillis } ?: return
    val color = if (hand == injury.hand) RgColors.Danger else if (hand == Hand.RIGHT) RgColors.RightHand else RgColors.LeftHand
    Text(
        "${Labels.handShort(hand)} ${formatKg(last.kg)}",
        style = MaterialTheme.typography.labelSmall,
        color = RgColors.Navy,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

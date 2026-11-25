package com.rehabgrip.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.workout.WorkoutTemplate

/** "Entrenos": saved sessions; play or edit them. */
@Composable
fun WorkoutsScreen(
    templates: List<WorkoutTemplate>,
    onNew: () -> Unit,
    onEdit: (WorkoutTemplate) -> Unit,
    onPlay: (WorkoutTemplate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Entrenamientos", leadingIcon = Icons.Rounded.Timer)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Sesiones guiadas: series con tiempo de tirar y descansar, zona objetivo y pitidos.",
                    style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary,
                )
            }
            items(templates, key = { it.id }) { t ->
                RgCard(Modifier.fillMaxWidth(), onClick = { onEdit(t) }, padding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
                            Text(
                                "${t.sets.size} series · ${t.sets.sumOf { it.reps }} reps · ${WorkoutLabels.handMode(t.handMode)}",
                                style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                            )
                            Text("Duración estimada: ${WorkoutLabels.estimate(t)}", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                        }
                        IconButton(onClick = { onEdit(t) }) { Icon(Icons.Rounded.Edit, "Editar", tint = RgColors.TextSecondary) }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(RgColors.Primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = { onPlay(t) }) { Icon(Icons.Rounded.PlayArrow, "Empezar", tint = Color.White) }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
        RgPrimaryButton("Nueva sesión", onNew, Modifier.padding(16.dp), icon = Icons.Rounded.Add)
    }
}

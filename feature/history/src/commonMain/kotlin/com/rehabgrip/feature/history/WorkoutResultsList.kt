package com.rehabgrip.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Guided sessions: max, average while pulling and % in zone per hand. */
@Composable
internal fun WorkoutResultsList(
    entries: List<WorkoutResultEntry>, onDelete: (Long) -> Unit, onChangeDate: (Long, Long) -> Unit, modifier: Modifier = Modifier,
) {
    var editingDate by remember { mutableStateOf<WorkoutResultEntry?>(null) }
    var deleting by remember { mutableStateOf<WorkoutResultEntry?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entries.isEmpty()) {
            item { Text("Aún no has hecho ningún entrenamiento guiado", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
        }
        items(entries, key = { it.epochMillis }) { e ->
            val dt = Instant.fromEpochMilliseconds(e.epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
            RgCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editingDate = e }) { Icon(Icons.Outlined.Edit, "Cambiar fecha", tint = RgColors.TextSecondary) }
                    IconButton(onClick = { deleting = e }) { Icon(Icons.Outlined.DeleteOutline, "Borrar entrenamiento", tint = RgColors.TextSecondary) }
                }
                Text(
                    "${dt.dayOfMonth}/${dt.monthNumber}/${dt.year} · ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                )
                e.hands.forEach { h ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Labels.hand(h.hand), style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                        Text(
                            "máx ${formatKg(h.maxKg)} kg · media ${formatKg(h.avgPullKg)} kg" +
                                (h.inZonePercent?.let { " · ${formatPercent(it, false)} en zona" } ?: "") + " · ${h.reps} reps",
                            style = MaterialTheme.typography.bodySmall, color = RgColors.Navy,
                        )
                    }
                }
            }
        }
    }
    editingDate?.let { e -> EditWorkoutDateDialog(e.epochMillis, e.name, { editingDate = null }) { onChangeDate(e.epochMillis, it); editingDate = null } }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar este entrenamiento?") },
            text = { Text("«${e.name}» desaparece del historial. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { onDelete(e.epochMillis); deleting = null }) { Text("Borrar", color = RgColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

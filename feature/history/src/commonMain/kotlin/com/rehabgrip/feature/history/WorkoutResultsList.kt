package com.rehabgrip.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
internal fun WorkoutResultsList(entries: List<WorkoutResultEntry>, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entries.isEmpty()) {
            item { Text("Aún no has hecho ningún entrenamiento guiado", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
        }
        items(entries, key = { it.epochMillis }) { e ->
            val dt = Instant.fromEpochMilliseconds(e.epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
            RgCard(Modifier.fillMaxWidth()) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
                Text(
                    "${dt.dayOfMonth}/${dt.monthNumber}/${dt.year} · ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                )
                e.hands.forEach { h ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Labels.hand(h.hand), style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                        Text(
                            "máx ${formatKg(h.maxKg)} · media ${formatKg(h.avgPullKg)} kg" +
                                (h.inZonePercent?.let { " · ${formatPercent(it, false)} en zona" } ?: "") + " · ${h.reps} reps",
                            style = MaterialTheme.typography.bodySmall, color = RgColors.Navy,
                        )
                    }
                }
            }
        }
    }
}

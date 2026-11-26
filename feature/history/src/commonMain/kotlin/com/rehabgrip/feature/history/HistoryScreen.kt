package com.rehabgrip.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.discomfortColor
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import com.rehabgrip.core.domain.workout.GripSpec
import kotlinx.datetime.toLocalDateTime

private const val DYNAMOMETER_FILTER = -1L

/** Library grips match by id (or, for predefined ones, by their old exercise); the dynamometer by exercise. */
private fun matchesFilter(m: Measurement, filter: Long?, grip: GripSpec?): Boolean = when {
    filter == null -> true
    filter == DYNAMOMETER_FILTER -> m.exercise == Exercise.DYNAMOMETER
    grip != null -> grip.matches(m)
    else -> true
}

private enum class HandFilter(val hand: Hand?, val label: String) {
    ALL(null, "Todas"), RIGHT(Hand.RIGHT, "Derecha"), LEFT(Hand.LEFT, "Izquierda")
}

/** "Historial": every record, newest first, filterable and exportable to CSV. */
@Composable
fun HistoryScreen(
    measurements: List<Measurement>,
    onExport: (List<Measurement>) -> Unit,
    onDelete: (Long) -> Unit,
    workouts: List<WorkoutResultEntry> = emptyList(),
    onDeleteWorkout: (Long) -> Unit = {},
    onEdit: (Measurement) -> Unit = {},
    onChangeWorkoutDate: (Long, Long) -> Unit = { _, _ -> },
    grips: List<GripSpec> = emptyList(),
) {
    fun nameOf(m: Measurement) = m.gripId?.let { id -> grips.firstOrNull { it.id == id } }?.label ?: Labels.exercise(m.exercise)
    var showWorkouts by rememberSaveable { mutableStateOf(false) }
    var handFilter by rememberSaveable { mutableStateOf(HandFilter.ALL) }
    // null = all; otherwise a library grip id, or DYNAMOMETER_FILTER.
    var gripFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val filterGrip = grips.firstOrNull { it.id == gripFilter }
    var showFilters by rememberSaveable { mutableStateOf(true) }
    var toDelete by remember { mutableStateOf<Measurement?>(null) }
    var editing by remember { mutableStateOf<Measurement?>(null) }
    val filtered = measurements.filter {
        (handFilter.hand == null || it.hand == handFilter.hand) && matchesFilter(it, gripFilter, filterGrip)
    }.sortedByDescending { it.epochMillis }

    Column(Modifier.fillMaxSize()) {
        RgTopBar("Historial", leadingIcon = Icons.Rounded.History) {
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Rounded.FilterList, "Filtrar", tint = RgColors.Navy)
            }
        }
        RgSegmented(
            listOf(false, true), showWorkouts, { if (it) "Entrenamientos" else "Registros" }, { showWorkouts = it },
            Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), colorFor = { RgColors.Navy },
        )
        if (showWorkouts) {
            WorkoutResultsList(workouts, onDeleteWorkout, onChangeWorkoutDate, Modifier.weight(1f))
            return@Column
        }
        if (showFilters) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RgSegmented(HandFilter.entries, handFilter, { it.label }, { handFilter = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Todos", gripFilter == null) { gripFilter = null }
                    grips.forEach { g -> Pill(g.label, gripFilter == g.id) { gripFilter = g.id } }
                    Pill(Labels.exercise(Exercise.DYNAMOMETER), gripFilter == DYNAMOMETER_FILTER) { gripFilter = DYNAMOMETER_FILTER }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filtered.isEmpty()) {
                item { Text("Todavía no hay registros con este filtro", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
            }
            items(filtered, key = { it.id }) { m -> HistoryRow(m, nameOf(m), { editing = m }) { toDelete = m } }
        }
        RgSecondaryButton(
            "Exportar datos (CSV)", { onExport(filtered) },
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp), icon = Icons.Rounded.IosShare,
        )
    }
    editing?.let { m -> EditRecordDialog(m, nameOf(m), { editing = null }) { onEdit(it); editing = null } }
    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar registro?") },
            text = { Text("${nameOf(m)} · ${Labels.hand(m.hand)} · ${formatKg(m.kg)} kg") },
            confirmButton = { TextButton(onClick = { onDelete(m.id); toDelete = null }) { Text("Borrar", color = RgColors.Danger) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = if (selected) Color.White else RgColors.Navy,
    modifier = Modifier.clip(CircleShape).background(if (selected) RgColors.Navy else RgColors.Surface)
        .clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 8.dp),
)

@Composable
private fun HistoryRow(m: Measurement, name: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dt = Instant.fromEpochMilliseconds(m.epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year}"
    val handColor = if (m.hand == Hand.RIGHT) RgColors.RightHand else RgColors.LeftHand
    RgCard(Modifier.fillMaxWidth(), onClick = onEdit, padding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(handColor))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$date · ${Labels.hand(m.hand)}", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (m.source == MeasurementSource.SCALE) Icons.Rounded.Bluetooth else Icons.Rounded.EditNote,
                        null, tint = RgColors.TextSecondary, modifier = Modifier.size(14.dp),
                    )
                }
                if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                m.discomforts.forEach { d ->
                    Text("• ${d.zone}: ${d.level}/10", style = MaterialTheme.typography.bodySmall, color = discomfortColor(d.level))
                }
            }
            Text("${formatKg(m.kg)} kg", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Borrar", tint = RgColors.TextSecondary) }
        }
    }
}

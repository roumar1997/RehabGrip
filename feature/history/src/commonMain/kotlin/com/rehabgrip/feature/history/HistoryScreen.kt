package com.rehabgrip.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import com.rehabgrip.core.designsystem.components.formatRecordDay
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.grip.GripKey
import com.rehabgrip.core.domain.grip.HistoryGroup
import com.rehabgrip.core.domain.grip.HistoryGroups
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DYNAMOMETER_FILTER = -1L

/** Same key as Home, Evolution and «Mis máximos»: library grip by id (or its predefined exercise), or the dynamometer. */
private fun matchesFilter(m: Measurement, filter: Long?, library: List<GripSpec>): Boolean = when (filter) {
    null -> true
    DYNAMOMETER_FILTER -> GripKey.of(m, library) == GripKey.Plain(Exercise.DYNAMOMETER)
    else -> GripKey.of(m, library) == GripKey.Library(filter)
}

private enum class HandFilter(val hand: Hand?, val label: String) {
    ALL(null, "Todas"), RIGHT(Hand.RIGHT, "Derecha"), LEFT(Hand.LEFT, "Izquierda")
}

/** "Historial": one row per day and grip with both hands, newest first, filterable and exportable to CSV. */
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
    var showWorkouts by rememberSaveable { mutableStateOf(false) }
    var handFilter by rememberSaveable { mutableStateOf(HandFilter.ALL) }
    // null = all; otherwise a library grip id, or DYNAMOMETER_FILTER.
    var gripFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(true) }
    var toDelete by remember { mutableStateOf<Measurement?>(null) }
    var editingRecord by remember { mutableStateOf<Measurement?>(null) }
    var editingGroup by remember { mutableStateOf<HistoryGroup?>(null) }
    val filtered = measurements.filter {
        (handFilter.hand == null || it.hand == handFilter.hand) && matchesFilter(it, gripFilter, grips)
    }
    val plainName = { e: Exercise -> Labels.exercise(e) }
    val groups = remember(filtered, grips) { HistoryGroups.build(filtered, grips, plainName) }
    fun nameOf(m: Measurement) = GripKey.label(m, grips, plainName)

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
            if (groups.isEmpty()) {
                item { Text("Todavía no hay registros con este filtro", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
            }
            items(groups, key = { it.day.toString() + it.key.toString() }) { g ->
                GroupRow(g, onEditGroup = { editingGroup = g }, onEditRecord = { editingRecord = it }, onDeleteRecord = { toDelete = it })
            }
        }
        RgSecondaryButton(
            "Exportar datos (CSV)", { onExport(filtered) },
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp), icon = Icons.Rounded.IosShare,
        )
    }
    editingRecord?.let { m -> EditRecordDialog(m, nameOf(m), { editingRecord = null }) { onEdit(it); editingRecord = null } }
    editingGroup?.let { g ->
        EditGroupDialog(g, { editingGroup = null }, onEdit = onEdit, onDelete = onDelete)
    }
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

/** «Extensión · 25/11 — Dcha 13,0 · Izda 31,5 kg»; expandable when a hand has several records that day. */
@Composable
private fun GroupRow(g: HistoryGroup, onEditGroup: () -> Unit, onEditRecord: (Measurement) -> Unit, onDeleteRecord: (Measurement) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val several = g.right.records.size > 1 || g.left.records.size > 1
    RgCard(Modifier.fillMaxWidth(), onClick = onEditGroup, padding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.label, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, maxLines = 2)
                Text(formatRecordDay(g.latest), style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HandValue("Dcha", g.right.best?.kg, RgColors.RightHand)
                    HandValue("Izda", g.left.best?.kg, RgColors.LeftHand)
                }
            }
            if (several) IconButton(onClick = { open = !open }) {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, "Ver todos los del día", tint = RgColors.TextSecondary)
            }
        }
        if (open) g.all.sortedByDescending { it.epochMillis }.forEach { m ->
            val dt = Instant.fromEpochMilliseconds(m.epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
            Row(Modifier.fillMaxWidth().clickableNoRipple { onEditRecord(m) }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')} · ${Labels.hand(m.hand)} · ${formatKg(m.kg)} kg" +
                        (if (m.discomforts.isNotEmpty()) " · " + m.discomforts.joinToString { "${it.zone} ${it.level}/10" } else ""),
                    style = MaterialTheme.typography.bodySmall, color = RgColors.Navy, modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
                IconButton(onClick = { onDeleteRecord(m) }) { Icon(Icons.Outlined.DeleteOutline, "Borrar", tint = RgColors.TextSecondary) }
            }
        }
    }
}

@Composable
private fun HandValue(label: String, kg: Double?, color: Color) {
    Text(
        "$label ${kg?.let { formatKg(it) + " kg" } ?: "—"}",
        style = MaterialTheme.typography.titleSmall, color = RgColors.Navy,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = if (selected) Color.White else RgColors.Navy,
    modifier = Modifier.clip(CircleShape).background(if (selected) RgColors.Navy else RgColors.Surface)
        .clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 8.dp),
)

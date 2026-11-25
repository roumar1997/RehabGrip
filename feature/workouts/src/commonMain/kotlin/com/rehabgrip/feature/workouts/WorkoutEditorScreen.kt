package com.rehabgrip.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.grip.GripRecord
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import androidx.compose.ui.text.style.TextOverflow
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlin.math.roundToInt

/** Stable id per row so drag-to-reorder keeps state attached to the moving row. */
private class RowItem(val id: Int, val set: WorkoutSet)

/** FREZ-like session editor: table of sets, mass edit, drag to reorder, estimated duration. */
@Composable
fun WorkoutEditorScreen(
    initial: WorkoutTemplate,
    library: List<GripSpec>,
    injury: Injury,
    recordOf: (Grip, Hand) -> GripRecord?,
    onBack: () -> Unit,
    onSave: (WorkoutTemplate) -> Unit,
    onDelete: (() -> Unit)?,
    onSaveGrip: (GripSpec) -> Unit,
    /** Hosts the «Evaluar agarre» flow without leaving (and losing) this draft. */
    evaluateContent: @Composable (GripSpec, () -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var handMode by remember { mutableStateOf(initial.handMode) }
    var firstHand by remember { mutableStateOf(initial.firstHand) }
    var nextId by remember { mutableStateOf(initial.sets.size) }
    val rows = remember { mutableStateListOf(*initial.sets.mapIndexed { i, s -> RowItem(i, s) }.toTypedArray()) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }
    var reordering by remember { mutableStateOf(false) }
    var editRow by remember { mutableStateOf<Int?>(null) }
    var configRow by remember { mutableStateOf<Int?>(null) }
    var massEdit by remember { mutableStateOf(false) }
    var evaluating by remember { mutableStateOf<GripSpec?>(null) }

    val draft = initial.copy(name = name, sets = rows.map { it.set }, handMode = handMode, firstHand = firstHand)
    evaluating?.let { grip ->
        evaluateContent(grip) { evaluating = null }
        return
    }
    configRow?.let { id ->
        val idx = rows.indexOfFirst { it.id == id }
        SetConfigScreen(
            index = idx,
            set = rows[idx].set,
            library = library,
            injury = injury,
            recordOf = recordOf,
            onBack = { configRow = null },
            onChange = { rows[idx] = RowItem(id, it) },
            onDelete = { rows.removeAt(idx); configRow = null },
            onSaveGrip = onSaveGrip,
            onEvaluate = { evaluating = it },
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        RgTopBar(if (initial.id == 0L) "Nueva sesión" else "Editar sesión", onBack = onBack) {
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Borrar sesión", tint = RgColors.TextSecondary) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                name, { name = it.take(40) }, label = { Text("Nombre") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            RgSegmented(HandMode.entries, handMode, { WorkoutLabels.handMode(it) }, { handMode = it })
            Text(WorkoutLabels.handModeHelp(handMode), style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
            RgSegmented(
                Hand.entries, firstHand,
                { if (handMode == HandMode.SINGLE) "Mano ${Labels.hand(it).lowercase()}" else "Empieza ${Labels.hand(it).lowercase()}" },
                { firstHand = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(if (selecting) "Cancelar" else "Edición masiva", Icons.Rounded.EditNote, selecting) {
                    selecting = !selecting
                    reordering = false
                    selected.clear()
                }
                ToolButton(if (reordering) "Hecho" else "Reordenar", Icons.Rounded.SwapVert, reordering) {
                    reordering = !reordering
                    selecting = false
                }
            }
            HeaderRow(selecting || reordering)
            SetsTable(
                rows = rows,
                selecting = selecting,
                reordering = reordering,
                selected = selected,
                onRowClick = { editRow = it },
                onGripClick = { configRow = it },
            )
            TextButton(onClick = {
                rows.add(RowItem(nextId, rows.lastOrNull()?.set ?: WorkoutSet(reps = 6, workSeconds = 10, restSeconds = 20)))
                nextId++
            }) {
                Icon(Icons.Rounded.Add, null)
                Text("Añadir serie")
            }
            if (selecting && selected.isNotEmpty()) {
                RgPrimaryButton("Editar ${selected.size} series", { massEdit = true })
            }
        }
        val problems = draft.validate()
        Column(Modifier.padding(16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            RgPrimaryButton("Guardar", { onSave(draft) }, enabled = problems.isEmpty())
            Text(
                problems.firstOrNull() ?: "Duración estimada: ${WorkoutLabels.estimate(draft)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (problems.isEmpty()) RgColors.Navy else RgColors.Danger,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    editRow?.let { id ->
        val idx = rows.indexOfFirst { it.id == id }
        ValuesDialog("Serie ${idx + 1}", rows[idx].set, onDismiss = { editRow = null }) { reps, work, rest ->
            rows[idx] = RowItem(id, rows[idx].set.copy(reps = reps ?: rows[idx].set.reps, workSeconds = work ?: rows[idx].set.workSeconds, restSeconds = rest ?: rows[idx].set.restSeconds))
            editRow = null
        }
    }
    if (massEdit) {
        ValuesDialog("Edición masiva (${selected.size})", null, onDismiss = { massEdit = false }) { reps, work, rest ->
            rows.indices.forEach { i ->
                val r = rows[i]
                if (r.id in selected) {
                    rows[i] = RowItem(r.id, r.set.copy(reps = reps ?: r.set.reps, workSeconds = work ?: r.set.workSeconds, restSeconds = rest ?: r.set.restSeconds))
                }
            }
            massEdit = false
            selecting = false
            selected.clear()
        }
    }
}

@Composable
private fun RowScope.ToolButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
            .background(if (active) RgColors.Navy else RgColors.Surface)
            .clickableNoRipple(onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) androidx.compose.ui.graphics.Color.White else RgColors.Primary, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (active) androidx.compose.ui.graphics.Color.White else RgColors.Navy)
    }
}

private const val GRIP_WEIGHT = 2.6f

@Composable
private fun HeaderRow(withLead: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (withLead) Box(Modifier.width(44.dp))
        listOf("#", "REPS", "WORK", "REST").forEach {
            Text(it, style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        Text("GRIP", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(GRIP_WEIGHT))
    }
}

@Composable
private fun SetsTable(
    rows: MutableList<RowItem>,
    selecting: Boolean,
    reordering: Boolean,
    selected: MutableList<Int>,
    onRowClick: (Int) -> Unit,
    onGripClick: (Int) -> Unit,
) {
    val rowHeight = 56.dp
    val rowPx = with(LocalDensity.current) { (rowHeight + 6.dp).toPx() }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, item ->
            key(item.id) {
                val dragging = dragId == item.id
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f; shadowElevation = if (dragging) 12f else 0f }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (item.id in selected) RgColors.SurfaceTint else RgColors.Surface)
                        .clickableNoRipple {
                            when {
                                selecting -> if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                                !reordering -> onRowClick(item.id)
                            }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        Checkbox(item.id in selected, null, colors = CheckboxDefaults.colors(checkedColor = RgColors.Primary), modifier = Modifier.width(44.dp))
                    }
                    if (reordering) {
                        Icon(
                            Icons.Rounded.DragHandle, "Arrastrar", tint = RgColors.TextSecondary,
                            modifier = Modifier.width(44.dp).pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { dragId = item.id; dragOffset = 0f },
                                    onDragEnd = { dragId = null; dragOffset = 0f },
                                    onDragCancel = { dragId = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val from = rows.indexOfFirst { it.id == item.id }
                                    val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, rows.lastIndex)
                                    if (to != from) {
                                        rows.add(to, rows.removeAt(from))
                                        dragOffset -= (to - from) * rowPx
                                    }
                                }
                            },
                        )
                    }
                    Cell("${index + 1}")
                    Cell("${item.set.reps}")
                    Cell("${item.set.workSeconds}s")
                    Cell("${item.set.restSeconds}s")
                    Row(
                        Modifier.weight(GRIP_WEIGHT).height(rowHeight).clip(RoundedCornerShape(10.dp))
                            .let { if (selecting || reordering) it else it.clickableNoRipple { onGripClick(item.id) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            WorkoutLabels.grip(item.set.grip), style = MaterialTheme.typography.labelMedium, color = RgColors.Navy,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Rounded.PanTool, "Configurar serie", tint = RgColors.Primary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Cell(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))

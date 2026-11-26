package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.grip.GripPalette
import com.rehabgrip.core.domain.grip.GripStyles
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.GripType

/**
 * Creates or edits a grip of the library: free name, edge width in mm (any value),
 * number of fingers (1-4) and which ones, and the hold type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GripEditorDialog(
    initial: GripSpec,
    onDismiss: () -> Unit,
    onSave: (GripSpec) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** The whole library: lets a new grip start with the first free colour. */
    library: List<GripSpec> = emptyList(),
) {
    var name by remember { mutableStateOf(initial.name) }
    var mm by remember { mutableStateOf(initial.edgeMm.toString()) }
    var count by remember { mutableStateOf(initial.fingerCount) }
    var which by remember { mutableStateOf(initial.fingerNote) }
    var type by remember { mutableStateOf(initial.type) }
    var color by remember { mutableStateOf(GripStyles.suggestedColor(initial, library)) }
    var letters by remember { mutableStateOf(initial.initials) }
    val draft = initial.copy(
        name = name.trim(), edgeMm = mm.toIntOrNull() ?: 0, fingerCount = count,
        fingerNote = if (GripSpec.fingerChoices(count).contains(which)) which else "", type = type,
        color = color, initials = GripStyles.sanitize(letters),
    )
    val colorConflict = GripStyles.conflict(color, library, initial.id)
    val problems = draft.validate()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text(if (initial.id == 0L) "Nuevo agarre" else "Editar agarre") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it.take(GripSpec.MAX_NAME) }, label = { Text("Nombre (opcional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    mm, { mm = it.filter(Char::isDigit).take(2) }, label = { Text("Ancho de regleta (mm)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 20, 25, 30).forEach { v -> GripChip("$v mm", mm == v.toString()) { mm = v.toString() } }
                }
                Text("Dedos", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { n -> GripChip("$n", count == n) { count = n } }
                }
                val choices = GripSpec.fingerChoices(count)
                if (choices.isNotEmpty()) {
                    Text("¿Cuáles?", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { c -> GripChip(c, draft.fingerNote == c) { which = if (which == c) "" else c } }
                    }
                }
                Text("Tipo de agarre", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GripType.entries.forEach { t -> GripChip(Labels.gripType(t), type == t) { type = t } }
                }
                Text("Siglas y color en Evolución", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                OutlinedTextField(
                    letters, { letters = GripStyles.sanitize(it) }, label = { Text("Siglas (máx. 3)") }, singleLine = true,
                    placeholder = { Text(GripStyles.codeOf(draft.copy(initials = ""))) }, modifier = Modifier.fillMaxWidth(),
                )
                GripColorPicker(color, { color = it }, GripStyles.takenBy(library, initial.id), colorConflict)
                Text(
                    problems.firstOrNull() ?: draft.label + if (draft.name.isNotBlank()) "  ·  ${draft.summary}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problems.isEmpty()) RgColors.TextSecondary else RgColors.Danger,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }, enabled = problems.isEmpty() && colorConflict == null) { Text("Guardar") } },
        dismissButton = {
            Column {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Borrar", color = RgColors.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun GripChip(text: String, selected: Boolean, onClick: () -> Unit) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = if (selected) Color.White else RgColors.Navy,
    modifier = Modifier.clip(CircleShape).background(if (selected) RgColors.Primary else RgColors.SurfaceTint)
        .clickableNoRipple(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
)

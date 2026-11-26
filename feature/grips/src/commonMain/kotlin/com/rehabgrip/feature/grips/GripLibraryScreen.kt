package com.rehabgrip.feature.grips

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.rehabgrip.core.designsystem.components.GripChip
import com.rehabgrip.core.domain.workout.Grip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.GripEditorDialog
import com.rehabgrip.core.designsystem.components.GripRecordsLines
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgInfoBanner
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.grip.GripRecordRow
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.grip.GripUsage
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.WorkoutTemplate

/** «Mis agarres»: the library with the evaluated max of each hand; create, edit and evaluate. */
@Composable
fun GripLibraryScreen(
    rows: List<GripRecordRow>,
    injury: Injury,
    onBack: () -> Unit,
    onSave: (GripSpec) -> Unit,
    templates: List<WorkoutTemplate>,
    /** Deletes [grip]; sets that used it move to the replacement. */
    onDelete: (GripSpec, Long?) -> Unit,
    onEvaluate: (GripSpec) -> Unit,
) {
    var editing by remember { mutableStateOf<GripSpec?>(null) }
    var deleting by remember { mutableStateOf<GripSpec?>(null) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Mis agarres", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                RgInfoBanner(
                    "Evalúa cada agarre antes de entrenar: su máximo por mano es la base del «Umbral basado en registro».",
                    Icons.Rounded.Info,
                )
            }
            items(rows, key = { it.grip.id }) { row ->
                RgCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.grip.label, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
                            if (row.grip.name.isNotBlank()) {
                                Text(row.grip.summary, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                            }
                        }
                        IconButton(onClick = { editing = row.grip }) { Icon(Icons.Rounded.Edit, "Editar agarre", tint = RgColors.TextSecondary) }
                    }
                    GripRecordsLines(row.right, row.left, injury, Modifier.padding(vertical = 8.dp))
                    RgSecondaryButton("Evaluar agarre", { onEvaluate(row.grip) }, icon = Icons.Rounded.Speed)
                }
            }
        }
        RgPrimaryButton("Nuevo agarre", { editing = GripSpec() }, Modifier.padding(16.dp).navigationBarsPadding(), icon = Icons.Rounded.Add)
    }
    deleting?.let { grip ->
        DeleteGripDialog(grip, rows, templates, onCancel = { deleting = null }) { replacement ->
            onDelete(grip, replacement)
            deleting = null
        }
    }
    editing?.let { grip ->
        GripEditorDialog(
            initial = grip,
            library = rows.map { it.grip },
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = if (grip.id != 0L && grip.builtIn == null) ({ deleting = grip; editing = null }) else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Confirms the deletion; when sessions use the grip it lists them and forces a replacement. */
@Composable
private fun DeleteGripDialog(
    grip: GripSpec,
    rows: List<GripRecordRow>,
    templates: List<WorkoutTemplate>,
    onCancel: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val users = GripUsage.usedBy(grip, templates)
    val options = GripUsage.replacements(grip, rows.map { it.grip })
    var replacement by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = RgColors.Background,
        title = { Text("¿Borrar «${grip.label}»?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (users.isEmpty()) {
                    Text("Ninguna sesión lo usa. Sus evaluaciones y registros se conservan en el historial.")
                } else {
                    Text("Lo usan estas sesiones:", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                    users.forEach { t ->
                        val n = t.sets.count { (it.grip as? Grip.Library)?.grip?.id == grip.id }
                        Text("• ${t.name} (${if (n == 1) "1 serie" else "$n series"})")
                    }
                    Text("Elige con qué agarre sustituirlo en esas series:", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { o -> GripChip(o.label, replacement == o.id) { replacement = o.id } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(replacement) }, enabled = users.isEmpty() || replacement != null) {
                Text(if (users.isEmpty()) "Borrar" else "Sustituir y borrar", color = if (users.isEmpty() || replacement != null) RgColors.Danger else RgColors.TextSecondary)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

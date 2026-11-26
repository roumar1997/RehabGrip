package com.rehabgrip.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgDateField
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatRecordDay
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.usecase.ManualKgParser

/** Editar un registro: fecha (nunca futura), mano y valor. */
@Composable
internal fun EditRecordDialog(m: Measurement, name: String, onDismiss: () -> Unit, onSave: (Measurement) -> Unit) {
    var at by remember { mutableStateOf<Long?>(m.epochMillis) }
    var hand by remember { mutableStateOf(m.hand) }
    var text by remember { mutableStateOf(formatKg(m.kg).replace(',', '.')) }
    val kg = ManualKgParser.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Editar · $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RgDateField(at, { at = it ?: System_now() })
                Text("Mano", color = RgColors.Navy)
                RgSegmented(Hand.entries, hand, { Labels.hand(it) }, { hand = it })
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text("Kilos") },
                    singleLine = true, isError = kg == null, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { kg?.let { onSave(m.copy(epochMillis = at ?: m.epochMillis, hand = hand, kg = it)) } },
                enabled = kg != null,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Mover un entrenamiento guardado a otra fecha. */
@Composable
internal fun EditWorkoutDateDialog(currentMillis: Long, name: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var at by remember { mutableStateOf<Long?>(currentMillis) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Fecha de «$name»") },
        text = { RgDateField(at, { at = it ?: System_now() }) },
        confirmButton = { TextButton(onClick = { onSave(at ?: currentMillis) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun System_now(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

/**
 * Edit a day + grip row: the date applies to both hands, each hand has its own kilos, and moving
 * a record to the other hand sits in a folded «avanzado» section. Hands can be deleted one by one.
 */
@Composable
internal fun EditGroupDialog(
    g: com.rehabgrip.core.domain.grip.HistoryGroup,
    onDismiss: () -> Unit,
    onEdit: (Measurement) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var at by remember { mutableStateOf<Long?>(g.latest) }
    var rightText by remember { mutableStateOf(g.right.best?.let { formatKg(it.kg).replace(',', '.') }.orEmpty()) }
    var leftText by remember { mutableStateOf(g.left.best?.let { formatKg(it.kg).replace(',', '.') }.orEmpty()) }
    var advanced by remember { mutableStateOf(false) }
    var rightToLeft by remember { mutableStateOf(false) }
    var leftToRight by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Hand?>(null) }
    val rightKg = ManualKgParser.parse(rightText)
    val leftKg = ManualKgParser.parse(leftText)
    val valid = (rightText.isBlank() || rightKg != null) && (leftText.isBlank() || leftKg != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Editar · ${g.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RgDateField(at, { at = it ?: System_now() }, label = "Fecha (las dos manos)")
                Hand.entries.forEach { h ->
                    val has = g.of(h).records.isNotEmpty()
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            if (h == Hand.RIGHT) rightText else leftText,
                            { v -> val f = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6); if (h == Hand.RIGHT) rightText = f else leftText = f },
                            label = { Text("Kilos · ${Labels.hand(h).lowercase()}") }, singleLine = true, enabled = has,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        if (has) TextButton(onClick = { deleting = h }) { Text("Borrar", color = RgColors.Danger) }
                    }
                }
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar avanzado" else "Avanzado…") }
                if (advanced) {
                    Text("Mover a la otra mano", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.rehabgrip.core.designsystem.components.GripChip("Derecha → izquierda", rightToLeft) { rightToLeft = !rightToLeft }
                        com.rehabgrip.core.designsystem.components.GripChip("Izquierda → derecha", leftToRight) { leftToRight = !leftToRight }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val target = at ?: g.latest
                    g.all.forEach { m ->
                        val newKg = when {
                            m.id == g.right.best?.id -> rightKg
                            m.id == g.left.best?.id -> leftKg
                            else -> null
                        } ?: m.kg
                        val newHand = when {
                            m.hand == Hand.RIGHT && rightToLeft -> Hand.LEFT
                            m.hand == Hand.LEFT && leftToRight -> Hand.RIGHT
                            else -> m.hand
                        }
                        val edited = m.copy(epochMillis = com.rehabgrip.core.domain.model.RecordDate.moveToDayOf(m.epochMillis, target), kg = newKg, hand = newHand)
                        if (edited != m) onEdit(edited)
                    }
                    onDismiss()
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    deleting?.let { h ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar la mano ${Labels.hand(h).lowercase()}?") },
            text = { Text("${g.label} · ${formatRecordDay(g.latest)} · ${g.of(h).records.size} registro(s). No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { g.of(h).records.forEach { onDelete(it.id) }; deleting = null; onDismiss() }) { Text("Borrar", color = RgColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

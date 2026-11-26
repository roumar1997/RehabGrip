package com.rehabgrip.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

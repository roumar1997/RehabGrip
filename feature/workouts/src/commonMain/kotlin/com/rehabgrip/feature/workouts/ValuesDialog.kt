package com.rehabgrip.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.workout.WorkoutSet

/**
 * Reps / work / rest. With [current] it edits one row; without it (mass edit)
 * empty fields mean "leave as is".
 */
@Composable
internal fun ValuesDialog(
    title: String,
    current: WorkoutSet?,
    onDismiss: () -> Unit,
    onApply: (reps: Int?, work: Int?, rest: Int?) -> Unit,
) {
    var reps by remember { mutableStateOf(current?.reps?.toString() ?: "") }
    var work by remember { mutableStateOf(current?.workSeconds?.toString() ?: "") }
    var rest by remember { mutableStateOf(current?.restSeconds?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current == null) {
                    Text("Deja vacío lo que no quieras cambiar", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                }
                NumberField("Repeticiones", reps) { reps = it }
                NumberField("Tirar (s)", work) { work = it }
                NumberField("Descanso (s)", rest) { rest = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(reps.toIntOrNull(), work.toIntOrNull(), rest.toIntOrNull()) }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Keeps its own text so typing "1" does not get reformatted to "1,00" mid-edit. */
@Composable
internal fun DecimalField(label: String, initial: Double?, modifier: Modifier = Modifier, onValue: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let(WorkoutLabels::kg)?.trimEnd('0')?.trimEnd(',') ?: "") }
    NumberField(label, text, decimal = true, modifier = modifier) {
        text = it
        onValue(it.replace(',', '.').toDoubleOrNull())
    }
}

@Composable
internal fun IntField(label: String, initial: Int?, modifier: Modifier = Modifier, onValue: (Int?) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    NumberField(label, text, modifier = modifier) {
        text = it
        onValue(it.toIntOrNull())
    }
}

@Composable
internal fun NumberField(label: String, value: String, decimal: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

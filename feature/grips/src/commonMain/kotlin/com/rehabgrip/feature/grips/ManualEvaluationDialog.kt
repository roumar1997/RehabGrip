package com.rehabgrip.feature.grips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.usecase.ManualKgParser

/** Typed values for one hand: max is required, min and average are optional. */
@Composable
internal fun ManualEvaluationDialog(hand: Hand, onDismiss: () -> Unit, onSave: (Double, Double?, Double?) -> Unit) {
    var max by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var avg by remember { mutableStateOf("") }
    val maxKg = ManualKgParser.parse(max)
    val minKg = ManualKgParser.parse(min)
    val avgKg = ManualKgParser.parse(avg)
    val problem = when {
        maxKg == null -> "Escribe el máximo en kg"
        min.isNotBlank() && minKg == null -> "Mínimo no válido"
        avg.isNotBlank() && avgKg == null -> "Media no válida"
        minKg != null && minKg > maxKg -> "El mínimo no puede superar al máximo"
        avgKg != null && (avgKg > maxKg || avgKg < (minKg ?: 0.0)) -> "La media debe quedar entre el mínimo y el máximo"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Valores a mano · ${Labels.hand(hand).lowercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Máximo (kg)", max) { max = it }
                field("Mínimo (kg, opcional)", min) { min = it }
                field("Media (kg, opcional)", avg) { avg = it }
                if (max.isNotBlank() && problem != null) Text(problem, style = MaterialTheme.typography.bodySmall, color = RgColors.Danger)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(maxKg!!, minKg, avgKg) }, enabled = problem == null) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value, { onChange(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) },
    label = { Text(label) }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
)

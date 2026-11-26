package com.rehabgrip.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.DiscomfortEditor
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.usecase.ManualKgParser

/** Typed value (dynamometer or any exercise without the scale) plus optional discomforts. */
@Composable
fun ManualEntryDialog(
    title: String,
    hand: Hand,
    recentZones: List<String>,
    onDismiss: () -> Unit,
    onSave: (kg: Double, note: String, discomforts: List<Discomfort>, at: Long?) -> Unit,
) {
    var at by remember { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var discomforts by remember { mutableStateOf(emptyList<Discomfort>()) }
    val kg = ManualKgParser.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = RgColors.Background,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = RgColors.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                    label = { Text("Kilos") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = text.isNotEmpty() && kg == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("Notas (opcional)") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                com.rehabgrip.core.designsystem.components.RgDateField(at, { at = it })
                DiscomfortEditor(discomforts, { discomforts = it }, recentZones, hand)
            }
        },
        confirmButton = {
            TextButton(onClick = { kg?.let { onSave(it, note, discomforts, at) } }, enabled = kg != null) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

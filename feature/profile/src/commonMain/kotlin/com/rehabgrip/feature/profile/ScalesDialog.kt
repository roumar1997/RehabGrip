package com.rehabgrip.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.ble.SavedScale
import com.rehabgrip.core.designsystem.theme.RgColors

/** «Mis básculas»: the known scales, one active (the one the phone listens to), each with its own name. */
@Composable
internal fun ScalesDialog(
    scales: List<SavedScale>,
    activeId: String?,
    onDismiss: () -> Unit,
    onUse: (SavedScale) -> Unit,
    onRename: (String, String) -> Unit,
    onForget: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf<SavedScale?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Mis básculas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (scales.isEmpty()) {
                    Text(
                        "Aún no hay ninguna. Se guarda sola la primera que se conecta, o elige una en «Báscula · diagnóstico Bluetooth».",
                        style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary,
                    )
                }
                scales.forEach { s ->
                    val active = s.id == activeId
                    Column {
                        Text(s.label + if (active) " · activa" else "", style = MaterialTheme.typography.titleSmall, color = if (active) RgColors.Success else RgColors.Navy)
                        Text(s.id, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (!active) TextButton(onClick = { onUse(s) }) { Text("Usar esta", maxLines = 1) }
                            TextButton(onClick = { renaming = s }) { Text("Nombre", maxLines = 1) }
                            TextButton(onClick = { onForget(s.id) }) { Text("Olvidar", color = RgColors.Danger, maxLines = 1) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
    renaming?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.customName ?: s.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Nombre de la báscula") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { onRename(s.id, name); renaming = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }
}

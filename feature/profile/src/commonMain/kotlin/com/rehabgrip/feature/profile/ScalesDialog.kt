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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.rehabgrip.core.ble.AdvertisementScanner
import com.rehabgrip.core.ble.DiagnosticsAccumulator
import com.rehabgrip.core.ble.LiveScales
import com.rehabgrip.core.ble.SavedScale
import com.rehabgrip.core.ble.SeenDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import com.rehabgrip.core.designsystem.theme.RgColors

private const val SCAN_SECONDS = 8

/** «Mis básculas»: the known scales, one active (the one the phone listens to), each with its own name. */
@Composable
internal fun ScalesDialog(
    scales: List<SavedScale>,
    activeId: String?,
    scannerFactory: (() -> AdvertisementScanner)?,
    ensureBluetooth: suspend () -> Boolean,
    onDetected: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    onUse: (SavedScale) -> Unit,
    onRename: (String, String) -> Unit,
    onForget: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf<SavedScale?>(null) }
    var seen by remember { mutableStateOf<List<SeenDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val scanner = remember { scannerFactory?.invoke() }
    // About 8 s of live scan when the list opens: every scale seen is listed and saved automatically.
    LaunchedEffect(Unit) {
        if (scanner == null || !ensureBluetooth()) return@LaunchedEffect
        val acc = DiagnosticsAccumulator()
        val mark = TimeSource.Monotonic.markNow()
        acc.reset(0)
        scanning = true
        scanner.start()
        val collector = launch { scanner.advertisements.collect { acc.add(it, mark.elapsedNow().inWholeMilliseconds) } }
        val reported = mutableSetOf<String>()
        while (mark.elapsedNow().inWholeSeconds < SCAN_SECONDS) {
            delay(500)
            seen = acc.devices()
            seen.filter { it.looksLikeScale && reported.add(it.id) }.forEach { onDetected(it.id, it.name) }
        }
        collector.cancel()
        scanner.stop()
        scanning = false
    }
    DisposableEffect(Unit) { onDispose { scanner?.stop() } }
    val rows = LiveScales.merge(scales, seen)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text("Mis básculas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (scanning) "Buscando básculas cercanas…" else "Búsqueda terminada",
                    style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                )
                if (rows.isEmpty()) {
                    Text(
                        if (scanning) "Enciende la báscula y acércala."
                        else "No se ha visto ninguna. Enciéndela y vuelve a abrir esta lista.",
                        style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary,
                    )
                }
                rows.forEach { row ->
                    val s = row.scale
                    val active = s.id == activeId
                    Column {
                        Text(s.label + if (active) " · activa" else "", style = MaterialTheme.typography.titleSmall, color = if (active) RgColors.Success else RgColors.Navy)
                        Text(
                            if (row.visible) "Detectada ahora · ${row.rssi?.toString() ?: "?"} dBm" else "No se ve ahora",
                            style = MaterialTheme.typography.bodySmall, color = if (row.visible) RgColors.Success else RgColors.TextSecondary, maxLines = 1,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (!active) TextButton(onClick = { onUse(s) }) { Text("Usar esta", maxLines = 1) }
                            TextButton(onClick = { renaming = s }, enabled = row.saved) { Text("Nombre", maxLines = 1) }
                            TextButton(onClick = { onForget(s.id) }, enabled = row.saved) { Text("Olvidar", color = RgColors.Danger, maxLines = 1) }
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

package com.rehabgrip.feature.measure

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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.ble.AdvertisementScanner
import com.rehabgrip.core.ble.DiagnosticsAccumulator
import com.rehabgrip.core.ble.FrameRecorder
import com.rehabgrip.core.ble.RecordedFrame
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.ble.SeenDevice
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private const val SCAN_SECONDS = 15
private const val RECORD_SECONDS = 30

/** A device being recorded: its id and a label for the report. */
private data class Recording(val device: SeenDevice, val round: Int)

/**
 * Perfil, Báscula: scans 15 s and lists every BLE device seen, marking the WH-C06. Each device
 * can be copied alone, chosen as THE scale, or have its frames recorded for 30 s.
 */
@Composable
fun DiagnosticsScreen(
    scanner: AdvertisementScanner,
    usedId: String?,
    onUse: (id: String, name: String?) -> Unit,
    onBack: () -> Unit,
) {
    val acc = remember { DiagnosticsAccumulator() }
    var devices by remember { mutableStateOf<List<SeenDevice>>(emptyList()) }
    var secondsLeft by remember { mutableStateOf(SCAN_SECONDS) }
    var round by remember { mutableStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordLeft by remember { mutableStateOf(0) }
    var frames by remember { mutableStateOf<List<RecordedFrame>>(emptyList()) }
    var frameReport by remember { mutableStateOf("") }
    val status by scanner.status.collectAsState()
    val clipboard = LocalClipboardManager.current
    fun copy(text: String) = clipboard.setText(AnnotatedString(text))

    LaunchedEffect(round) {
        val mark = TimeSource.Monotonic.markNow()
        acc.reset(0)
        devices = emptyList()
        secondsLeft = SCAN_SECONDS
        scanner.start()
        val collector = launch { scanner.advertisements.collect { acc.add(it, mark.elapsedNow().inWholeMilliseconds) } }
        while (mark.elapsedNow().inWholeSeconds < SCAN_SECONDS) {
            delay(500)
            secondsLeft = (SCAN_SECONDS - mark.elapsedNow().inWholeSeconds).toInt().coerceAtLeast(0)
            devices = acc.devices()
        }
        collector.cancel()
        scanner.stop()
        secondsLeft = 0
        devices = acc.devices()
    }

    val rec = recording
    LaunchedEffect(rec) {
        if (rec == null) return@LaunchedEffect
        val recorder = FrameRecorder()
        val mark = TimeSource.Monotonic.markNow()
        frames = emptyList()
        recordLeft = RECORD_SECONDS
        scanner.start()
        val collector = launch {
            scanner.advertisements.collect { ad -> if (ad.id == rec.device.id) ad.manufacturer?.let(recorder::add) }
        }
        while (mark.elapsedNow().inWholeSeconds < RECORD_SECONDS) {
            delay(500)
            recordLeft = (RECORD_SECONDS - mark.elapsedNow().inWholeSeconds).toInt().coerceAtLeast(0)
            frames = recorder.frames()
        }
        collector.cancel()
        scanner.stop()
        recordLeft = 0
        frames = recorder.frames()
        frameReport = recorder.report(rec.device.name ?: rec.device.id, RECORD_SECONDS)
    }

    Column(Modifier.fillMaxSize()) {
        RgTopBar("Diagnóstico Bluetooth", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val problem = when (status) {
                    ScaleStatus.BLUETOOTH_OFF -> "Activa el Bluetooth del teléfono."
                    ScaleStatus.UNAUTHORIZED -> "Falta el permiso de Bluetooth."
                    else -> null
                }
                val line = when {
                    problem != null -> problem
                    rec != null && recordLeft > 0 -> "Grabando tramas… $recordLeft s"
                    secondsLeft > 0 -> "Escaneando… $secondsLeft s · ${devices.size} dispositivos"
                    else -> "Escaneo terminado · ${devices.size} dispositivos"
                }
                Text(line, style = MaterialTheme.typography.titleSmall, color = if (problem != null) RgColors.Danger else RgColors.Navy)
                if (secondsLeft == 0 && devices.none { it.looksLikeScale }) {
                    Text(
                        "No se ha visto ninguna WH-C06 (company 0x0100, firma 02 03 o nombre IF_B7/WH-C06/Crane). Comprueba que está encendida y cerca.",
                        style = MaterialTheme.typography.bodySmall, color = RgColors.Warning, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (rec != null) item { FramesCard(rec.device, frames, recordLeft, frameReport, ::copy) }
            items(devices, key = { it.id }) { d ->
                DeviceCard(
                    d, used = d.id == usedId, canRecord = secondsLeft == 0 && recordLeft == 0,
                    onCopy = { copy(DiagnosticsAccumulator.reportOne(d)) },
                    onUse = { onUse(d.id, d.name) },
                    onRecord = { recording = Recording(d, (rec?.round ?: 0) + 1) },
                )
            }
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RgSecondaryButton("Repetir", { recording = null; round++ }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                RgPrimaryButton(
                    "Copiar todo", { copy(DiagnosticsAccumulator.report(devices, SCAN_SECONDS)) },
                    Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, enabled = devices.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    d: SeenDevice, used: Boolean, canRecord: Boolean,
    onCopy: () -> Unit, onUse: () -> Unit, onRecord: () -> Unit,
) {
    RgCard(Modifier.fillMaxWidth()) {
        Text(
            (if (d.looksLikeScale) "WH-C06 · " else "") + (d.name ?: "(sin nombre)") + if (used) " · en uso" else "",
            style = MaterialTheme.typography.titleSmall, color = if (d.looksLikeScale) RgColors.Success else RgColors.Navy,
        )
        Text("RSSI ${d.rssi} dBm · ${d.advertisements} anuncios · ${(d.perSecond * 10).toInt() / 10.0}/s", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
        Text(
            "Company ${d.companyId?.let { "0x" + it.toString(16).padStart(4, '0') } ?: "—"}",
            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
        )
        if (d.manufacturerHex.isNotEmpty()) Text(d.manufacturerHex, style = MaterialTheme.typography.bodySmall, color = RgColors.Navy)
        d.interpretedKg?.let { Text("Peso interpretado: ${formatKg(it)} kg", style = MaterialTheme.typography.titleSmall, color = RgColors.Primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCopy) { Text("Copiar", maxLines = 1) }
            if (d.looksLikeScale && !used) TextButton(onClick = onUse) { Text("Usar esta báscula", maxLines = 1) }
            if (d.manufacturerHex.isNotEmpty()) TextButton(onClick = onRecord, enabled = canRecord) { Text("Grabar", maxLines = 1) }
        }
    }
}

@Composable
private fun FramesCard(d: SeenDevice, frames: List<RecordedFrame>, left: Int, report: String, copy: (String) -> Unit) {
    RgCard(Modifier.fillMaxWidth()) {
        Text("Tramas de ${d.name ?: d.id}", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
        Text(
            if (left > 0) "Grabando… $left s · ${frames.size} distintas" else "${frames.size} tramas distintas",
            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
        )
        frames.sortedByDescending { it.count }.take(40).forEach { f ->
            Text(
                "${if (f.jump) "SALTO " else ""}${f.hex}\n   x${f.count} · ${f.kg?.let { formatKg(it) + " kg" } ?: "sin peso"}",
                style = MaterialTheme.typography.bodySmall, color = if (f.jump) RgColors.Danger else RgColors.Navy,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (left == 0 && report.isNotEmpty()) TextButton(onClick = { copy(report) }) { Text("Copiar tramas") }
    }
}

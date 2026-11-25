package com.rehabgrip.feature.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.rehabgrip.core.ble.ScaleStatus
import com.rehabgrip.core.ble.SeenDevice
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private const val SCAN_SECONDS = 15

/** Perfil, Báscula: scans 15 s and lists every BLE device seen, marking the WH-C06. */
@Composable
fun DiagnosticsScreen(scanner: AdvertisementScanner, onBack: () -> Unit) {
    val acc = remember { DiagnosticsAccumulator() }
    var devices by remember { mutableStateOf<List<SeenDevice>>(emptyList()) }
    var secondsLeft by remember { mutableStateOf(SCAN_SECONDS) }
    var round by remember { mutableStateOf(0) }
    val status by scanner.status.collectAsState()
    val clipboard = LocalClipboardManager.current

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

    Column(Modifier.fillMaxSize()) {
        RgTopBar("Diagnóstico Bluetooth", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val problem = when (status) {
                    ScaleStatus.BLUETOOTH_OFF -> "Activa el Bluetooth del teléfono."
                    ScaleStatus.UNAUTHORIZED -> "Falta el permiso de Bluetooth."
                    else -> null
                }
                Text(
                    problem ?: if (secondsLeft > 0) "Escaneando… $secondsLeft s · ${devices.size} dispositivos" else "Escaneo terminado · ${devices.size} dispositivos",
                    style = MaterialTheme.typography.titleSmall, color = if (problem != null) RgColors.Danger else RgColors.Navy,
                )
                if (secondsLeft == 0 && devices.none { it.looksLikeScale }) {
                    Text(
                        "No se ha visto ninguna WH-C06 (company 0x0100 o nombre WH-C06/Crane). Comprueba que está encendida y cerca.",
                        style = MaterialTheme.typography.bodySmall, color = RgColors.Warning, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(devices, key = { it.id }) { DeviceCard(it) }
        }
        Row(Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RgSecondaryButton("Repetir", { round++ }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
            RgPrimaryButton(
                "Copiar informe", { clipboard.setText(AnnotatedString(DiagnosticsAccumulator.report(devices, SCAN_SECONDS))) },
                Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, enabled = devices.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun DeviceCard(d: SeenDevice) {
    RgCard(Modifier.fillMaxWidth()) {
        Text(
            (if (d.looksLikeScale) "WH-C06 · " else "") + (d.name ?: "(sin nombre)"),
            style = MaterialTheme.typography.titleSmall, color = if (d.looksLikeScale) RgColors.Success else RgColors.Navy,
        )
        Text("RSSI ${d.rssi} dBm · ${d.advertisements} anuncios · ${(d.perSecond * 10).toInt() / 10.0}/s", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
        Text(
            "Company ${d.companyId?.let { "0x" + it.toString(16).padStart(4, '0') } ?: "—"}",
            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
        )
        if (d.manufacturerHex.isNotEmpty()) Text(d.manufacturerHex, style = MaterialTheme.typography.bodySmall, color = RgColors.Navy)
        d.interpretedKg?.let { Text("Peso interpretado: ${formatKg(it)} kg", style = MaterialTheme.typography.titleSmall, color = RgColors.Primary) }
    }
}

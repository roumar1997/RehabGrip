package com.rehabgrip.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.illustrations.Brand
import com.rehabgrip.core.designsystem.theme.RgColors
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.remember
import com.rehabgrip.core.designsystem.components.InjuryForm
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.grip.FriendMaxes
import com.rehabgrip.core.domain.grip.MaxRow
import com.rehabgrip.core.domain.workout.GripSpec

data class ProfileUiState(
    val name: String,
    /** Improvement of the injured hand (right when none) in half crimp. */
    val focusImprovementPercent: Double?,
    /** Injured as % of healthy (right vs left when none). */
    val symmetryPercent: Double?,
    val simulatedScale: Boolean,
    val showDeveloperOptions: Boolean,
    val injury: Injury = Injury.NONE,
    /** Each grip of the library with the evaluated max of every hand. */
    val maxRows: List<MaxRow> = emptyList(),
    /** Name of the scale this phone is locked to (null = auto). */
    val scaleName: String? = null,
    val scaleLocked: Boolean = false,
    val scaleCustomName: String? = null,
    val simContaminated: Boolean = false,
    /** Other people's records, kept apart from mine. */
    val friends: List<FriendMaxes> = emptyList(),
)

/** "Perfil y configuración". */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRename: (String) -> Unit,
    onSimulatedScale: (Boolean) -> Unit,
    onLoadSampleData: () -> Unit,
    onInjury: (Injury) -> Unit = {},
    onReminders: () -> Unit = {},
    onGrips: () -> Unit = {},
    onEvaluate: (GripSpec) -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onForgetScale: () -> Unit = {},
    onScaleName: (String) -> Unit = {},
    onSimContaminated: (Boolean) -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Perfil y configuración", leadingIcon = Icons.Rounded.Settings)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RgCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(RgColors.SurfaceTint), contentAlignment = Alignment.Center) {
                        Image(Brand.Mountain, null, Modifier.fillMaxWidth(0.75f).aspectRatio(200f / 110f))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.name, style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy)
                        Text(
                            if (state.injury.hand == null) "Tu progreso (mano derecha)" else "Tu progreso (mano lesionada)",
                            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = RgColors.Gold, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                state.focusImprovementPercent?.let(::formatPercent) ?: "—",
                                style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1E8A55),
                            )
                        }
                        Text(
                            "Desde el inicio · ${if (state.injury.hand == null) "Dcha/Izda" else "Lesionada/sana"} " +
                                (state.symmetryPercent?.let { formatPercent(it, false) } ?: "—"),
                            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                        )
                    }
                }
            }
            GripRecordsCard(state, onGrips, onEvaluate)
            FriendRecordsCard(state)
            RgCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Option(Icons.Rounded.Badge, "Cambiar nombre") { dialog = DIALOG_NAME }
                HorizontalDivider(color = RgColors.Outline)
                Option(Icons.Rounded.Healing, "Mano y dedos lesionados") { dialog = DIALOG_INJURY }
                HorizontalDivider(color = RgColors.Outline)
                Option(Icons.Rounded.Notifications, "Recordatorios", onReminders)
                HorizontalDivider(color = RgColors.Outline)
                Option(Icons.Rounded.Bluetooth, "Báscula · diagnóstico Bluetooth", onDiagnostics)
                HorizontalDivider(color = RgColors.Outline)
                Option(Icons.Rounded.Badge, "Nombre de mi báscula (${state.scaleCustomName ?: "Báscula " + (state.scaleName ?: "IF_B7")})") { dialog = DIALOG_SCALE_NAME }
                if (state.scaleLocked) {
                    HorizontalDivider(color = RgColors.Outline)
                    Option(Icons.Rounded.Bluetooth, "Olvidar báscula (${state.scaleName ?: "elegida"})", onForgetScale)
                }
                HorizontalDivider(color = RgColors.Outline)
                Option(Icons.Rounded.Lightbulb, "Información y consejos") { dialog = DIALOG_TIPS }
                HorizontalDivider(color = RgColors.Outline)
                Option(Icons.Rounded.Info, "Acerca de Rehagrip") { dialog = DIALOG_ABOUT }
            }
            if (state.showDeveloperOptions) {
                RgCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = RgColors.Primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Báscula simulada", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                            Text("Solo pruebas: curva realista sin Bluetooth", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                        }
                        Switch(state.simulatedScale, onSimulatedScale, colors = SwitchDefaults.colors(checkedTrackColor = RgColors.Primary))
                    }
                    HorizontalDivider(color = RgColors.Outline)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Simulada contaminada", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
                            Text("Mezcla otro dispositivo (87 kg) y picos para probar el filtro", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                        }
                        Switch(state.simContaminated, onSimContaminated, colors = SwitchDefaults.colors(checkedTrackColor = RgColors.Primary))
                    }
                    HorizontalDivider(color = RgColors.Outline)
                    Option(Icons.Rounded.DataObject, "Cargar datos de ejemplo (3 semanas)") { dialog = DIALOG_SAMPLE }
                }
            }
            Footer()
        }
    }
    when (dialog) {
        DIALOG_INJURY -> InjuryDialog(state.injury, { dialog = null }) { onInjury(it); dialog = null }
        DIALOG_SCALE_NAME -> RenameDialog(state.scaleCustomName ?: "Báscula " + (state.scaleName ?: "IF_B7"), { dialog = null }, "Nombre de tu báscula") { onScaleName(it); dialog = null }
        DIALOG_NAME -> RenameDialog(state.name, { dialog = null }) { onRename(it); dialog = null }
        DIALOG_TIPS -> InfoDialog("Información y consejos", TIPS) { dialog = null }
        DIALOG_ABOUT -> InfoDialog("Acerca de Rehagrip", ABOUT) { dialog = null }
        DIALOG_SAMPLE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("¿Cargar datos de ejemplo?") },
            text = { Text("Sustituye TODOS los registros por 3 semanas de ejemplo. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { onLoadSampleData(); dialog = null }) { Text("Sustituir", color = RgColors.Danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Option(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RgColors.Primary)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = RgColors.TextSecondary)
    }
}

@Composable
private fun Footer() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RgColors.SurfaceTint)) {
        Image(Brand.Ridge, null, Modifier.fillMaxWidth().aspectRatio(4f).align(Alignment.BottomCenter), contentScale = ContentScale.FillWidth)
        Text(
            "Paso a paso,\nvolverás más fuerte 💪",
            style = MaterialTheme.typography.titleMedium, color = RgColors.Navy,
            modifier = Modifier.padding(18.dp),
        )
    }
    Spacer(Modifier.size(12.dp))
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, title: String = "Tu nombre", onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } },
)

private const val DIALOG_NAME = "name"
private const val DIALOG_SCALE_NAME = "scale_name"
private const val DIALOG_TIPS = "tips"
private const val DIALOG_ABOUT = "about"
private const val DIALOG_SAMPLE = "sample"
private const val DIALOG_INJURY = "injury"

@Composable
private fun InjuryDialog(current: Injury, onDismiss: () -> Unit, onSave: (Injury) -> Unit) {
    var injury by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        text = { InjuryForm(injury, { injury = it }) },
        confirmButton = { TextButton(onClick = { onSave(injury) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private val TIPS = """
    • Mide siempre en las mismas condiciones: misma regleta de 20 mm, mismo calentamiento y a una hora parecida.
    • Haz 2-3 intentos por mano con 2-3 minutos de descanso; guarda el mejor.
    • Compara la derecha con la izquierda: el objetivo es acercarte al 100 %.
    • Si aparece dolor, para y coméntalo con tu fisioterapeuta.
""".trimIndent()

private val ABOUT = """
    Rehagrip registra tu fuerza de agarre durante la rehabilitación.
    Funciona sin cuenta: todo se guarda solo en este móvil.
    Compatible con la báscula Crane Scale WH-C06 por Bluetooth (lee sus anuncios, no hace falta emparejarla).
    Tipografía Nunito (SIL Open Font License).
""".trimIndent()

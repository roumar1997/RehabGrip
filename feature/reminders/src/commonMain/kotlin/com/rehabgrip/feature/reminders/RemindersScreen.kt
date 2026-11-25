package com.rehabgrip.feature.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.reminder.Reminder
import kotlinx.datetime.DayOfWeek

private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

fun Reminder.timeText() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

fun Reminder.daysText(): String = when {
    days.size == 7 -> "Todos los días"
    days.isEmpty() -> "Sin días"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(" · ") { dayLetters[it.ordinal] }
}

/** Optional weekly reminders: create, toggle, delete and send a test. */
@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    onBack: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onTest: () -> Unit,
) {
    var editing by remember { mutableStateOf<Reminder?>(null) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Recordatorios", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Te avisamos los días y a la hora que elijas para que no se te pase el entrenamiento.",
                    style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary,
                )
            }
            if (reminders.isEmpty()) {
                item { Text("Todavía no tienes recordatorios", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy) }
            }
            items(reminders, key = { it.id }) { r ->
                RgCard(Modifier.fillMaxWidth(), onClick = { editing = r }, padding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.timeText(), style = MaterialTheme.typography.headlineMedium, color = if (r.enabled) RgColors.Navy else RgColors.TextSecondary)
                            Text(r.daysText(), style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
                        }
                        Switch(r.enabled, { onSave(r.copy(enabled = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = RgColors.Primary))
                        IconButton(onClick = { onDelete(r) }) { Icon(Icons.Outlined.DeleteOutline, "Borrar", tint = RgColors.TextSecondary) }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RgSecondaryButton("Enviar aviso de prueba", onTest, icon = Icons.Rounded.NotificationsActive)
            RgPrimaryButton("Nuevo recordatorio", {
                editing = Reminder(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), hour = 19, minute = 0)
            }, icon = Icons.Rounded.Add)
        }
    }
    editing?.let { r ->
        ReminderDialog(r, onDismiss = { editing = null }) {
            onSave(it)
            editing = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(initial: Reminder, onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    var days by remember { mutableStateOf(initial.days) }
    val time = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RgColors.Background,
        title = { Text(if (initial.id == 0L) "Nuevo recordatorio" else "Editar recordatorio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in days
                        Text(
                            dayLetters[d.ordinal],
                            style = MaterialTheme.typography.titleSmall,
                            color = if (on) Color.White else RgColors.Navy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.size(34.dp).clip(CircleShape)
                                .background(if (on) RgColors.Primary else RgColors.SurfaceTint)
                                .clickableNoRipple { days = if (on) days - d else days + d }
                                .padding(top = 7.dp),
                        )
                    }
                }
                TimeInput(time)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(days = days, hour = time.hour, minute = time.minute, enabled = true)) },
                enabled = days.isNotEmpty(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

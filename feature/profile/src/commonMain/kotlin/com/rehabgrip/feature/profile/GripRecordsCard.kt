package com.rehabgrip.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rehabgrip.core.designsystem.components.formatRecordDay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.GripRecordsLines
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.workout.GripSpec

/** «Mis agarres»: the evaluated max of each hand per grip and when it was set. */
@Composable
internal fun GripRecordsCard(state: ProfileUiState, onGrips: () -> Unit, onEvaluate: (GripSpec) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val withData = state.maxRows.filter { it.right != null || it.left != null }
    val last = withData.flatMap { listOfNotNull(it.right?.epochMillis, it.left?.epochMillis) }.maxOrNull()
    val summary = "${withData.size} agarres" + (last?.let { " · último " + formatRecordDay(it).take(5) } ?: "")
    RgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickableNoRipple { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, null, tint = RgColors.Primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Mis máximos", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary, maxLines = 1)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = RgColors.TextSecondary)
        }
        if (open) TextButton(onClick = onGrips) { Text("Mis agarres (editar y crear)") }
        if (open) state.maxRows.forEach { row ->
            HorizontalDivider(Modifier.padding(top = 10.dp), color = RgColors.Outline)
            Column(Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f))
                    row.grip?.let { g -> TextButton(onClick = { onEvaluate(g) }) { Text("Evaluar") } }
                }
                GripRecordsLines(row.right, row.left, state.injury)
            }
        }
    }
}

/** «Registros de amigos»: what other people pulled with the scale, never mixed with mine. */
@Composable
internal fun FriendRecordsCard(state: ProfileUiState) {
    if (state.friends.isEmpty()) return
    RgCard(Modifier.fillMaxWidth()) {
        Text("Registros de amigos", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        state.friends.forEach { f ->
            HorizontalDivider(Modifier.padding(top = 10.dp), color = RgColors.Outline)
            Text(f.person, style = MaterialTheme.typography.titleSmall, color = RgColors.Primary, modifier = Modifier.padding(top = 10.dp))
            f.rows.forEach { row ->
                Text(row.label, style = MaterialTheme.typography.bodyMedium, color = RgColors.Navy, modifier = Modifier.padding(top = 6.dp))
                GripRecordsLines(row.right, row.left, state.injury)
            }
        }
    }
}

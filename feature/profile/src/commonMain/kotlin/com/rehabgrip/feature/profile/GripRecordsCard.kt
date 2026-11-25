package com.rehabgrip.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Speed
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
    RgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickableNoRipple(onGrips), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, null, tint = RgColors.Primary)
            Text(
                "Mis máximos", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = RgColors.TextSecondary)
        }
        state.maxRows.forEach { row ->
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

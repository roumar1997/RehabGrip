package com.rehabgrip.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.HandMode

private enum class Sides { BOTH, RIGHT, LEFT }

/**
 * «Ajustes de la sesión» (collapsed by default): both hands is the normal case; here the
 * session can be limited to one hand, or the order of the hands changed.
 */
@Composable
internal fun SessionSettings(mode: HandMode, first: Hand, onChange: (HandMode, Hand) -> Unit) {
    var open by remember { mutableStateOf(mode == HandMode.SINGLE) }
    val sides = when {
        mode != HandMode.SINGLE -> Sides.BOTH
        first == Hand.RIGHT -> Sides.RIGHT
        else -> Sides.LEFT
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickableNoRipple { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ajustes de la sesión", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = RgColors.TextSecondary)
        }
        if (open) {
            Text("Manos", style = MaterialTheme.typography.labelLarge, color = RgColors.TextSecondary)
            RgSegmented(
                Sides.entries, sides,
                { when (it) { Sides.BOTH -> "Ambas"; Sides.RIGHT -> "Solo derecha"; Sides.LEFT -> "Solo izquierda" } },
                {
                    when (it) {
                        Sides.BOTH -> onChange(if (mode == HandMode.SINGLE) HandMode.ALTERNATING else mode, first)
                        Sides.RIGHT -> onChange(HandMode.SINGLE, Hand.RIGHT)
                        Sides.LEFT -> onChange(HandMode.SINGLE, Hand.LEFT)
                    }
                },
            )
            if (mode != HandMode.SINGLE) {
                RgSegmented(
                    listOf(HandMode.ALTERNATING, HandMode.BOTH_SEQUENTIAL), mode,
                    { WorkoutLabels.handMode(it) }, { onChange(it, first) },
                )
                Text(WorkoutLabels.handModeHelp(mode), style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                RgSegmented(
                    Hand.entries, first,
                    { if (it == Hand.RIGHT) "Empieza derecha" else "Empieza izquierda" },
                    { onChange(mode, it) },
                )
            }
        }
    }
}

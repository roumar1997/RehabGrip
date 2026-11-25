package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.grip.GripRecord
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury

/** One line per hand: its max in kg and the date; «sin evaluar» when there is no record. */
@Composable
fun GripRecordsLines(right: GripRecord?, left: GripRecord?, injury: Injury, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Hand.RIGHT to right, Hand.LEFT to left).forEach { (hand, record) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (hand == injury.hand) RgColors.Danger else if (hand == Hand.RIGHT) RgColors.RightHand else RgColors.LeftHand),
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Labels.hand(hand) + if (hand == injury.hand) " (lesionada)" else "",
                        style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                    )
                    record?.let {
                        Text(
                            formatShortDate(it.epochMillis) + if (it.fromEvaluation) "" else " · último registro",
                            style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary,
                        )
                    }
                }
                Text(
                    record?.let { formatKg(it.kg) + " kg" } ?: "sin evaluar",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (record == null) RgColors.Warning else RgColors.Navy,
                )
            }
        }
    }
}

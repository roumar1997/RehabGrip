package com.rehabgrip.feature.workouts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.HandPanel
import com.rehabgrip.core.domain.workout.HandState

/**
 * Split screen for alternating / both-hands sessions: IZQUIERDA and DERECHA side by side,
 * each with its own state and timer, the hand in turn highlighted and bigger.
 */
@Composable
internal fun HandColumns(panels: List<HandPanel>, modifier: Modifier = Modifier) {
    val byHand = panels.associateBy { it.hand }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Hand.LEFT, Hand.RIGHT).forEach { hand ->
            val panel = byHand[hand] ?: HandPanel(hand, HandState.WAIT, 0, inTurn = false)
            HandColumn(panel, Modifier.weight(1f))
        }
    }
}

internal fun handStateText(s: HandState) = when (s) {
    HandState.PULL -> "TIRA"
    HandState.REST -> "DESCANSA"
    HandState.WAIT -> "ESPERA"
}

@Composable
private fun HandColumn(panel: HandPanel, modifier: Modifier) {
    val color = handColor(panel.hand)
    val bg by animateColorAsState(
        when {
            panel.inTurn && panel.state == HandState.PULL -> color
            panel.inTurn -> color.copy(alpha = 0.72f)
            else -> RgColors.Surface
        },
    )
    val fg = if (panel.inTurn) Color.White else RgColors.Navy
    val seconds = if (panel.displayMs > 0) "${(panel.displayMs + 999) / 1000}" else "—"
    Column(
        modifier.height(214.dp).clip(RoundedCornerShape(24.dp)).background(bg)
            .let { if (panel.inTurn) it else it.border(BorderStroke(2.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(24.dp)) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            handName(panel.hand), color = if (panel.inTurn) Color.White else color,
            fontSize = if (panel.inTurn) 24.sp else 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        )
        Text(
            handStateText(panel.state), color = fg.copy(alpha = if (panel.inTurn) 1f else 0.8f),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        )
        Text(
            seconds, color = fg,
            fontSize = if (panel.inTurn) 78.sp else 46.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        )
        Text(
            if (panel.inTurn) "TE TOCA" else "",
            color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

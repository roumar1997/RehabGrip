package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.DiscomfortZones
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury

private enum class InjuryHandOption(val hand: Hand?, val label: String) {
    RIGHT(Hand.RIGHT, "Derecha"), LEFT(Hand.LEFT, "Izquierda"), NONE(null, "Ninguna")
}

/** Injured/operated hand + optional fingers (tap the drawing). Used in onboarding and profile. */
@Composable
fun InjuryForm(injury: Injury, onChange: (Injury) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Qué mano tienes lesionada u operada?", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        RgSegmented(
            options = InjuryHandOption.entries,
            selected = InjuryHandOption.entries.first { it.hand == injury.hand },
            label = { it.label },
            onSelect = { onChange(Injury(it.hand, if (it.hand == injury.hand) injury.fingers else emptySet())) },
            colorFor = { if (it.hand == null) RgColors.TextSecondary else RgColors.Danger },
        )
        val hand = injury.hand ?: return@Column
        Text(
            "Opcional: toca el dedo o dedos afectados",
            style = MaterialTheme.typography.bodyMedium,
            color = RgColors.TextSecondary,
        )
        HandFingerPicker(
            hand = hand,
            selected = injury.fingers,
            onToggle = { f -> onChange(injury.copy(fingers = if (f in injury.fingers) injury.fingers - f else injury.fingers + f)) },
            modifier = Modifier.widthIn(max = 220.dp).align(Alignment.CenterHorizontally).fillMaxWidth(0.6f),
        )
        Text(
            if (injury.fingers.isEmpty()) "Ningún dedo marcado"
            else injury.fingers.sortedBy { it.ordinal }.joinToString(", ") { DiscomfortZones.fingerName(it) }.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            color = if (injury.fingers.isEmpty()) RgColors.TextSecondary else RgColors.Danger,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** "lesionada" label helpers shared by several screens. */
object InjuryLabels {
    fun handRole(hand: Hand, injury: Injury): String = when (injury.hand) {
        null -> Labels.hand(hand)
        hand -> "${Labels.hand(hand)} (lesionada)"
        else -> "${Labels.hand(hand)} (sana)"
    }

    /** "Lesionada vs sana" or "Derecha vs izquierda" when nothing is injured. */
    fun comparisonTitle(injury: Injury): String =
        if (injury.hand == null) "Derecha respecto a izquierda" else "Lesionada respecto a sana"
}

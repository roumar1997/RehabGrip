package com.rehabgrip.feature.exercises

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgInfoBanner
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.components.RgSecondaryButton
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgStat
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.illustrations.GripIllustrations
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.Measurement

/** "Entrenamiento": choose hand, then connect the scale or type the value. */
@Composable
fun TrainingScreen(
    exercise: Exercise,
    history: List<Measurement>,
    onBack: () -> Unit,
    onConnectScale: (Hand) -> Unit,
    onManualSave: (hand: Hand, kg: Double, note: String, discomforts: List<Discomfort>, at: Long?) -> Unit,
    injury: Injury = Injury.NONE,
    recentZones: List<String> = emptyList(),
) {
    var hand by rememberSaveable { mutableStateOf(injury.focusHand) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    val mine = history.filter { it.exercise == exercise && it.hand == hand }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Entrenamiento", onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                Labels.exercise(exercise),
                style = MaterialTheme.typography.headlineSmall,
                color = RgColors.Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Image(
                GripIllustrations.of(exercise), Labels.exercise(exercise),
                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop,
            )
            RgSegmented(
                options = Hand.entries,
                selected = hand,
                label = { if (it == injury.hand) "${Labels.hand(it)} · lesionada" else "Mano ${Labels.hand(it).lowercase()}" },
                onSelect = { hand = it },
                colorFor = { if (it == injury.hand) RgColors.Danger else RgColors.Primary },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RgStat(
                    "Mejor (kg)", mine.maxOfOrNull { it.kg }?.let(::formatKg) ?: "—",
                    Modifier.weight(1f), icon = Icons.Rounded.EmojiEvents, iconTint = RgColors.Gold,
                )
                RgStat(
                    "Último (kg)", mine.maxByOrNull { it.epochMillis }?.kg?.let(::formatKg) ?: "—",
                    Modifier.weight(1f), icon = Icons.Rounded.History,
                )
            }
            RgInfoBanner(
                if (exercise.usesScale) "Cuelga la báscula de la regleta de 20 mm y tira con la mano ${Labels.hand(hand).lowercase()}. También puedes apuntar el valor a mano."
                else "Aprieta el dinamómetro con la mano ${Labels.hand(hand).lowercase()} y apunta el valor que marque.",
                Icons.Rounded.Info,
            )
            if (exercise.usesScale) {
                RgPrimaryButton(
                    "Conectar báscula", { onConnectScale(hand) },
                    icon = Icons.Rounded.Bluetooth, subtitle = "(Crane Scale WH-C06)",
                )
            }
            RgSecondaryButton("Introducir manualmente", { showManual = true }, icon = Icons.Rounded.Edit)
        }
    }
    if (showManual) {
        ManualEntryDialog(
            title = "${Labels.exercise(exercise)} · ${Labels.hand(hand)}",
            hand = hand,
            recentZones = recentZones,
            onDismiss = { showManual = false },
            onSave = { kg, note, discomforts, at ->
                showManual = false
                onManualSave(hand, kg, note, discomforts, at)
            },
        )
    }
}

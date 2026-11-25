package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.progress.ProgressSummary
import kotlin.math.abs

/** Row data: injured as % of healthy now and its change since the start. */
data class SymmetryRow(val exercise: Exercise, val percent: Double?, val trendPoints: Double?, val summary: ProgressSummary)

fun symmetryRows(all: List<Measurement>, injury: Injury, range: ProgressRange, nowMillis: Long): List<SymmetryRow> {
    val calc = ProgressCalculator()
    return Exercise.entries.map { e ->
        val s = calc.summarize(all, e, range, nowMillis)
        SymmetryRow(e, s.symmetryPercent(injury.focusHand), s.symmetryTrend(injury.focusHand), s)
    }
}

/** Main card: "Lesionada respecto a sana" per exercise, with a 0-100 % bar and trend. */
@Composable
fun SymmetryOverview(
    measurements: List<Measurement>,
    injury: Injury,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    onExercise: ((Exercise) -> Unit)? = null,
) {
    val rows = remember(measurements, injury) { symmetryRows(measurements, injury, ProgressRange.ALL, nowMillis) }
    RgCard(modifier.fillMaxWidth()) {
        Text(InjuryLabels.comparisonTitle(injury), style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        Text(
            if (injury.hand == null) "100 % = las dos manos igual de fuertes"
            else "100 % = tu ${Labels.hand(injury.hand!!).lowercase()} ya iguala a la ${Labels.hand(injury.hand!!.other).lowercase()}",
            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().let { m -> onExercise?.let { m.clickableNoRipple { it(r.exercise) } } ?: m }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Labels.exercise(r.exercise), style = MaterialTheme.typography.bodyMedium, color = RgColors.Navy, modifier = Modifier.width(118.dp), maxLines = 1)
                SymmetryBar(r.percent, Modifier.weight(1f).height(12.dp))
                Spacer(Modifier.width(10.dp))
                Text(r.percent?.let { formatPercent(it, false) } ?: "—", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.width(52.dp))
                TrendBadge(r.trendPoints)
            }
        }
    }
}

@Composable
fun SymmetryBar(percent: Double?, modifier: Modifier) {
    val f = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val color = symmetryColor(percent)
    Box(modifier.clip(CircleShape).background(RgColors.SurfaceTint)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(color))
    }
}

fun symmetryColor(percent: Double?): Color = when {
    percent == null -> RgColors.Outline
    percent >= 90 -> RgColors.Success
    percent >= 70 -> RgColors.Primary
    else -> RgColors.Warning
}

/** "+12 pts" in green when closing the gap, red when it opens. */
@Composable
fun TrendBadge(points: Double?, modifier: Modifier = Modifier) {
    val (icon, color) = when {
        points == null || abs(points) < 1 -> Icons.AutoMirrored.Rounded.TrendingFlat to RgColors.TextSecondary
        points > 0 -> Icons.AutoMirrored.Rounded.TrendingUp to Color(0xFF1E8A55)
        else -> Icons.AutoMirrored.Rounded.TrendingDown to RgColors.Danger
    }
    Row(modifier.width(70.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            points?.let { (if (it >= 0) "+" else "−") + abs(it).toInt() + " pts" } ?: "",
            style = MaterialTheme.typography.labelSmall, color = color,
        )
    }
}

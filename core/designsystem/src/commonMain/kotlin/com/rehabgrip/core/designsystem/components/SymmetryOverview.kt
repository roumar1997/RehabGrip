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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.progress.ProgressSummary
import kotlin.math.abs

/**
 * Row data: one grip (or the dynamometer) with the injured hand as % of the healthy one,
 * its change since the start and the latest kg of each hand.
 */
data class SymmetryRow(
    val label: String,
    val exercise: Exercise?,
    val grip: GripSpec?,
    val percent: Double?,
    val trendPoints: Double?,
    val rightKg: Double?,
    val leftKg: Double?,
    val summary: ProgressSummary,
)

/** One row per library grip plus the dynamometer; without a library, one per exercise. */
fun symmetryRows(
    all: List<Measurement>,
    injury: Injury,
    range: ProgressRange,
    nowMillis: Long,
    grips: List<GripSpec> = emptyList(),
): List<SymmetryRow> {
    val calc = ProgressCalculator()
    fun row(label: String, exercise: Exercise?, grip: GripSpec?, s: ProgressSummary) = SymmetryRow(
        label, exercise, grip, s.symmetryPercent(injury.focusHand), s.symmetryTrend(injury.focusHand),
        s.right.lastKg, s.left.lastKg, s,
    )
    val fromGrips = grips.map { g -> row(g.label, g.builtIn, g, calc.summarizeGrip(all, g, range, nowMillis, grips)) }
    val others = (if (grips.isEmpty()) Exercise.selectable else listOf(Exercise.DYNAMOMETER))
        .map { e -> row(Labels.exercise(e), e, null, calc.summarize(all, e, range, nowMillis)) }
    return fromGrips + others
}

/** Main card: «Lesionada respecto a sana» per grip, with a 0-100 % bar, trend and the kg of each hand. */
@Composable
fun SymmetryOverview(
    measurements: List<Measurement>,
    injury: Injury,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    grips: List<GripSpec> = emptyList(),
    collapsible: Boolean = false,
    onRow: ((SymmetryRow) -> Unit)? = null,
) {
    val allRows = remember(measurements, injury, grips) { symmetryRows(measurements, injury, ProgressRange.ALL, nowMillis, grips) }
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var showAll by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val rows = if (collapsible && !showAll) allRows.filter { it.percent != null || it.rightKg != null || it.leftKg != null } else allRows
    RgCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().let { if (collapsible) it.clickableNoRipple { open = !open } else it }, verticalAlignment = Alignment.CenterVertically) {
            Text(InjuryLabels.comparisonTitle(injury), style = MaterialTheme.typography.titleMedium, color = RgColors.Navy, modifier = Modifier.weight(1f))
            if (collapsible) Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = RgColors.TextSecondary)
        }
        if (collapsible && !open) return@RgCard
        Text(
            percentMeaning(injury),
            style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        rows.forEach { r ->
            Column(
                Modifier.fillMaxWidth().let { m -> onRow?.let { m.clickableNoRipple { it(r) } } ?: m }.padding(vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(r.percent?.let { formatPercent(it, false) } ?: "—", style = MaterialTheme.typography.titleMedium, color = symmetryColor(r.percent))
                    TrendBadge(r.trendPoints)
                }
                SymmetryBar(r.percent, Modifier.fillMaxWidth().height(10.dp))
                Text(
                    "${Labels.hand(Hand.RIGHT)} ${kgText(r.rightKg)} · ${Labels.hand(Hand.LEFT)} ${kgText(r.leftKg)}",
                    style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (collapsible && !showAll && allRows.size > rows.size) {
            androidx.compose.material3.TextButton(onClick = { showAll = true }) { Text("Ver todos los agarres") }
        } else if (collapsible && showAll) {
            androidx.compose.material3.TextButton(onClick = { showAll = false }) { Text("Solo los que tienen datos") }
        }
    }
}

private fun kgText(kg: Double?) = kg?.let { "${formatKg(it)} kg" } ?: "—"

/** What the % means, in plain words. */
fun percentMeaning(injury: Injury): String {
    val hurt = injury.hand
    return if (hurt == null) "% = fuerza de la mano derecha respecto a la izquierda. 100 % = igual de fuertes."
    else "% = fuerza de tu mano lesionada (${Labels.hand(hurt).lowercase()}) respecto a la sana (${Labels.hand(hurt.other).lowercase()}). 100 % = igual de fuertes."
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

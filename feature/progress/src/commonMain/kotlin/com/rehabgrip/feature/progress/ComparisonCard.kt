package com.rehabgrip.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.ChartSeries
import com.rehabgrip.core.designsystem.chart.LineChart
import com.rehabgrip.core.designsystem.components.InjuryLabels
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.SymmetryBar
import com.rehabgrip.core.designsystem.components.TrendBadge
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.components.symmetryColor
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.progress.DayPoint
import com.rehabgrip.core.domain.progress.ProgressSummary

/** One exercise: best of each hand, symmetry now, its trend and % over time. */
@Composable
internal fun ComparisonCard(summary: ProgressSummary, injury: Injury) {
    val focus = injury.focusHand
    val hurt = summary.of(focus)
    val healthy = summary.of(focus.other)
    val now = summary.symmetryPercent(focus)
    RgCard(Modifier.fillMaxWidth()) {
        Text(Labels.exercise(summary.exercise), style = MaterialTheme.typography.titleLarge, color = RgColors.Navy)
        Text(InjuryLabels.comparisonTitle(injury), style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                now?.let { formatPercent(it, false) } ?: "—",
                style = MaterialTheme.typography.displayMedium, color = symmetryColor(now),
            )
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                SymmetryBar(now, Modifier.fillMaxWidth().height(14.dp))
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Desde el inicio", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
                    TrendBadge(summary.symmetryTrend(focus))
                }
            }
        }
        HorizontalDivider(color = RgColors.Outline)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BestColumn(InjuryLabels.handRole(focus, injury), hurt.bestKg, hurt.lastKg, if (injury.hand != null) RgColors.Danger else RgColors.RightHand)
            BestColumn(InjuryLabels.handRole(focus.other, injury), healthy.bestKg, healthy.lastKg, RgColors.LeftHand)
        }
        val series = symmetrySeries(summary, focus)
        val axis = ChartAxis.fractions(listOf(series, series.map { it.copy(kg = 100.0) }))
        if (axis != null && series.size >= 2) {
            Text("% a lo largo del tiempo (línea gris = 100 %)", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary)
            LineChart(
                listOf(ChartSeries(RgColors.Outline, axis.first[1]), ChartSeries(symmetryColor(now), axis.first[0])),
                axis.second,
                Modifier.fillMaxWidth().height(180.dp),
            )
        }
    }
}

@Composable
private fun BestColumn(title: String, best: Double?, last: Double?, color: Color) = Column {
    Text(title, style = MaterialTheme.typography.labelMedium, color = color)
    Text(best?.let { "${formatKg(it)} kg" } ?: "—", style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy)
    Text("mejor marca · último ${last?.let(::formatKg) ?: "—"}", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
}

/** Days where both hands were measured: injured as % of healthy. */
private fun symmetrySeries(s: ProgressSummary, focus: com.rehabgrip.core.domain.model.Hand): List<DayPoint> {
    val other = s.of(focus.other).points.associateBy { it.date }
    return s.of(focus).points.mapNotNull { p ->
        other[p.date]?.takeIf { it.kg > 0 }?.let { DayPoint(p.date, p.kg / it.kg * 100.0) }
    }
}

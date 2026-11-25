package com.rehabgrip.feature.progress

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.ChartSeries
import com.rehabgrip.core.designsystem.chart.LineChart
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.discomfortColor
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.progress.DayPoint
import com.rehabgrip.core.domain.progress.DiscomfortCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import kotlinx.datetime.LocalDate

/** One small chart per discomfort zone (0-10), newest first. */
@Composable
internal fun DiscomfortView(measurements: List<Measurement>, range: ProgressRange, nowMillis: Long) {
    val series = remember(measurements, range) { DiscomfortCalculator().series(measurements, range, nowMillis) }
    if (series.isEmpty()) {
        RgCard(Modifier.fillMaxWidth()) {
            Text("Sin molestias anotadas en este periodo", style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
        }
        return
    }
    series.forEach { s ->
        RgCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.zone, style = MaterialTheme.typography.titleMedium, color = RgColors.Navy, modifier = Modifier.weight(1f))
                Text(
                    "${s.first ?: "—"} → ${s.last ?: "—"} /10",
                    style = MaterialTheme.typography.titleMedium,
                    color = discomfortColor(s.last ?: 0),
                )
            }
            val withCeiling = s.points + DayPoint(s.points.last().date, 10.0)
            val axis = ChartAxis.fractions(listOf(s.points, withCeiling))
            if (axis != null && s.points.size >= 2) {
                LineChart(
                    listOf(ChartSeries(discomfortColor(s.last ?: 0), axis.first[0])),
                    axis.second,
                    Modifier.fillMaxWidth().height(130.dp),
                )
            } else {
                Text("Solo un registro: ${s.points.first().date.short()}", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
            }
        }
    }
}

private fun LocalDate.short() = "$dayOfMonth/$monthNumber"

package com.rehabgrip.feature.progress

import com.rehabgrip.core.designsystem.components.GripLook
import com.rehabgrip.core.designsystem.components.GripBadges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.ChartSeries
import com.rehabgrip.core.designsystem.chart.LineChart
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.SymmetryBar
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.components.symmetryColor
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.progress.ProgressSummary

/** A grip (or the dynamometer) chosen to be drawn, with its stable colour and letters and its summary. */
internal class Picked(val id: Long, val name: String, val look: GripLook, val summary: ProgressSummary) {
    val color: Color get() = look.color
}

/** Dropdown with check boxes: each grip shows its letters and colour; [notice] explains a refused tick. */
@Composable
internal fun GripMultiPicker(
    options: List<Pair<Long, String>>,
    selected: Set<Long>,
    lookOf: (Long) -> GripLook,
    notice: String?,
    onToggle: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    RgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickableNoRipple { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Agarres · ${selected.size} seleccionados", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = RgColors.TextSecondary)
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RgColors.Warning, modifier = Modifier.padding(top = 6.dp)) }
        if (open) options.forEach { (id, name) ->
            val look = lookOf(id)
            Row(Modifier.fillMaxWidth().clickableNoRipple { onToggle(id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(id in selected, { onToggle(id) }, colors = CheckboxDefaults.colors(checkedColor = look.color))
                GripBadges(look)
                Text(name, style = MaterialTheme.typography.bodyMedium, color = RgColors.Navy, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
            }
        }
    }
}

/** «Comparar»: the % injured/healthy over time per grip with the 100 % line, plus bars with the current %. */
@Composable
internal fun CompareView(picked: List<Picked>, injury: Injury) {
    val focus = injury.focusHand
    RgCard(Modifier.fillMaxWidth()) {
        Text(com.rehabgrip.core.designsystem.components.InjuryLabels.comparisonTitle(injury), style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        val lists = picked.map { symmetrySeries(it.summary, focus) }
        val axis = ChartAxis.fractions(lists)
        if (picked.isEmpty() || axis == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text("Hacen falta registros de las dos manos el mismo día", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val (pts, labels) = axis
            LineChart(picked.mapIndexed { i, p -> ChartSeries(p.color, pts[i]) }, labels, Modifier.fillMaxWidth().height(220.dp), referenceY = 100f)
            Text("Línea de puntos = 100 % (igual de fuertes)", style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary)
            if (hasSingleDay(lists)) OneDayText()
        }
        picked.forEach { p ->
            val pct = p.summary.symmetryPercent(focus)
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GripBadges(p.look)
                    Text(p.name, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 2)
                    Text(pct?.let { formatPercent(it, false) } ?: "—", style = MaterialTheme.typography.titleMedium, color = symmetryColor(pct))
                }
                SymmetryBar(pct, Modifier.fillMaxWidth().height(10.dp))
            }
        }
    }
}

/** True when everything drawn sits on one single day (a line needs two). */
internal fun hasSingleDay(series: List<List<com.rehabgrip.core.domain.progress.DayPoint>>): Boolean =
    series.flatten().map { it.date }.distinct().size == 1

@Composable
internal fun OneDayText() =
    Text("Solo hay un día de datos: registra otro día para ver la línea", style = MaterialTheme.typography.bodySmall, color = RgColors.Warning, modifier = Modifier.padding(top = 6.dp))

internal fun dynamometerName() = Labels.exercise(Exercise.DYNAMOMETER)

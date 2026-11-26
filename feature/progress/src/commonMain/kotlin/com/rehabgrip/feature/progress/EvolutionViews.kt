package com.rehabgrip.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.ChartSeries
import com.rehabgrip.core.designsystem.chart.LineChart
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.SymmetryBar
import com.rehabgrip.core.designsystem.components.TrendBadge
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.components.symmetryColor
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.progress.ProgressSummary

/** A grip (or the dynamometer) chosen to be drawn, with its own colour and its summary. */
internal class Picked(val id: Long, val name: String, val color: Color, val summary: ProgressSummary)

/** Distinct colour per grip; the hand is told apart by tone (right solid, left lighter and dashed). */
internal val GripPalette = listOf(
    Color(0xFF1E6FE0), Color(0xFFF28C28), Color(0xFF2BB673), Color(0xFF7B4FD6),
    Color(0xFFE5484D), Color(0xFF0EA5A5), Color(0xFFD6409F), Color(0xFF8D6E63),
)

internal fun paletteColor(index: Int) = GripPalette[index % GripPalette.size]

/** Dropdown with check boxes: any number of grips can be seen together. */
@Composable
internal fun GripMultiPicker(options: List<Pair<Long, String>>, selected: Set<Long>, colorOf: (Long) -> Color, onToggle: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    RgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickableNoRipple { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Agarres · ${selected.size} seleccionados", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = RgColors.TextSecondary)
        }
        if (open) options.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().clickableNoRipple { onToggle(id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(id in selected, { onToggle(id) }, colors = CheckboxDefaults.colors(checkedColor = colorOf(id)))
                Box(Modifier.size(12.dp).clip(CircleShape).background(colorOf(id)))
                Text(name, style = MaterialTheme.typography.bodyMedium, color = RgColors.Navy, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
            }
        }
    }
}

/** «Por mano»: every picked grip, right hand solid and left hand lighter and dashed, a legend and a table. */
@Composable
internal fun ByHandView(picked: List<Picked>, injury: Injury) {
    RgCard(Modifier.fillMaxWidth()) {
        Text("Evolución por mano", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        if (picked.isEmpty()) {
            Text("Elige al menos un agarre", style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
            return@RgCard
        }
        val lists = picked.flatMap { listOf(it.summary.right.points, it.summary.left.points) }
        val axis = ChartAxis.fractions(lists)
        if (axis == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("Sin registros en este periodo", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val (pts, labels) = axis
            val series = picked.flatMapIndexed { i, p ->
                listOf(
                    ChartSeries(p.color, pts[i * 2]),
                    ChartSeries(p.color.copy(alpha = 0.5f), pts[i * 2 + 1], dashed = true),
                )
            }
            LineChart(series, labels, Modifier.fillMaxWidth().height(230.dp))
        }
        Legend(picked)
        if (picked.isNotEmpty() && hasSingleDay(picked.flatMap { listOf(it.summary.right.points, it.summary.left.points) })) OneDayText()
    }
    picked.forEach { ComparisonCard(it.summary, injury, dotColor = it.color, showSeries = false) }
}

@Composable
private fun Legend(picked: List<Picked>) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        picked.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(22.dp).height(4.dp).background(p.color))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.width(22.dp).height(4.dp).background(p.color.copy(alpha = 0.45f)))
                Text("  ${p.name}", style = MaterialTheme.typography.bodySmall, color = RgColors.Navy, maxLines = 1)
            }
        }
        Text("Línea sólida = derecha · tono suave y discontinua = izquierda", style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary)
    }
}

/** Per grip: best right, best left, % injured/healthy and trend. */
@Composable
private fun SummaryTable(picked: List<Picked>, injury: Injury) {
    if (picked.isEmpty()) return
    val focus = injury.focusHand
    RgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text("Agarre", style = MaterialTheme.typography.labelMedium, color = RgColors.TextSecondary, modifier = Modifier.weight(1.7f))
            listOf("Mejor dcha", "Mejor izda", "% les./sana").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
            }
        }
        picked.forEach { p ->
            val s = p.summary
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1.7f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
                        Text(" ${p.name}", style = MaterialTheme.typography.bodySmall, color = RgColors.Navy, maxLines = 2)
                    }
                    Text(s.right.bestKg?.let { formatKg(it) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, textAlign = TextAlign.End)
                    Text(s.left.bestKg?.let { formatKg(it) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, textAlign = TextAlign.End)
                    val pct = s.symmetryPercent(focus)
                    Text(pct?.let { formatPercent(it, false) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = symmetryColor(pct), textAlign = TextAlign.End)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TrendBadge(s.symmetryTrend(focus)) }
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
                    Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
                    Text(" ${p.name}", style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.weight(1f), maxLines = 1)
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

@Composable
private fun OneDayNotice(picked: List<Picked>) = Unit

/** Compare: the two hands of ONE grip (right solid and strong, left soft and dashed); chips pick the grip when several are chosen. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun TwoHandsMini(picked: List<Picked>) {
    if (picked.isEmpty()) return
    var chosenId by remember { mutableStateOf(picked.first().id) }
    val p = picked.firstOrNull { it.id == chosenId } ?: picked.first()
    RgCard(Modifier.fillMaxWidth()) {
        Text("Las dos manos · kilos", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        if (picked.size > 1) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                picked.forEach { q -> com.rehabgrip.core.designsystem.components.GripChip(q.name, q.id == p.id) { chosenId = q.id } }
            }
        } else {
            Text(p.name, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
        }
        val axis = ChartAxis.fractions(listOf(p.summary.right.points, p.summary.left.points))
        if (axis == null) {
            Text("Sin registros en este periodo", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            LineChart(
                listOf(ChartSeries(p.color, axis.first[0]), ChartSeries(p.color.copy(alpha = 0.5f), axis.first[1], dashed = true)),
                axis.second, Modifier.fillMaxWidth().height(170.dp),
            )
            Text("Derecha sólida · izquierda suave y discontinua", style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary)
            if (hasSingleDay(listOf(p.summary.right.points, p.summary.left.points))) OneDayText()
        }
    }
}

internal fun dynamometerName() = Labels.exercise(Exercise.DYNAMOMETER)

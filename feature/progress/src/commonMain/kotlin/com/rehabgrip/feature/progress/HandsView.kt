package com.rehabgrip.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.AxisTickUi
import com.rehabgrip.core.designsystem.chart.GripChart
import com.rehabgrip.core.designsystem.chart.GripLineUi
import com.rehabgrip.core.designsystem.chart.GripPointUi
import com.rehabgrip.core.designsystem.components.GripChip
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.progress.ChartMode
import com.rehabgrip.core.domain.progress.DayAxis
import kotlinx.datetime.LocalDate

private fun LocalDate.short() = "$dayOfMonth/$monthNumber"

/** The two hand lines of [p] for the chart; [index] of [count] drives the dodge. */
internal fun handLines(p: Picked, axis: DayAxis, index: Int, count: Int): List<GripLineUi> =
    listOf(Hand.RIGHT, Hand.LEFT).mapNotNull { hand ->
        val pts = p.summary.of(hand).points
        if (pts.isEmpty()) null
        else GripLineUi(
            code = p.look.code, gripName = p.name, handName = Labels.hand(hand), right = hand == Hand.RIGHT,
            base = p.look.color, tint = p.look.tint, dark = p.look.dark,
            points = pts.map { GripPointUi(axis.fraction(it.date), it.kg.toFloat(), it.date.short()) },
            gripIndex = index, gripCount = count,
        )
    }

internal fun axisOf(picked: List<Picked>, nowMillis: Long): DayAxis? =
    DayAxis.build(picked.flatMap { p -> p.summary.right.points + p.summary.left.points }.map { it.date }, nowMillis)

private fun ticksOf(axis: DayAxis) = axis.ticks().map { (fx, d) -> AxisTickUi(fx, d.short()) }

/** «Semiarqueo · Izquierda · 38,0 kg · 25/11» */
@Composable
private fun PointDialog(line: GripLineUi, point: GripPointUi, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = RgColors.Background,
    text = { Text("${line.gripName} · ${line.handName} · ${formatKg(point.kg.toDouble())} kg · ${point.dateLabel}", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
)

/** «Por mano»: lettered markers per grip and hand, overlaid in one chart or one chart per grip. */
@Composable
internal fun ByHandView(picked: List<Picked>, injury: Injury, mode: ChartMode, onMode: (ChartMode) -> Unit, nowMillis: Long) {
    var tapped by remember { mutableStateOf<Pair<GripLineUi, GripPointUi>?>(null) }
    val shown = if (mode == ChartMode.OVERLAID) picked.take(com.rehabgrip.core.domain.progress.ChartModeRules.MAX_OVERLAID) else picked
    val axis = axisOf(shown, nowMillis)
    RgCard(Modifier.fillMaxWidth()) {
        Text("Evolución por mano", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        RgSegmented(
            ChartMode.entries, mode, { if (it == ChartMode.OVERLAID) "Superpuestas" else "Separadas" }, onMode,
            Modifier.padding(vertical = 8.dp), colorFor = { RgColors.Navy },
        )
        if (shown.isEmpty()) {
            Text("Elige al menos un agarre", style = MaterialTheme.typography.bodyMedium, color = RgColors.TextSecondary)
        } else if (axis == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("Sin registros en este periodo", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (mode == ChartMode.OVERLAID) {
            GripChart(
                shown.flatMapIndexed { i, p -> handLines(p, axis, i, shown.size) }, ticksOf(axis),
                Modifier.fillMaxWidth().height(260.dp), onPointTap = { l, p -> tapped = l to p },
            )
            Legend(shown)
        }
        if (axis != null && shown.isNotEmpty()) {
            Text(
                "Círculo lleno y línea continua = derecha · círculo claro y línea discontinua = izquierda",
                style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary, modifier = Modifier.padding(top = 6.dp),
            )
            if (hasSingleDay(shown.flatMap { listOf(it.summary.right.points, it.summary.left.points) })) OneDayText()
        }
    }
    if (mode == ChartMode.SEPARATE && axis != null) shown.forEach { p ->
        RgCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GripBadges(p.look)
                Text(p.name, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
            }
            val lines = handLines(p, axis, 0, 1)
            if (lines.isEmpty()) Text("Sin registros en este periodo", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            else GripChart(lines, ticksOf(axis), Modifier.fillMaxWidth().height(210.dp), onPointTap = { l, pt -> tapped = l to pt })
        }
    }
    shown.forEach { ComparisonCard(it.summary, injury, look = it.look, showSeries = false) }
    tapped?.let { (l, p) -> PointDialog(l, p) { tapped = null } }
}

@Composable
private fun Legend(picked: List<Picked>) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        picked.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                GripBadges(p.look)
                Text(p.name, style = MaterialTheme.typography.bodySmall, color = RgColors.Navy, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
            }
        }
    }
}

/** Compare: the two hands of ONE grip with the same lettered markers; chips pick the grip when several are chosen. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TwoHandsMini(picked: List<Picked>, nowMillis: Long) {
    if (picked.isEmpty()) return
    var chosenId by remember { mutableStateOf(picked.first().id) }
    var tapped by remember { mutableStateOf<Pair<GripLineUi, GripPointUi>?>(null) }
    val p = picked.firstOrNull { it.id == chosenId } ?: picked.first()
    RgCard(Modifier.fillMaxWidth()) {
        Text("Las dos manos · kilos", style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
        if (picked.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                picked.forEach { q -> GripChip("${q.look.code} · ${q.name}", q.id == p.id) { chosenId = q.id } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GripBadges(p.look)
            Text(p.name, style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary, modifier = Modifier.padding(start = 8.dp))
        }
        val axis = axisOf(listOf(p), nowMillis)
        if (axis == null) {
            Text("Sin registros en este periodo", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            GripChart(handLines(p, axis, 0, 1), ticksOf(axis), Modifier.fillMaxWidth().height(200.dp), onPointTap = { l, pt -> tapped = l to pt })
            Text("Círculo lleno = derecha · círculo claro y discontinua = izquierda", style = MaterialTheme.typography.labelSmall, color = RgColors.TextSecondary)
            if (hasSingleDay(listOf(p.summary.right.points, p.summary.left.points))) OneDayText()
        }
    }
    tapped?.let { (l, pt) -> PointDialog(l, pt) { tapped = null } }
}

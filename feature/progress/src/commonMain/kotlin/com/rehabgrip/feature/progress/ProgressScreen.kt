package com.rehabgrip.feature.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.chart.ChartSeries
import com.rehabgrip.core.designsystem.chart.LineChart
import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.designsystem.components.RgCard
import com.rehabgrip.core.designsystem.components.RgSegmented
import com.rehabgrip.core.designsystem.components.RgTopBar
import com.rehabgrip.core.designsystem.components.clickableNoRipple
import com.rehabgrip.core.designsystem.components.formatKg
import com.rehabgrip.core.designsystem.components.formatPercent
import com.rehabgrip.core.designsystem.illustrations.GripIllustrations
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.progress.HandProgress
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.core.domain.progress.ProgressSummary
import com.rehabgrip.core.domain.progress.DayPoint
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.grip.GripStyles
import com.rehabgrip.core.domain.progress.ChartMode
import com.rehabgrip.core.domain.progress.ChartModeRules
import com.rehabgrip.core.designsystem.components.InjuryLabels
import com.rehabgrip.core.designsystem.components.SymmetryOverview
import kotlinx.datetime.LocalDate

/** "Evolución": right vs left over the chosen range, adapted to the days trained. */
@Composable
fun ProgressScreen(
    measurements: List<Measurement>,
    nowMillis: Long,
    injury: Injury = Injury.NONE,
    grips: List<GripSpec> = emptyList(),
) {
    // Grips shown together, kept as "1,2,-1" so it survives rotation; empty = the first ones with data.
    var selectedText by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(ProgressRange.ALL) }
    var view by rememberSaveable { mutableStateOf(ProgressView.HANDS) }
    val calculator = remember { ProgressCalculator() }
    val options = remember(grips) { grips.map { it.id to it.label } + (DYNAMOMETER_ID to dynamometerName()) }
    val styles = remember(grips) { GripStyles.resolve(grips) }
    val lookOf = { id: Long -> GripLook.of(styles[id] ?: GripStyles.dynamometer()) }
    var modeText by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val selected: Set<Long> = remember(selectedText, measurements, grips) {
        val chosen = selectedText.split(',').mapNotNull { it.toLongOrNull() }.filter { id -> options.any { it.first == id } }.toSet()
        if (selectedText.isNotEmpty()) chosen
        else {
            val withData = grips.filter { g -> measurements.any { m -> g.matches(m, grips) } }.map { it.id }
            (withData.ifEmpty { grips.map { it.id } }).take(2).toSet()
        }
    }
    val picked = remember(measurements, selected, range, nowMillis, grips) {
        options.filter { it.first in selected }.map { (id, name) ->
            val summary = if (id == DYNAMOMETER_ID) calculator.summarize(measurements, Exercise.DYNAMOMETER, range, nowMillis)
            else calculator.summarizeGrip(measurements, grips.first { it.id == id }, range, nowMillis, grips)
            Picked(id, name, lookOf(id), summary)
        }
    }
    val mode = when (modeText) { "O" -> ChartMode.OVERLAID; "S" -> ChartMode.SEPARATE; else -> ChartModeRules.default(selected.size) }
    Column(Modifier.fillMaxSize()) {
        RgTopBar("Evolución", leadingIcon = Icons.Rounded.BarChart)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RgSegmented(ProgressView.entries, view, { it.label }, { view = it }, colorFor = { RgColors.Navy })
            RgSegmented(
                ProgressRange.entries, range,
                label = { when (it) { ProgressRange.WEEK -> "7d"; ProgressRange.FOUR_WEEKS -> "4s"; ProgressRange.TWELVE_WEEKS -> "12s"; ProgressRange.ALL -> "Todo" } },
                onSelect = { range = it },
            )
            if (view != ProgressView.DISCOMFORT) {
                GripMultiPicker(options, selected, lookOf, notice) { id ->
                    if (id !in selected && !ChartModeRules.canAdd(mode, selected.size)) {
                        notice = ChartModeRules.OVERLAID_FULL
                    } else {
                        notice = null
                        val next = if (id in selected) selected - id else selected + id
                        selectedText = next.joinToString(",").ifEmpty { "x" }
                    }
                }
            }
            when (view) {
                ProgressView.HANDS -> ByHandView(picked, injury, mode, { m ->
                    if (ChartModeRules.canSwitchTo(m, selected.size)) { notice = null; modeText = if (m == ChartMode.OVERLAID) "O" else "S" }
                    else notice = ChartModeRules.OVERLAID_FULL
                }, nowMillis)
                ProgressView.COMPARE -> {
                    CompareView(picked, injury)
                    TwoHandsMini(picked, nowMillis)
                    picked.forEach { ComparisonCard(it.summary, injury, look = it.look) }
                }
                ProgressView.DISCOMFORT -> DiscomfortView(measurements, range, nowMillis)
            }
        }
    }
}

private enum class ProgressView(val label: String) { HANDS("Por mano"), COMPARE("Comparar"), DISCOMFORT("Molestias") }

/** Id used by the picker for the dynamometer (not a library grip). */
private const val DYNAMOMETER_ID = -1L

/** Selector of the grip whose evolution is shown: each grip of the library, plus the dynamometer. */
@Composable
internal fun GripPicker(grips: List<GripSpec>, selected: GripSpec?, dynamometer: Boolean, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (grips.map { it.id to it.label } + (DYNAMOMETER_ID to Labels.exercise(Exercise.DYNAMOMETER))).forEach { (id, name) ->
            val sel = if (id == DYNAMOMETER_ID) dynamometer else !dynamometer && id == selected?.id
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (sel) Color.White else RgColors.Navy,
                modifier = Modifier.clip(CircleShape)
                    .background(if (sel) RgColors.Navy else RgColors.Surface)
                    .clickableNoRipple { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChartCard(summary: ProgressSummary, range: ProgressRange, nowMillis: Long, grip: GripSpec?) {
    RgCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                (grip?.let(GripIllustrations::of) ?: GripIllustrations.of(summary.exercise)), null,
                Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.title ?: Labels.exercise(summary.exercise), style = MaterialTheme.typography.titleMedium, color = RgColors.Navy)
                Text(
                    if (summary.spanDays == 0) "Sin registros en este periodo" else "Últimos ${summary.spanDays} días entrenados",
                    style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary,
                )
            }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Derecha", RgColors.RightHand)
            Legend("Izquierda", RgColors.LeftHand)
        }
        val axis = ChartAxis.build(summary, range, nowMillis)
        if (axis == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("Registra tu primer intento para ver la gráfica", color = RgColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LineChart(
                listOf(ChartSeries(RgColors.LeftHand, axis.left), ChartSeries(RgColors.RightHand, axis.right)),
                axis.labels,
                Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = RgColors.Navy)
}

@Composable
private fun HandCard(p: HandProgress, modifier: Modifier, injury: Injury) {
    val color = if (p.hand == Hand.RIGHT) RgColors.RightHand else RgColors.LeftHand
    RgCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(InjuryLabels.handRole(p.hand, injury), style = MaterialTheme.typography.labelMedium, color = if (p.hand == injury.hand) RgColors.Danger else RgColors.TextSecondary)
        }
        Text(p.lastKg?.let { "${formatKg(it)} kg" } ?: "—", style = MaterialTheme.typography.headlineSmall, color = RgColors.Navy)
        Text("Mejor: ${p.bestKg?.let { formatKg(it) + " kg" } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
        val imp = p.improvementPercent
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Rounded.KeyboardDoubleArrowUp, null, tint = if ((imp ?: 0.0) >= 0) RgColors.Success else RgColors.Danger, modifier = Modifier.size(20.dp))
            Text(
                imp?.let(::formatPercent) ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = if ((imp ?: 0.0) >= 0) Color(0xFF1E8A55) else RgColors.Danger,
            )
        }
        Text("desde el inicio", style = MaterialTheme.typography.bodySmall, color = RgColors.TextSecondary)
    }
}

/** Maps day points to chart x fractions between the first day shown and today. */
internal class ChartAxis(val right: List<Pair<Float, Float>>, val left: List<Pair<Float, Float>>, val labels: List<String>) {
    companion object {
        fun build(s: ProgressSummary, range: ProgressRange, nowMillis: Long): ChartAxis? {
            val days = (s.right.points + s.left.points).map { it.date }
            if (days.isEmpty()) return null
            val first = days.min()
            val last = days.max()
            val span = (last.toEpochDays() - first.toEpochDays()).coerceAtLeast(1)
            fun pts(p: HandProgress) = p.points.map { ((it.date.toEpochDays() - first.toEpochDays()).toFloat() / span) to it.kg.toFloat() }
            val labels = tickLabels(first, last.toEpochDays() - first.toEpochDays())
            return ChartAxis(pts(s.right), pts(s.left), labels)
        }

        /**
         * Generic: x fraction from the first to the last date among [series]. Axis labels are unique and
         * readable: as many distinct days as fit (at most 4); a single day gives one label and a centred point.
         */
        fun fractions(series: List<List<DayPoint>>): Pair<List<List<Pair<Float, Float>>>, List<String>>? {
            val days = series.flatten().map { it.date }
            if (days.isEmpty()) return null
            val first = days.min()
            val span = days.max().toEpochDays() - first.toEpochDays()
            val pts = series.map { s -> s.map { (if (span == 0) 0.5f else (it.date.toEpochDays() - first.toEpochDays()).toFloat() / span) to it.kg.toFloat() } }
            return pts to tickLabels(first, span)
        }

        /** Distinct labels: min(4, days) ticks evenly spread over the span. */
        fun tickLabels(first: LocalDate, span: Int): List<String> =
            com.rehabgrip.core.domain.progress.AxisTicks.days(first, span).map { it.short() }

        private fun LocalDate.short() = "${dayOfMonth}/${monthNumber}"
    }
}

package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.RecordDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** «dd/MM/yyyy» of an epoch in the local time zone. */
fun formatRecordDay(epochMillis: Long): String {
    val d = RecordDate.day(epochMillis)
    return "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}/${d.year}"
}

/**
 * «Fecha» of a record: Hoy / Ayer / another day (calendar, never in the future). [at] null = now.
 * The time of day is the current one. [onChange] gets null again when «Hoy» is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RgDateField(at: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, label: String = "Fecha") {
    val now = Clock.System.now().toEpochMilliseconds()
    val yesterday = RecordDate.yesterday(now)
    val today = RecordDate.day(now)
    val chosen = at?.let(RecordDate::day)
    val isToday = chosen == null || chosen == today
    val isYesterday = chosen == RecordDate.day(yesterday)
    var picking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = RgColors.Navy)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GripChip("Hoy", isToday) { onChange(null) }
            GripChip("Ayer", isYesterday) { onChange(yesterday) }
            GripChip(if (!isToday && !isYesterday && at != null) formatRecordDay(at) else "Otra fecha…", !isToday && !isYesterday) { picking = true }
        }
    }
    if (picking) {
        val todayUtc = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (chosen ?: today).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(RecordDate.onPickedDay(it, now)) }
                    picking = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

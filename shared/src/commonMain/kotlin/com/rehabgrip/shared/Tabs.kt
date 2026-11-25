package com.rehabgrip.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.UserProfile
import com.rehabgrip.core.domain.progress.ProgressCalculator
import com.rehabgrip.core.domain.progress.ProgressRange
import com.rehabgrip.feature.history.HistoryScreen
import com.rehabgrip.feature.profile.ProfileScreen
import com.rehabgrip.feature.profile.ProfileUiState
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun HistoryTab(container: AppContainer, measurements: List<Measurement>) {
    val scope = rememberCoroutineScope()
    HistoryScreen(
        measurements = measurements,
        onExport = container::export,
        onDelete = { id -> scope.launch { container.measurements.delete(id) } },
    )
}

@Composable
internal fun ProfileTab(container: AppContainer, profile: UserProfile, measurements: List<Measurement>, simulated: Boolean) {
    val scope = rememberCoroutineScope()
    val summary = remember(measurements) {
        ProgressCalculator().summarize(measurements, Exercise.HALF_CRIMP, ProgressRange.ALL, container.now())
    }
    ProfileScreen(
        state = ProfileUiState(
            name = profile.name,
            rightImprovementPercent = summary.right.improvementPercent,
            rightVsLeftPercent = summary.rightVsLeftPercent,
            simulatedScale = simulated,
            showDeveloperOptions = container.platform.isDebug,
        ),
        onRename = { scope.launch { container.settings.save(UserProfile(it)) } },
        onSimulatedScale = { scope.launch { container.settings.setFlag(SIM_KEY, it) } },
        onLoadSampleData = { scope.launch { container.loadSampleData() } },
    )
}

internal fun formatDateTime(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    fun Int.p() = toString().padStart(2, '0')
    return "${dt.dayOfMonth.p()}/${dt.monthNumber.p()}/${dt.year} · ${dt.hour.p()}:${dt.minute.p()}"
}

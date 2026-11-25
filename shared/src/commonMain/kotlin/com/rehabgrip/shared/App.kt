package com.rehabgrip.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.rehabgrip.core.designsystem.theme.RehabGripTheme
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.model.UserProfile
import com.rehabgrip.feature.exercises.ExercisesScreen
import com.rehabgrip.feature.exercises.TrainingScreen
import com.rehabgrip.feature.measure.LiveMeasurementPresenter
import com.rehabgrip.feature.measure.LiveMeasurementScreen
import com.rehabgrip.feature.measure.SaveSessionScreen
import com.rehabgrip.feature.measure.SavedScreen
import com.rehabgrip.feature.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private object Loading

/** Root composable shared by Android and iOS. */
@Composable
fun App(container: AppContainer, navigator: Navigator) {
    RehabGripTheme {
        val profileFlow = remember { container.settings.observe().map<UserProfile?, Any?> { it } }
        val profile by profileFlow.collectAsState(Loading)
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(RgColors.Background)) {
            when (val p = profile) {
                Loading -> Unit
                null -> OnboardingScreen { name -> scope.launch { container.settings.save(UserProfile(name)) } }
                is UserProfile -> MainContent(container, navigator, p)
            }
        }
    }
}

@Composable
private fun MainContent(container: AppContainer, navigator: Navigator, profile: UserProfile) {
    val measurements by remember { container.measurements.observeAll() }.collectAsState(emptyList())
    val simulated by remember { container.settings.observeFlag(SIM_KEY, container.platform.isDebug) }
        .collectAsState(container.platform.isDebug)
    val scope = rememberCoroutineScope()

    fun saveAndConfirm(route: Route.Live, kg: Double, note: String, source: MeasurementSource) = scope.launch {
        val at = container.now()
        container.saveMeasurement(route.exercise, route.hand, kg, source, at, note)
            .onSuccess { navigator.resetTo(Route.Saved(route.exercise, route.hand, kg, note, at)) }
    }

    when (val route = navigator.current) {
        is Route.Tabs -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                TabContent(route.tab, container, navigator, profile, measurements, simulated)
            }
            BottomBar(route.tab, navigator::selectTab)
        }
        is Route.Training -> TrainingScreen(
            exercise = route.exercise,
            history = measurements,
            onBack = { navigator.pop() },
            onConnectScale = { hand ->
                scope.launch {
                    if (simulated || container.platform.ensureBluetoothPermission()) {
                        navigator.push(Route.Live(route.exercise, hand))
                    }
                }
            },
            onManualSave = { hand, kg, note ->
                saveAndConfirm(Route.Live(route.exercise, hand), kg, note, MeasurementSource.MANUAL)
            },
        )
        is Route.Live -> LiveRoute(route, container, navigator, simulated)
        is Route.SaveSession -> SaveSessionScreen(
            exercise = route.exercise,
            hand = route.hand,
            attempts = route.attempts,
            sessionPeakKg = route.peakKg,
            onBack = { navigator.pop() },
            onSave = { kg, note -> saveAndConfirm(Route.Live(route.exercise, route.hand), kg, note, MeasurementSource.SCALE) },
        )
        is Route.Saved -> SavedScreen(
            exercise = route.exercise,
            hand = route.hand,
            kg = route.kg,
            note = route.note,
            dateText = formatDateTime(route.epochMillis),
            onAccept = { navigator.selectTab(Tab.HOME) },
        )
    }
}

@Composable
private fun LiveRoute(route: Route.Live, container: AppContainer, navigator: Navigator, simulated: Boolean) {
    val scope = rememberCoroutineScope()
    val presenter = remember(route) { LiveMeasurementPresenter(container.forceSource(simulated), scope) }
    DisposableEffect(presenter) {
        presenter.start()
        onDispose { presenter.dispose() }
    }
    val state by presenter.state.collectAsState()
    LiveMeasurementScreen(
        exercise = route.exercise,
        hand = route.hand,
        state = state,
        onBack = { navigator.pop() },
        onToggleRecording = presenter::toggleRecording,
        onFinish = {
            navigator.push(Route.SaveSession(route.exercise, route.hand, state.attempts, state.peak?.kg ?: 0.0))
        },
    )
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = RgColors.Surface) {
        Tab.entries.forEach { tab ->
            val (label, icon) = when (tab) {
                Tab.HOME -> "Inicio" to Icons.Rounded.Home
                Tab.PROGRESS -> "Evolución" to Icons.Rounded.BarChart
                Tab.HISTORY -> "Historial" to Icons.Rounded.History
                Tab.PROFILE -> "Perfil" to Icons.Rounded.Person
            }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RgColors.Primary,
                    selectedTextColor = RgColors.Primary,
                    indicatorColor = RgColors.SurfaceTint,
                    unselectedIconColor = RgColors.TextSecondary,
                    unselectedTextColor = RgColors.TextSecondary,
                ),
            )
        }
    }
}

internal const val SIM_KEY = com.rehabgrip.core.data.SqlSettingsStore.KEY_SIMULATED_SCALE

@Composable
private fun TabContent(
    tab: Tab,
    container: AppContainer,
    navigator: Navigator,
    profile: UserProfile,
    measurements: List<com.rehabgrip.core.domain.model.Measurement>,
    simulated: Boolean,
) {
    when (tab) {
        Tab.HOME -> ExercisesScreen(profile.name, measurements) { navigator.push(Route.Training(it)) }
        Tab.PROGRESS -> com.rehabgrip.feature.progress.ProgressScreen(measurements, container.now())
        Tab.HISTORY -> HistoryTab(container, measurements)
        Tab.PROFILE -> ProfileTab(container, profile, measurements, simulated)
    }
}

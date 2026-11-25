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
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.rehabgrip.core.designsystem.theme.RehabGripTheme
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.UserProfile
import com.rehabgrip.feature.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private object Loading

private data class Gate(val profile: UserProfile?, val injury: Injury?)

/** Root composable shared by Android and iOS. */
@Composable
fun App(container: AppContainer, navigator: Navigator) {
    RehabGripTheme {
        val gateFlow = remember {
            combine(container.settings.observe(), container.settings.observeInjuryOrNull()) { p, i -> Gate(p, i) }
        }
        val gate by gateFlow.collectAsState(Loading)
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            container.seedExampleWorkout()
            container.platform.reminderScheduler.reschedule(container.reminders.all())
        }
        Box(Modifier.fillMaxSize().background(RgColors.Background)) {
            val g = gate as? Gate ?: return@Box
            when {
                g.profile == null -> OnboardingScreen { name, injury ->
                    scope.launch {
                        container.settings.saveInjury(injury)
                        container.settings.save(UserProfile(name))
                    }
                }
                g.injury == null -> InjuryQuestionScreen { injury -> scope.launch { container.settings.saveInjury(injury) } }
                else -> MainContent(container, navigator, AppSession(g.profile, g.injury))
            }
        }
    }
}

/** Data every screen may need, collected once. */
internal data class AppSession(val profile: UserProfile, val injury: Injury)

@Composable
private fun MainContent(container: AppContainer, navigator: Navigator, session: AppSession) {
    val data = rememberAppData(container)
    when (val route = navigator.current) {
        is Route.Tabs -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { TabContent(route.tab, container, navigator, session, data) }
            BottomBar(route.tab, navigator::selectTab)
        }
        is Route.Training, is Route.Live, is Route.SaveSession, is Route.Saved, is Route.Meter, is Route.Diagnostics ->
            MeasureRoutes(route, container, navigator, session, data)
        is Route.GripLibrary, is Route.GripEvaluate -> GripRoutes(route, container, navigator, session, data)
        is Route.Reminders, is Route.WorkoutEditor, is Route.WorkoutRun, is Route.WorkoutSummary ->
            WorkoutAndReminderRoutes(route, container, navigator, session, data)
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = RgColors.Surface) {
        Tab.entries.forEach { tab ->
            val (label, icon) = when (tab) {
                Tab.HOME -> "Inicio" to Icons.Rounded.Home
                Tab.PROGRESS -> "Evolución" to Icons.Rounded.BarChart
                Tab.WORKOUTS -> "Entrenos" to Icons.Rounded.Timer
                Tab.HISTORY -> "Historial" to Icons.Rounded.History
                Tab.PROFILE -> "Perfil" to Icons.Rounded.Person
            }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, null) },
                label = { Text(label, maxLines = 1) },
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

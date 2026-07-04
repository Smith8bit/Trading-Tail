package com.tradingtail

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.tradingtail.ui.analytics.AnalyticsScreen
import com.tradingtail.ui.analytics.AnalyticsViewModel
import com.tradingtail.ui.calendar.CalendarScreen
import com.tradingtail.ui.calendar.CalendarViewModel
import com.tradingtail.ui.journal.JournalScreen
import com.tradingtail.ui.journal.JournalViewModel
import com.tradingtail.ui.theme.TradingTailTheme
import com.tradingtail.ui.tradeentry.QuickTradeEntryScreen
import com.tradingtail.ui.tradeentry.QuickTradeEntryViewModel
import kotlinx.coroutines.launch

private enum class Screen(val label: String, val icon: ImageVector) {
    Journal("Journal", Icons.AutoMirrored.Filled.List),
    Calendar("Calendar", Icons.Filled.DateRange),
    Analytics("Analytics", BarChartIcon),
}

/**
 * ponytail: state-based nav — a `when(screen)` behind a bottom bar / rail, no nav library for three
 * flat destinations. Quick Entry is a pushed full-screen overlay reached via the FAB, not a tab.
 * Width breakpoint via BoxWithConstraints — no material3-window-size-class dependency for one check.
 */
@Composable
fun App(module: AppModule) {
    TradingTailTheme {
        var screen by remember { mutableStateOf(Screen.Journal) }
        var showEntry by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val journalVm = remember { JournalViewModel(module.tradeRepo, module.deleteTrade) }
        val quickVm = remember { QuickTradeEntryViewModel(module.recordQuickTrade) }
        val calendarVm = remember { CalendarViewModel(module.tradeRepo, module.calculateCalendarPnl) }
        val analyticsVm = remember {
            AnalyticsViewModel(
                module.tradeRepo,
                module.calculateWinRate,
                module.calculatePnlBySymbol,
                module.calculatePnlByHour,
            )
        }

        if (showEntry) {
            QuickTradeEntryScreen(
                vm = quickVm,
                onBack = { showEntry = false },
                onSaved = {
                    showEntry = false
                    screen = Screen.Journal
                    scope.launch { snackbar.showSnackbar("Trade recorded") }
                },
            )
            return@TradingTailTheme
        }

        BoxWithConstraints {
            val wide = maxWidth >= 600.dp
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            Screen.entries.forEach { s ->
                                NavigationBarItem(
                                    selected = screen == s,
                                    onClick = { screen = s },
                                    icon = { Icon(s.icon, contentDescription = s.label) },
                                    label = { Text(s.label) },
                                )
                            }
                        }
                    }
                },
                floatingActionButton = { if (!wide) CaptureFab { showEntry = true } },
            ) { padding ->
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                        NavigationRail(header = { CaptureFab { showEntry = true } }) {
                            Screen.entries.forEach { s ->
                                NavigationRailItem(
                                    selected = screen == s,
                                    onClick = { screen = s },
                                    icon = { Icon(s.icon, contentDescription = s.label) },
                                    label = { Text(s.label) },
                                )
                            }
                        }
                        ScreenContent(screen, journalVm, calendarVm, analyticsVm, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    ScreenContent(screen, journalVm, calendarVm, analyticsVm, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun CaptureFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) { Icon(Icons.Filled.Add, contentDescription = "Record trade") }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    journalVm: JournalViewModel,
    calendarVm: CalendarViewModel,
    analyticsVm: AnalyticsViewModel,
    modifier: Modifier,
) {
    Surface(modifier = modifier) {
        when (screen) {
            Screen.Journal -> JournalScreen(journalVm)
            Screen.Calendar -> CalendarScreen(calendarVm)
            Screen.Analytics -> AnalyticsScreen(analyticsVm)
        }
    }
}

// ponytail: a 3-bar glyph built by hand instead of adding the whole material-icons-extended artifact
// for one icon (List + DateRange are in material-icons-core; a bar chart isn't).
private val BarChartIcon: ImageVector = ImageVector.Builder(
    name = "BarChart",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) { moveTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 20f); lineTo(4f, 20f); close() }
    path(fill = SolidColor(Color.Black)) { moveTo(10f, 6f); lineTo(14f, 6f); lineTo(14f, 20f); lineTo(10f, 20f); close() }
    path(fill = SolidColor(Color.Black)) { moveTo(16f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(16f, 20f); close() }
}.build()

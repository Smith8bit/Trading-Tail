package com.tradingtail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tradingtail.ui.analytics.AnalyticsScreen
import com.tradingtail.ui.analytics.AnalyticsViewModel
import com.tradingtail.ui.analytics.DashboardScreen
import com.tradingtail.ui.calendar.CalendarScreen
import com.tradingtail.ui.calendar.CalendarViewModel
import com.tradingtail.ui.journal.JournalScreen
import com.tradingtail.ui.journal.JournalViewModel
import com.tradingtail.ui.theme.TradingTailTheme
import com.tradingtail.ui.tradeentry.QuickTradeEntryScreen
import com.tradingtail.ui.tradeentry.QuickTradeEntryViewModel
import kotlinx.coroutines.launch

private enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Home),
    Journal("Journal", Icons.AutoMirrored.Filled.List),
    Calendar("Calendar", Icons.Filled.DateRange),
    Analytics("Reports", BarChartIcon),
}

/**
 * ponytail: state-based nav — a `when(screen)` behind a bottom bar / rail, no nav library for three
 * flat destinations. Quick Entry is a pushed full-screen overlay reached via the FAB, not a tab.
 * Width breakpoint via BoxWithConstraints — no material3-window-size-class dependency for one check.
 */
@Composable
fun App(module: AppModule) {
    TradingTailTheme {
        var screen by remember { mutableStateOf(Screen.Dashboard) }
        var showEntry by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val journalVm = remember { JournalViewModel(module.tradeRepo, module.deleteTrade) }
        val quickVm = remember { QuickTradeEntryViewModel(module.recordQuickTrade) }
        val calendarVm = remember { CalendarViewModel(module.tradeRepo, module.calculateCalendarPnl) }
        val analyticsVm = remember {
            AnalyticsViewModel(
                module.tradeRepo,
                module.executionRepo,
                module.calculateWinRate,
                module.calculatePnlBySymbol,
                module.calculatePnlByHour,
            )
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
                        Sidebar(current = screen, onSelect = { screen = it }, onNewTrade = { showEntry = true })
                        VerticalDivider()
                        ScreenContent(screen, { screen = it }, journalVm, calendarVm, analyticsVm, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    ScreenContent(screen, { screen = it }, journalVm, calendarVm, analyticsVm, Modifier.padding(padding))
                }
            }
        }

        // Quick Entry as a modal over the shell — sidebar/content stay visible (dimmed) behind it.
        if (showEntry) {
            Dialog(
                onDismissRequest = { showEntry = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f).heightIn(max = 640.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    QuickTradeEntryScreen(
                        vm = quickVm,
                        onBack = { showEntry = false },
                        onSaved = {
                            showEntry = false
                            screen = Screen.Journal
                            scope.launch { snackbar.showSnackbar("Trade recorded") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) { Icon(Icons.Filled.Add, contentDescription = "Record trade") }
}

/** Desktop sidebar, styled after the dashboard mock: gradient brand mark, nav, gradient CTA, account. */
@Composable
private fun Sidebar(current: Screen, onSelect: (Screen) -> Unit, onNewTrade: () -> Unit) {
    val accent = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
    )
    Column(
        modifier = Modifier.fillMaxHeight().width(232.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Brand
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BarChartIcon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
            }
            Row {
                Text("Trading", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tail",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Screen.entries.forEach { s -> NavItem(s, s == current) { onSelect(s) } }

        Spacer(Modifier.weight(1f))

        // Gradient "New Trade" CTA
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(accent)
                .clickable(onClick = onNewTrade).padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Trade", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }

        // Account chip
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("KS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text("K. Siwatt", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Bangkok · USD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(screen.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(screen.label, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    journalVm: JournalViewModel,
    calendarVm: CalendarViewModel,
    analyticsVm: AnalyticsViewModel,
    modifier: Modifier,
) {
    // Content sits on the darker canvas so the lighter `surface` cards read as raised (like the mock).
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.Dashboard -> DashboardScreen(analyticsVm)
            Screen.Journal -> JournalScreen(journalVm)
            Screen.Calendar -> CalendarScreen(calendarVm)
            Screen.Analytics -> AnalyticsScreen(analyticsVm, onOpenCalendar = { onNavigate(Screen.Calendar) })
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

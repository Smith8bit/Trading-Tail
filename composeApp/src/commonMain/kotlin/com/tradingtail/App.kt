package com.tradingtail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.tradingtail.data.imports.ParsedFill
import com.tradingtail.data.imports.pickPdfBytes
import com.tradingtail.ui.calendar.CalendarScreen
import com.tradingtail.ui.calendar.CalendarViewModel
import com.tradingtail.ui.imports.ImportPreviewContent
import com.tradingtail.ui.journal.JournalScreen
import com.tradingtail.ui.journal.JournalViewModel
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.TradingTailTheme
import com.tradingtail.ui.theme.glassChrome
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
        var importFills by remember { mutableStateOf<List<ParsedFill>?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // Pick a Webull PDF → parse → stash fills for the confirm dialog.
        val onImport: () -> Unit = {
            scope.launch {
                val bytes = pickPdfBytes() ?: return@launch
                runCatching { module.importWebullPdf.preview(bytes) }
                    .onSuccess { importFills = it }
                    .onFailure { snackbar.showSnackbar("Couldn't read that PDF") }
            }
        }

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
            // One haze source (the immersive ground); the glass chrome panels blur what's behind them.
            val hazeState = remember { HazeState() }
            ImmersiveBackground(Modifier.matchParentSize().hazeSource(hazeState))
            // Transparent containers break contentColorFor's chain (it falls back to an unset local →
            // black text on the dark canvas). Pin the content color once for the whole shell.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = { if (!wide) MobileTopBar(onImport, hazeState) },
                bottomBar = {
                    if (!wide) {
                        NavigationBar(
                            modifier = Modifier.hazeEffect(hazeState, glassChrome()),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                        ) {
                            Screen.entries.forEach { s ->
                                NavigationBarItem(
                                    selected = screen == s,
                                    onClick = { screen = s },
                                    // label below carries the accessible name — null avoids a double read.
                                    icon = { Icon(s.icon, contentDescription = null) },
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
                        // Floating glass panel — no divider; the inset + sheen border do the separation.
                        Sidebar(hazeState, current = screen, onSelect = { screen = it }, onNewTrade = { showEntry = true }, onImport = onImport)
                        ScreenContent(screen, { screen = it }, journalVm, calendarVm, analyticsVm, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    ScreenContent(screen, { screen = it }, journalVm, calendarVm, analyticsVm, Modifier.padding(padding))
                }
            }
            }
        }

        // Quick Entry as a modal over the shell — sidebar/content stay visible (dimmed) behind it.
        if (showEntry) {
            Dialog(
                onDismissRequest = { showEntry = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                BoxWithConstraints {
                    // compact measures the real window, not the dialog's own ≤560dp width (which would
                    // always read narrow) — same threshold as the rest of the app.
                    val compact = maxWidth < 600.dp
                    val tc = LocalTradeColors.current
                    Surface(
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(if (compact) 0.96f else 0.92f).heightIn(max = 640.dp),
                        shape = RoundedCornerShape(Radii.xl),
                        // Glass sheet: near-solid so the form stays legible, but the dimmed shell ghosts through.
                        color = tc.glass.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, tc.sheen),
                    ) {
                        QuickTradeEntryScreen(
                            vm = quickVm,
                            onBack = { showEntry = false },
                            onSaved = {
                                showEntry = false
                                screen = Screen.Journal
                                scope.launch { snackbar.showSnackbar("Trade recorded") }
                            },
                            compact = compact,
                        )
                    }
                }
            }
        }

        // Statement import: preview the parsed fills, then commit on confirm.
        importFills?.let { fills ->
            Dialog(
                onDismissRequest = { importFills = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                val tc = LocalTradeColors.current
                Surface(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f).heightIn(max = 640.dp),
                    shape = RoundedCornerShape(Radii.xl),
                    color = tc.glass.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, tc.sheen),
                ) {
                    ImportPreviewContent(
                        fills = fills,
                        onCancel = { importFills = null },
                        onConfirm = {
                            importFills = null
                            scope.launch {
                                val s = module.importWebullPdf.commit(fills)
                                screen = Screen.Journal
                                snackbar.showSnackbar(
                                    if (s.skipped > 0) "Imported ${s.executions} executions · ${s.skipped} duplicates skipped"
                                    else "Imported ${s.executions} executions across ${s.symbols} symbols",
                                )
                            }
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

// Narrow layout has no sidebar, so Import is a direct top-bar action — one tap, always visible —
// instead of hiding a single item behind an overflow menu.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MobileTopBar(onImport: () -> Unit, hazeState: HazeState) {
    TopAppBar(
        modifier = Modifier.hazeEffect(hazeState, glassChrome()),
        title = { Text("Trading Tail") },
        actions = {
            TextButton(onClick = onImport) { Text("Import") }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

/** Desktop sidebar: a floating glass panel — inset from the window edge, real backdrop blur + frost. */
@Composable
private fun Sidebar(hazeState: HazeState, current: Screen, onSelect: (Screen) -> Unit, onNewTrade: () -> Unit, onImport: () -> Unit) {
    val tc = LocalTradeColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier.fillMaxHeight().width(256.dp)
            .padding(Space.md) // inset margin — the panel floats over the immersive ground
            .clip(shape)
            .hazeEffect(hazeState, glassChrome()) // real blur + noise; tint comes from the style
            .border(1.dp, tc.sheen, shape)
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
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(Radii.md)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BarChartIcon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
            }
            Row {
                Text("Trading", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tail",
                    color = tc.accent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Screen.entries.forEach { s -> NavItem(s, s == current) { onSelect(s) } }

        Spacer(Modifier.weight(1f))

        // "New Trade" CTA — solid Signal Green, matching the "Record trade" button
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onNewTrade).padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Trade", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import statement") }

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
    val tc = LocalTradeColors.current
    val bg = if (selected) tc.accent.copy(alpha = 0.15f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (selected) tc.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(bg)
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
    // Transparent so the immersive canvas + glow show through the bento tile gaps; tiles stay opaque surface.
    Surface(modifier = modifier, color = Color.Transparent) {
        when (screen) {
            Screen.Dashboard -> DashboardScreen(analyticsVm)
            Screen.Journal -> JournalScreen(journalVm)
            Screen.Calendar -> CalendarScreen(calendarVm)
            Screen.Analytics -> AnalyticsScreen(analyticsVm, onOpenCalendar = { onNavigate(Screen.Calendar) })
        }
    }
}

/**
 * The immersive ground: the base canvas plus two faint Webull-blue radial glows (top-right, bottom-left).
 * Subtle — the accent is data-quiet chrome, not decoration — and near-invisible in light. Drawn once,
 * behind the whole shell, so glass edges and bento gaps reveal it.
 */
@Composable
private fun ImmersiveBackground(modifier: Modifier) {
    val base = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val dark = isSystemInDarkTheme()
    // Strong enough to bleed through the translucent glass tiles; still chrome, not decoration.
    val a1 = if (dark) 0.26f else 0.10f
    val a2 = if (dark) 0.16f else 0.07f
    val a3 = if (dark) 0.12f else 0.05f
    Box(modifier.drawBehind {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = a1), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * -0.05f),
                radius = size.maxDimension * 0.62f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = a2), Color.Transparent),
                center = Offset(size.width * -0.05f, size.height * 1.05f),
                radius = size.maxDimension * 0.55f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = a3), Color.Transparent),
                center = Offset(size.width * 0.30f, size.height * 0.45f),
                radius = size.maxDimension * 0.40f,
            ),
        )
    })
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

package com.tradingtail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tradingtail.ui.analytics.AnalyticsScreen
import com.tradingtail.ui.analytics.AnalyticsViewModel
import com.tradingtail.ui.analytics.DashboardScreen
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.imports.pickPdfBytes
import com.tradingtail.data.sync.SyncState
import com.tradingtail.ui.settings.OnboardingScreen
import com.tradingtail.ui.settings.SettingsDialog
import kotlinx.coroutines.flow.MutableStateFlow
import com.tradingtail.domain.usecase.ImportPreview
import com.tradingtail.ui.calendar.CalendarScreen
import com.tradingtail.ui.calendar.CalendarViewModel
import com.tradingtail.ui.imports.ImportPreviewContent
import com.tradingtail.ui.journal.JournalScreen
import com.tradingtail.ui.journal.JournalViewModel
import com.tradingtail.ui.theme.BarChartIcon
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.tradedetail.TradeDetailScreen
import com.tradingtail.ui.tradedetail.TradeDetailViewModel
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

internal enum class Screen(val label: String, val icon: ImageVector) {
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
    // Theme follows the OS at launch, then the in-app toggle takes over. ponytail: session-only state,
    // resets to system on restart — add DataStore persistence when a settings screen needs it.
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    TradingTailTheme(darkTheme = dark) {
        var screen by rememberSaveable { mutableStateOf(Screen.Dashboard) }
        var showEntry by rememberSaveable { mutableStateOf(false) }
        var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
        // The open trade detail, held as an EXECUTION id rather than a trade id: correcting a fill
        // re-derives the symbol's trades, so every trade row id churns. Fills are the durable rows.
        var detailAnchor by rememberSaveable { mutableStateOf<Long?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // Pick a Webull PDF → parse → stash the resolved preview for the confirm gate.
        val onImport: () -> Unit = {
            scope.launch { runImport(module, snackbar) { importPreview = it } }
        }

        val journalVm = remember { JournalViewModel(module.tradeRepo) }
        val quickVm = remember { QuickTradeEntryViewModel(module.recordQuickTrade) }
        val calendarVm = remember { CalendarViewModel(module.tradeRepo, module.tradeNoteRepo, module.calculateCalendarPnl) }
        val analyticsVm = remember {
            AnalyticsViewModel(
                module.tradeRepo,
                module.executionRepo,
                module.calculateWinRate,
                module.calculatePnlBySymbol,
                module.calculatePnlByHour,
            )
        }

        // Background two-way sync while the app is open. No-op when no credentials are configured
        // (syncManager == null). Cancelled automatically when App leaves composition.
        val syncManager = module.syncManager
        syncManager?.let { LaunchedEffect(it) { it.runPeriodic() } }
        // Always collect from a non-null flow so collectAsState is called unconditionally (Compose rule);
        // a stand-in flow covers the no-credentials case. The Sync button is hidden then anyway.
        val syncFlow = remember(syncManager) { syncManager?.state ?: MutableStateFlow(SyncState()) }
        val syncState by syncFlow.collectAsState()

        // Device-local profile — drives first-run onboarding and the account chip; never synced.
        val appSettings by module.settings.data.collectAsState()
        var showSettings by remember { mutableStateOf(false) }

        // One bundle feeds both chrome surfaces (desktop sidebar + mobile pill), so they can't drift.
        val chrome = ShellChrome(
            dark = dark,
            onToggleTheme = { dark = !dark },
            onImport = onImport,
            syncEnabled = syncManager != null,
            syncState = syncState,
            onSync = { syncManager?.let { sm -> scope.launch { sm.syncOnce() } } },
            displayName = appSettings.displayName,
            onOpenSettings = { showSettings = true },
        )

        BoxWithConstraints {
            val wide = maxWidth >= 600.dp
            // Desktop shows the selected trade beside the list instead of pushing over it — but only
            // once there's room for all three columns: 256dp sidebar + DETAIL_PANEL + a journal list
            // that still reads (~320dp). Under that, the full-screen push is the honest layout.
            val splitDetail = maxWidth >= 1040.dp
            // One haze source (the aurora ground); the glass chrome panels blur what's behind them.
            // Data tiles don't — they're opaque now, see Glass.kt.
            val hazeState = remember { HazeState() }
            ImmersiveBackground(dark, Modifier.matchParentSize().hazeSource(hazeState))
            // Transparent containers break contentColorFor's chain (it falls back to an unset local →
            // black text on the dark canvas). Pin the content color once for the whole shell.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            if (!appSettings.configured) {
                // First run: ask the name before anything else, over the same aurora ground.
                OnboardingScreen(
                    onDone = { name -> module.settings.update { it.copy(displayName = name, configured = true) } },
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
                return@CompositionLocalProvider
            }
            val anchor = detailAnchor
            if (showEntry) {
                // A pushed full-screen surface, per DESIGN.md's nav spec ("a full-screen overlay with a
                // back arrow, not a tab"). It was a Dialog, whose dismiss-on-outside-tap destroyed all
                // eight fields of hand-typed money with no confirmation — the app's worst bug, on its
                // highest-traffic path. The form now owns back and guards a dirty form itself.
                QuickTradeEntryScreen(
                    vm = quickVm,
                    onBack = { showEntry = false },
                    onSaved = {
                        showEntry = false
                        screen = Screen.Journal
                        scope.launch { snackbar.showSnackbar("Trade recorded") }
                    },
                    compact = !wide,
                    // No Scaffold on this branch, so system-bar insets are this surface's own job.
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
                return@CompositionLocalProvider
            }
            if (anchor != null && !splitDetail) {
                // A pushed full-screen surface with a back arrow, over the same aurora — not a dialog.
                // The shell's nav is deliberately gone: this is a place you leave, not a tab.
                TradeDetail(
                    module = module,
                    anchor = anchor,
                    onBack = { detailAnchor = null },
                    // No Scaffold here, so system-bar insets are this surface's own job.
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
                return@CompositionLocalProvider
            }
            MainScaffold(
                module = module,
                chrome = chrome,
                hazeState = hazeState,
                wide = wide,
                splitDetail = splitDetail,
                screen = screen,
                onScreen = { screen = it },
                journalVm = journalVm,
                calendarVm = calendarVm,
                analyticsVm = analyticsVm,
                anchor = anchor,
                onAnchor = { detailAnchor = it },
                onNewTrade = { showEntry = true },
                snackbar = snackbar,
            )
            }
        }

        if (showSettings) {
            SettingsDialog(
                current = appSettings,
                onSave = { updated -> module.settings.update { updated }; showSettings = false },
                onDismiss = { showSettings = false },
            )
        }

        // Statement import: preview the parsed fills, then commit on confirm.
        importPreview?.let { preview ->
            ImportPreviewHost(
                preview = preview,
                onDismiss = { importPreview = null },
                onConfirm = {
                    importPreview = null
                    scope.launch {
                        val s = module.importWebullPdf.commit(preview.fills)
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

/**
 * Pick a statement, parse it, hand the resolved preview to the confirm gate — or explain the failure
 * and offer the retry inline.
 *
 * The old failure path was a bare "Couldn't read that PDF": no reason, so the user couldn't tell
 * whether to retry, pick a different file, or give up, and no way back into the picker without
 * hunting for the Import button again. Recursive on retry, which is the whole reason this is a
 * function rather than a lambda in the composable — a lambda can't call itself.
 */
private suspend fun runImport(
    module: AppModule,
    snackbar: SnackbarHostState,
    onPreview: (ImportPreview) -> Unit,
) {
    val bytes = pickPdfBytes() ?: return // user backed out of the picker; not a failure
    runCatching { module.importWebullPdf.preview(bytes) }
        .onSuccess(onPreview)
        .onFailure { e ->
            // The parser's own message is the specific one ("no trade rows found", a bad date, …);
            // the fallback covers the common case of simply the wrong PDF.
            val why = e.message?.takeIf { it.isNotBlank() }
                ?: "it doesn't look like a Webull trade statement"
            val action = snackbar.showSnackbar(
                message = "Couldn't read that PDF — $why",
                actionLabel = "Pick another",
                duration = SnackbarDuration.Long,
            )
            if (action == SnackbarResult.ActionPerformed) runImport(module, snackbar, onPreview)
        }
}

/**
 * The main shell once onboarding / quick-entry / the full-screen detail push are out of the way:
 * nav chrome (bottom bar or sidebar), the selected screen, and — when the window can afford it —
 * the split detail panel. Pure layout: every piece of state lives in [App] and arrives here as a
 * value + callback pair, so this can't grow state of its own.
 */
@Composable
private fun MainScaffold(
    module: AppModule,
    chrome: ShellChrome,
    hazeState: HazeState,
    wide: Boolean,
    splitDetail: Boolean,
    screen: Screen,
    onScreen: (Screen) -> Unit,
    journalVm: JournalViewModel,
    calendarVm: CalendarViewModel,
    analyticsVm: AnalyticsViewModel,
    anchor: Long?,
    onAnchor: (Long?) -> Unit,
    onNewTrade: () -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
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
                            onClick = { onScreen(s) },
                            // label below carries the accessible name — null avoids a double read.
                            icon = { Icon(s.icon, contentDescription = null) },
                            // labelSmall (12sp), not the inherited labelMedium: our type scale bumps
                            // labelMedium to 14sp, but M3 sizes nav labels for 12sp and a phone only
                            // gives each of four items ~90dp. Android then multiplies by the user's
                            // font-size setting, so 14sp wrapped "Dashboard" onto two lines. 12sp is
                            // M3's intended size and still on our scale (FontSize.xs). Mobile-only:
                            // the NavigationBar exists only when !wide — desktop uses the Sidebar.
                            label = {
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, // backstop at huge font scales
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = { if (!wide) CaptureFab(onNewTrade) },
    ) { padding ->
        if (wide) {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Floating glass panel — no divider; the inset + sheen border do the separation.
                Sidebar(hazeState, current = screen, onSelect = onScreen, onNewTrade = onNewTrade, chrome = chrome)
                ScreenContent(screen, onScreen, journalVm, calendarVm, analyticsVm, onOpenTrade = { onAnchor(it.entryExecutionIds.firstOrNull()) }, onNewTrade = onNewTrade, selectedAnchor = anchor.takeIf { splitDetail }, modifier = Modifier.weight(1f).fillMaxSize())
                // Content, not chrome: bare tiles over the aurora, exactly like the list beside
                // it. Only on Journal — a trade detail wedged next to the Calendar would be a
                // non-sequitur, so the anchor just goes dormant on the other tabs and the panel
                // is waiting when the user comes back.
                //
                // The column is permanent here, holding an empty state when nothing is picked,
                // rather than appearing on first click: a panel that comes and goes re-lays out
                // every row in the journal each time, so the list the user was aiming at moves
                // under the cursor at the exact moment they click it.
                if (splitDetail && screen == Screen.Journal) {
                    val panel = Modifier.width(DETAIL_PANEL).fillMaxHeight()
                    if (anchor != null) {
                        TradeDetail(module, anchor, onBack = { onAnchor(null) }, modifier = panel)
                    } else {
                        DetailEmpty(panel)
                    }
                }
            }
        } else {
            // No app bar on mobile — Import + theme float over the immersive content, bottom-LEFT,
            // mirroring the FAB across the thumb zone. They sat top-right until 2026-07-16: the
            // least reachable corner of a one-handed phone, and directly over the first day's
            // subtotal in the journal. Every scrolling screen had grown its own clearance hack to
            // dodge them (Journal reserved 48dp of contentPadding, Dashboard and Reports padded
            // their headers, Calendar forgot to and let the pill land on the MonthCard). Moving the
            // pill deleted all four. Keep it out of the top-right corner.
            Box(Modifier.fillMaxSize().padding(padding)) {
                ScreenContent(screen, onScreen, journalVm, calendarVm, analyticsVm, onOpenTrade = { onAnchor(it.entryExecutionIds.firstOrNull()) }, onNewTrade = onNewTrade, modifier = Modifier.fillMaxSize())
                FloatingActions(
                    hazeState = hazeState,
                    chrome = chrome,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Space.md),
                )
            }
        }
    }
}

/** The import confirm gate's presentation: a width-capped glass dialog around [ImportPreviewContent]. */
@Composable
private fun ImportPreviewHost(preview: ImportPreview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints {
            // Same real-window measure as Quick Entry — the dialog's own capped width would always
            // read narrow.
            val compact = maxWidth < 600.dp
            val tc = LocalTradeColors.current
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(if (compact) 0.96f else 0.92f).heightIn(max = 640.dp),
                shape = RoundedCornerShape(Radii.xl),
                color = tc.glass.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, tc.sheen),
            ) {
                ImportPreviewContent(preview = preview, onCancel = onDismiss, onConfirm = onConfirm, compact = compact)
            }
        }
    }
}

/**
 * Width of the desktop detail panel. Above phone width on purpose: the detail's widest rows are a
 * label against a monospace figure ("Proceeds (sold)" … "+$407.00"), and the fills list adds a third
 * column of fees — at phone width those crowd the gutter the eye needs to pair label to number.
 */
private val DETAIL_PANEL = 540.dp

/**
 * The trade detail, wherever it's rendered: a full-screen push when the window is too narrow to split,
 * a right-hand panel when it isn't. Same screen, same ViewModel, keyed on the anchor fill — only the
 * modifier differs, so the two layouts can't drift apart.
 */
@Composable
private fun TradeDetail(module: AppModule, anchor: Long, onBack: () -> Unit, modifier: Modifier) {
    val vm = remember(anchor) {
        TradeDetailViewModel(
            anchorExecutionId = anchor,
            tradeRepo = module.tradeRepo,
            executionRepo = module.executionRepo,
            notesRepo = module.tradeNoteRepo,
            deleteTrade = module.deleteTrade,
            updateExecution = module.updateExecution,
        )
    }
    TradeDetailScreen(vm = vm, onBack = onBack, modifier = modifier)
}

/**
 * The panel with nothing picked. Bare centered text over the aurora — the same shape as the journal's
 * own "No trades yet", not a card: an empty card would draw a big lit box around the absence of data,
 * and tiles in this app mean "a figure lives here".
 */
@Composable
private fun DetailEmpty(modifier: Modifier) {
    Column(
        modifier = modifier.padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No trade selected", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pick a trade from the journal to see the fills behind its P&L, the arithmetic that gets " +
                "there, and the note you left on it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}


@Composable
private fun ScreenContent(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    journalVm: JournalViewModel,
    calendarVm: CalendarViewModel,
    analyticsVm: AnalyticsViewModel,
    onOpenTrade: (TradeEntity) -> Unit,
    onNewTrade: () -> Unit,
    selectedAnchor: Long? = null,
    modifier: Modifier,
) {
    // Transparent so the immersive canvas + glow show through the bento tile gaps; tiles stay opaque surface.
    Surface(modifier = modifier, color = Color.Transparent) {
        when (screen) {
            Screen.Dashboard -> DashboardScreen(analyticsVm)
            Screen.Journal -> JournalScreen(journalVm, onOpenTrade = onOpenTrade, onNewTrade = onNewTrade, selectedAnchor = selectedAnchor)
            Screen.Calendar -> CalendarScreen(calendarVm)
            Screen.Analytics -> AnalyticsScreen(analyticsVm, onOpenCalendar = { onNavigate(Screen.Calendar) })
        }
    }
}

/**
 * The aurora backdrop — what the frosted glass frosts. Replaces the old faint "immersive" wash:
 * fewer, bigger, more vivid blue-family blobs (Webull blue → indigo → sky) spread across the canvas
 * so every tile has color under it. Vivid ON PURPOSE: over a flat ground, glass reads as solid; the
 * blur on chrome and tiles is what tames this back down to ambience. Blue-family only — gain-green
 * and loss-red stay unambiguous as data.
 */
@Composable
private fun ImmersiveBackground(dark: Boolean, modifier: Modifier) {
    val base = MaterialTheme.colorScheme.background
    val blue = Color(0xFF005FFF)
    val indigo = Color(0xFF5B3DF5)
    val sky = Color(0xFF4D8BFF)
    val a1 = if (dark) 0.42f else 0.20f
    val a2 = if (dark) 0.34f else 0.15f
    val a3 = if (dark) 0.28f else 0.12f
    Box(modifier.drawBehind {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                colors = listOf(blue.copy(alpha = a1), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.02f),
                radius = size.maxDimension * 0.60f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(indigo.copy(alpha = a2), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.85f),
                radius = size.maxDimension * 0.55f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(sky.copy(alpha = a3), Color.Transparent),
                center = Offset(size.width * 0.45f, size.height * 0.40f),
                radius = size.maxDimension * 0.45f,
            ),
        )
    })
}


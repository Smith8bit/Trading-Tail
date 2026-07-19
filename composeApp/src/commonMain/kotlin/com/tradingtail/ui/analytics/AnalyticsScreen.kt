package com.tradingtail.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BkkDate
import com.tradingtail.common.bkkDate
import com.tradingtail.common.nowMillis
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.ui.theme.FAB_CLEARANCE
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, onOpenCalendar: () -> Unit, modifier: Modifier = Modifier) {
    // initial = null, not empty: "no data yet" and "haven't asked the database yet" are different
    // facts. Conflating them made cold start render $0.00 across the board before Room's first
    // emission — a figure the user could read and believe. Nothing renders during the gap.
    val allTradesOrNull by vm.trades.collectAsState(initial = null)
    val allExecutionsOrNull by vm.executions.collectAsState(initial = null)
    val allTrades = allTradesOrNull ?: return
    val allExecutions = allExecutionsOrNull ?: return
    val now = remember { nowMillis() }
    var tab by remember { mutableStateOf(0) }
    var view by remember { mutableStateOf(ReportView.Recent) }
    var fromDate by remember { mutableStateOf<BkkDate?>(null) }
    var toDate by remember { mutableStateOf<BkkDate?>(null) }

    // Global From–To day filter (by trade day = entry day); every tab reads from these filtered sets.
    val hasFilter = fromDate != null || toDate != null
    val trades = remember(allTrades, fromDate, toDate) { allTrades.filter { dayInRange(it.tradeDay(), fromDate, toDate) } }
    val executions = remember(allExecutions, fromDate, toDate) { allExecutions.filter { dayInRange(bkkDate(it.timestamp), fromDate, toDate) } }
    val win = remember(trades) { CalculateWinRate()(trades) }
    val byHour = remember(trades) { CalculatePnlByHour()(trades) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ponytail: one breakpoint — <600dp (phones) stacks every 2-up and scrolls overflowing bars.
        val compact = maxWidth < 600.dp
        // The sheet's heads must fit one row — the outline traces the active head, so they can't wrap.
        // Full length measured ~1060px against a 411dp phone's 1017; "Win/Loss Days" got that to 1023,
        // which still overflowed by 6px, so selecting Drawdown scrolled the strip and clipped Overview
        // off the left instead. Measured twice, wrong twice: a label budget with ~0px of slack is not a
        // fix. "Win/Loss" lands at ~900 and leaves >100px of room, so ordinary drift can't re-break it.
        // (Unambiguous in place: the Win/Loss/Expectation *category* lives inside Detailed, not here.)
        val tabs = listOf(
            "Overview",
            "Detailed",
            if (compact) "Win/Loss" else "Win vs Loss Days",
            "Drawdown",
        )
        CompositionLocalProvider(LocalCompact provides compact) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Space.md)) {
        Text(
            "Reports",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = Space.md, bottom = Space.sm),
        )
        DateRangeBar(fromDate, toDate, now) { f, t -> fromDate = f; toDate = t }
        // Top-level switcher as a Chrome-style tab sheet: the active head opens into the outlined
        // panel below, so head + content read as one sheet of paper. (Replaced the M3 TabRow whose
        // blue labels + hairline sat illegibly on the aurora.) Sub-switchers inside the sheet drop to
        // a segmented control — a step down the hierarchy.
        TabSheet(tabs, tab, onSelect = { tab = it }, modifier = Modifier.fillMaxSize().padding(bottom = Space.md)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Space.md, end = Space.md, top = Space.md)
                .padding(bottom = if (compact) FAB_CLEARANCE else Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            when (tab) {
                0 -> {
                    // ponytail: skipped the cosmetic P&L Type / View mode / Report type dropdowns — v1 is
                    // gross-only / $-value / aggregate, so they'd be dead chrome. Add if Net/% ever ships.
                    var period by remember { mutableStateOf(30) }
                    OverviewControls(
                        view = view,
                        period = period,
                        showPeriod = view == ReportView.Recent && !hasFilter,
                        onOpenCalendar = onOpenCalendar,
                        onView = { view = it },
                        onPeriod = { period = it },
                    )
                    when (view) {
                        ReportView.Recent -> RecentView(trades, executions, now, hasFilter, period)
                        ReportView.YearMonthDay -> YearMonthDayView(trades, now)
                    }
                }
                1 -> DetailedView(trades, executions, byHour, win)
                2 -> WinLossDaysView(trades, executions)
                3 -> DrawdownView(trades)
            }
        }
        }
        }
        }
    }
}

// Recent charts a rolling window; Year/Month/Day drills year → month → day. (Calendar is its own nav dest.)
private enum class ReportView { Recent, YearMonthDay }

/** The mock's Recent | Year/Month/Day | Calendar view selector under the Overview tab.
 *  Recent/YMD are real segments; Calendar is a trailing segment that navigates away (never selected). */
@Composable
private fun ReportViewToggle(current: ReportView, onOpenCalendar: () -> Unit, onSelect: (ReportView) -> Unit) {
    val sel = if (current == ReportView.Recent) 0 else 1
    SegmentedControl(listOf("Recent", "Year/Month/Day", "Calendar"), sel) { i ->
        when (i) {
            0 -> onSelect(ReportView.Recent)
            1 -> onSelect(ReportView.YearMonthDay)
            else -> onOpenCalendar()
        }
    }
}

/**
 * The Overview tab's one control row: view selector left, 30/60/90 window right — grouped and
 * baseline-aligned instead of the old two stacked rows with opposite alignments.
 *
 * Compact stacks the two tracks instead. Side by side they measured ~1210px against a 411dp (1017px)
 * phone, and the horizontalScroll that was supposed to rescue them meant "60 Days"/"90 Days" rendered
 * entirely off-screen — two thirds of the window selector, invisible with nothing to hint at it. The
 * scroll also fed [SegmentedControl] an infinite width constraint, so the track could never wrap
 * itself out of the problem. Stacked, each track has the full width and fits. Mirrors the Dashboard's
 * compact header, which stacks its title and the same PeriodToggle for the same reason.
 */
@Composable
private fun OverviewControls(
    view: ReportView,
    period: Int,
    showPeriod: Boolean,
    onOpenCalendar: () -> Unit,
    onView: (ReportView) -> Unit,
    onPeriod: (Int) -> Unit,
) {
    if (LocalCompact.current) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            ReportViewToggle(view, onOpenCalendar, onView)
            if (showPeriod) PeriodToggle(period, onPeriod)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportViewToggle(view, onOpenCalendar, onView)
            if (showPeriod) PeriodToggle(period, onPeriod)
        }
    }
}

/**
 * The mock's day-range filter: a preset quick-pick + manual From/To fields; scopes every report tab.
 *
 * **Wraps, and measure it in the FILLED state.** Empty it reads "All time · From · To" and ends ~590px
 * into a 1017px phone — which is exactly the trap: picking a day swaps the preset to the wider "Custom
 * range", expands both placeholders into mono dates ("2026-07-05"), *and* reveals a Clear button. The
 * bar then ran past 1080 and **Clear rendered off-screen entirely** — the one control that undoes the
 * filter, gone the moment the filter existed. The old horizontalScroll made it reachable only by a
 * sideways swipe with nothing to advertise it.
 *
 * The From–To trio is one atomic Row so a wrap can never split the pairing across lines; only the
 * preset, the range group, and Clear are breakable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateRangeBar(from: BkkDate?, to: BkkDate?, now: Long, onRange: (BkkDate?, BkkDate?) -> Unit) {
    var preset by remember { mutableStateOf<RangePreset?>(null) }
    // ponytail: `itemVerticalAlignment` is Compose 1.8+; this is CMP 1.7.3, so each child aligns itself
    // via FlowRowScope.align — same result, no version bump for one parameter.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(Modifier.align(Alignment.CenterVertically)) {
            PresetPicker(preset, hasDates = from != null || to != null) { p -> preset = p; presetRange(p, now).let { onRange(it.first, it.second) } }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateField(from, "From") { preset = null; onRange(it, to) }
            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
            DateField(to, "To") { preset = null; onRange(from, it) }
        }
        if (from != null || to != null) {
            Box(Modifier.align(Alignment.CenterVertically)) {
                TextButton(onClick = { preset = null; onRange(null, null) }) { Text("Clear") }
            }
        }
    }
}

/**
 * The day-range bar's shared field skin. These sit on the bare aurora, so they need a surface of
 * their own: the fields used to be a 1px outline over the gradient with no fill (near-invisible),
 * and the preset picker a different material again. One opaque skin for both; [active] (a value is
 * set) trades the sheen hairline for a primary edge so a live filter is obvious at a glance.
 */
@Composable
private fun FieldSkin(active: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(Radii.sm) // a field, not a chip
    Row(
        modifier = Modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else LocalTradeColors.current.sheen, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = tapPadV()), // 48dp touch target on phones
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        content = content,
    )
}

/** Dropdown of the range presets (Today, Last 7 days, YTD, …). */
@Composable
private fun PresetPicker(selected: RangePreset?, hasDates: Boolean, onSelect: (RangePreset) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FieldSkin(active = selected != null, onClick = { open = true }) {
            Text(
                // No preset + no manual dates = the reports really cover everything — say so.
                selected?.label ?: if (hasDates) "Custom range" else "All time",
                // "All time" is the real scope, not a placeholder — it reads at full contrast.
                color = if (selected == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (p in RangePreset.entries) {
                DropdownMenuItem(
                    text = {
                        Text(
                            p.label,
                            color = if (p == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (p == selected) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    onClick = { open = false; onSelect(p) },
                )
            }
        }
    }
}

/** A pill showing a chosen day (or a placeholder) that opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: BkkDate?, placeholder: String, onPick: (BkkDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    FieldSkin(active = date != null, onClick = { show = true }) {
        Text(
            date?.let { "${it.year}-${two(it.month)}-${two(it.day)}" } ?: placeholder,
            // A set date is data (mono, like every other figure); the placeholder stays muted label text.
            color = if (date == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = if (date == null) null else FontFamily.Monospace,
            fontWeight = if (date == null) FontWeight.Normal else FontWeight.Medium,
        )
    }
    if (show) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false; state.selectedDateMillis?.let { onPick(bkkDate(it)) } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

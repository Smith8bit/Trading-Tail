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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.CURRENCY
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkDate
import com.tradingtail.common.formatMoney
import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.HourPnl
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.FAB_CLEARANCE
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import kotlin.math.roundToInt

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

/** Recent window (mock's "Recent"): 30/60/90-day daily P&L, cumulative curve, volume, win%.
 *  [period] lives in the Overview header row (OverviewControls), grouped with the view selector. */
@Composable
private fun RecentView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, now: Long, hasFilter: Boolean, period: Int) {
    val cutoff = now - period * DAY_MS
    // When a global From–To filter is set it already scoped `trades`/`executions` — honor it instead of
    // re-clamping to the rolling window, which would hide any range further back than `period` days.
    val ft = remember(trades, period, hasFilter) { if (hasFilter) trades else trades.filter { it.entryTimestamp >= cutoff } }
    val ew = remember(executions, period, hasFilter) { if (hasFilter) executions else executions.filter { it.timestamp >= cutoff } }
    val span = if (hasFilter) "Selected range" else "$period Days"
    val tc = LocalTradeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        ChartPair(
            { m, fh -> BarChartCard("Gross Daily P&L ($span)", remember(ft) { dailyPnlPoints(ft) }, diverging = true, barColor = tc.gain, modifier = m, fillHeight = fh) },
            { m, fh -> LineCard("Gross Cumulative P&L ($span)", remember(ft) { cumulativeSeries(ft) }, remember(ft) { exitDateLabels(ft) }, tc.gain, m, fillHeight = fh) },
        )
        ChartPair(
            { m, fh -> BarChartCard("Daily Volume ($span)", remember(ew) { volumeByDay(ew) }, diverging = false, barColor = MaterialTheme.colorScheme.primary, modifier = m, fillHeight = fh) },
            { m, fh -> BarChartCard("Win % ($span)", remember(ft) { winRateByDay(ft) }, diverging = false, barColor = tc.gain, modifier = m, fillHeight = fh) },
        )
    }
}

/** Year/Month/Day drill-down (mock's "Year/Month/Day"): year → month → day, distribution + performance. */
@Composable
private fun YearMonthDayView(trades: List<TradeEntity>, now: Long) {
    val tc = LocalTradeColors.current
    val nowDate = remember(now) { bkkDate(now) }
    val years = remember(trades) { trades.map { it.tradeDay().year }.distinct().sorted() }
    var year by remember(years) { mutableStateOf(years.lastOrNull() ?: nowDate.year) }
    var month by remember { mutableStateOf(nowDate.month) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        ChartPair(
            { m, fh -> BarChartCard("Trade Distribution by Year", remember(trades) { tradesByYear(trades) }, false, MaterialTheme.colorScheme.primary, m, fh, xLabel = "Year", labelEveryBar = true) },
            { m, fh -> BarChartCard("Performance by Year", remember(trades) { pnlByYear(trades) }, true, tc.gain, m, fh, xLabel = "Year", labelEveryBar = true) },
        )
        ChipRow((years.ifEmpty { listOf(nowDate.year) }).map { it.toString() }, year.toString()) { year = it.toInt() }
        ChartPair(
            { m, fh -> BarChartCard("Trade Distribution by Month", remember(trades, year) { tradesByMonthOfYear(trades, year) }, false, MaterialTheme.colorScheme.primary, m, fh, xLabel = "Month", labelEveryBar = true) },
            { m, fh -> BarChartCard("Performance by Month", remember(trades, year) { pnlByMonthOfYear(trades, year) }, true, tc.gain, m, fh, xLabel = "Month", labelEveryBar = true) },
        )
        ChipRow(MONTHS, MONTHS[month - 1]) { month = MONTHS.indexOf(it) + 1 }
        ChartPair(
            { m, fh -> BarChartCard("Trade Distribution by Day", remember(trades, year, month) { tradesByDayOfMonth(trades, year, month) }, false, MaterialTheme.colorScheme.primary, m, fh, xLabel = "Day") },
            { m, fh -> BarChartCard("Performance by Day of Month", remember(trades, year, month) { pnlByDayOfMonth(trades, year, month) }, true, tc.gain, m, fh, xLabel = "Day") },
        )
    }
}

/**
 * A row of selectable chips (the year picker, the month picker) that **wraps** instead of scrolling —
 * the twelve month chips ran ~1670px against a 411dp phone's 1017, so more than half the year was
 * reachable only by a sideways swipe with nothing on screen to suggest it. A month picker that hides
 * months is not a picker. Wrapping fits any item count at any font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        for (item in items) ToggleChip(item, item == selected) { onSelect(item) }
    }
}

/** A titled line-chart card (Cumulative P&L in the Recent grid) — reuses the shared line canvas. */
@Composable
private fun LineCard(title: String, series: List<Float>, dates: List<String>, line: Color, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(Space.lg)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.lg))
            if (series.size < 2) {
                Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChartBody(series, dates, line, chartModifier(fillHeight, 190.dp), fillToBottom = false, negColor = LocalTradeColors.current.loss)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detailed tab: a stats grid + category breakdowns (the mock's "Detailed" report)
// ---------------------------------------------------------------------------------------------

// Four categories, every one of which plots real data. "Market Behavior" and "Liquidity" used to sit
// here and resolve to nothing but a "needs a market-data source" card — two of six segments that were
// dead ends, on a control that scrolls at 411dp. Add them back alongside the feed that feeds them.
private enum class DetailCat(val label: String) {
    DaysTimes("Days/Times"), PriceVolume("Price/Volume"), Instrument("Instrument"),
    WinLoss("Win/Loss/Expectation"),
}

@Composable
private fun DetailedView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, byHour: List<HourPnl>, win: WinRateSummary) {
    val priceById = remember(executions) { executions.associate { it.id to it.price } }
    val qtyById = remember(executions) { executions.associate { it.id to it.quantity } }
    var cat by remember { mutableStateOf(DetailCat.DaysTimes) }

    StatsCard(trades, executions)
    CategoryToggle(cat) { cat = it }
    when (cat) {
        DetailCat.DaysTimes -> DaysTimesSection(trades, byHour)
        DetailCat.PriceVolume -> PriceVolumeSection(trades, { priceById[it] }, { qtyById[it] })
        DetailCat.Instrument -> InstrumentSection(trades, { qtyById[it] })
        DetailCat.WinLoss -> WinLossExpectationSection(trades, win)
    }
}

/** Instrument category: per-symbol ranking + share-size buckets. The rest of the mock's instrument
 *  reports (ATR, SMA, relative volume, opening gap, day type, movement) need OHLC market data — deferred. */
@Composable
private fun InstrumentSection(trades: List<TradeEntity>, qtyById: (Long) -> BigDecimal?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        TwoUp(
            { HBarChartCard("Performance by Symbol – Top 20", remember(trades) { pnlBySymbolTop(trades) }, performance = true, modifier = it) },
            { HBarChartCard("Performance by Symbol – Bottom 20", remember(trades) { pnlBySymbolBottom(trades) }, performance = true, modifier = it) },
        )
        BreakdownPair("Distribution by Volume Traded", "Performance by Volume Traded", remember(trades) { pnlByVolumeTraded(trades, qtyById) })
    }
}

/** The mock's Days/Times · Price/Volume · … category selector, a segmented control (wraps on narrow widths). */
@Composable
private fun CategoryToggle(current: DetailCat, onSelect: (DetailCat) -> Unit) {
    val cats = DetailCat.entries.toList()
    SegmentedControl(cats.map { it.label }, cats.indexOf(current)) { onSelect(cats[it]) }
}

private data class Stat(val label: String, val value: String, val color: Color)

/**
 * Every summary metric for a set of trades (+ their executions), colors resolved. Shared by the
 * Detailed 3-column grid and the Win-vs-Loss two-column split.
 *
 * Every row here computes. Metrics needing a market-data source or heavy regression stats
 * (per-share, MAE/MFE, K-ratio, prob. of random chance) used to ship as permanent "—" rows — six
 * lines telling the one person who already knows what he hasn't built. They return with their feed.
 * "—" still appears, but only where a real metric is undefined for *this* set of trades (no trades
 * yet, stddev of one sample) — an answer, not a placeholder.
 */
@Composable
private fun statList(trades: List<TradeEntity>, executions: List<ExecutionEntity>): List<Stat> {
    // remember: the block below is ~15 full passes over the trade list (win rate, best/worst,
    // averages, streaks, profit factor, volume, trading days, stddev, SQN, Kelly, avg daily, three
    // hold times, fees). It had no remember at all, so it re-ran on EVERY recomposition — not just
    // when the data changed — and the Win-vs-Loss tab calls it twice (one column per cohort), so a
    // scroll cost ~30 passes over the entire history. Keyed on list identity: Room hands us a new
    // list only when the tables actually change.
    val rows = remember(trades, executions) { statRows(trades, executions) }
    val tc = LocalTradeColors.current
    val plain = MaterialTheme.colorScheme.onSurface
    // Color resolves here, not in the computation — DESIGN.md's Color-At-Call-Site rule. The pure
    // half names a Tone; only this half knows what Tone means in the current theme.
    return rows.map { r ->
        Stat(
            r.label,
            r.value,
            when (r.tone) {
                Tone.Gain -> tc.gain
                Tone.Loss -> tc.loss
                Tone.Plain -> plain
                Tone.Signed -> pnlColor(r.signed!!)
            },
        )
    }
}

/** How a stat's figure is colored. The value, not the theme — resolved by [statList] at the call site. */
private enum class Tone { Gain, Loss, Plain, Signed }

/** A computed stat before the theme is applied. [signed] carries the figure [Tone.Signed] colors by. */
private data class StatRow(val label: String, val value: String, val tone: Tone, val signed: BigDecimal? = null)

/**
 * The arithmetic half of [statList] — pure, no composables, no colors, so it can be remembered and
 * kept off the recomposition path. Every metric here is one or more passes over the trade list; this
 * is the expensive part of the Detailed and Win-vs-Loss tabs.
 */
private fun statRows(trades: List<TradeEntity>, executions: List<ExecutionEntity>): List<StatRow> {
    val na = "—"

    val win = CalculateWinRate()(trades)
    val totalTrades = win.wins + win.losses + win.breakeven
    val bw = bestWorst(trades)
    val avg = averages(trades)
    val streaks = maxStreaks(trades)
    val pf = profitFactor(trades)
    val vol = totalVolume(executions)
    val days = tradingDays(executions)
    val sd = pnlStdDev(trades)
    val sqnV = sqn(trades)
    val kellyV = kelly(trades)
    val adp = avgDailyPnl(trades)

    // Rounded (not truncated) and always two decimals — a "1.9" next to a "2.00" reads as imprecision.
    fun d2(x: Double) = (if (x < 0) "−" else "") + twoDpAbs(kotlin.math.abs(x))
    fun dur(ms: Long?) = ms?.let { formatDuration(it) } ?: na
    fun count(n: Int) = if (totalTrades == 0) "$n" else "$n (${(n * 1000.0 / totalTrades).roundToInt() / 10.0}%)"

    return listOf(
        StatRow("Total Gain/Loss", formatMoney(win.totalPnl), Tone.Signed, win.totalPnl),
        StatRow("Largest Gain", formatMoney(bw.largestGain), Tone.Gain),
        StatRow("Largest Loss", formatMoney(bw.largestLoss), Tone.Loss),
        StatRow("Average Daily Gain/Loss", formatMoney(adp), Tone.Signed, adp),
        StatRow("Average Daily Volume", if (days > 0) (vol.toFloat() / days).toLong().toString() else "0", Tone.Plain),
        StatRow("Average Trade Gain/Loss", formatMoney(avg.perTrade), Tone.Signed, avg.perTrade),
        StatRow("Average Winning Trade", formatMoney(avg.perWinner), Tone.Gain),
        StatRow("Average Losing Trade", formatMoney(avg.perLoser), Tone.Loss),
        StatRow("Total Number of Trades", totalTrades.toString(), Tone.Plain),
        StatRow("Number of Winning Trades", count(win.wins), Tone.Gain),
        StatRow("Number of Losing Trades", count(win.losses), Tone.Loss),
        StatRow("Average Hold Time (scratch trades)", dur(avgHoldScratch(trades)), Tone.Plain),
        StatRow("Average Hold Time (winning trades)", dur(avgHoldMillis(trades, true)), Tone.Plain),
        StatRow("Average Hold Time (losing trades)", dur(avgHoldMillis(trades, false)), Tone.Plain),
        StatRow("Number of Scratch Trades", count(win.breakeven), Tone.Plain),
        StatRow("Max Consecutive Wins", streaks.maxWins.toString(), Tone.Gain),
        StatRow("Max Consecutive Losses", streaks.maxLosses.toString(), Tone.Loss),
        StatRow("Trade P&L Standard Deviation", sd?.let { "$CURRENCY${d2(it)}" } ?: na, Tone.Plain),
        StatRow("System Quality Number (SQN)", sqnV?.let { d2(it) } ?: na, Tone.Plain),
        StatRow("Kelly Percentage", kellyV?.let { if (it < 0) "< 0%" else "${(it * 100).toInt()}%" } ?: na, Tone.Plain),
        StatRow("Profit Factor", pf?.let { d2(it) } ?: "∞", Tone.Plain),
        StatRow("Total Commissions", formatMoney(ZERO), Tone.Plain),
        // Signed cost (−$…), matching the Dashboard's fees figure — "+$144" fees would read as a gain.
        StatRow("Total Fees", formatMoney(ZERO.subtract(totalFees(executions))), Tone.Plain),
    )
}

/** The mock's big Stats table: a 3-column grid of summary metrics with hairline row separators. */
@Composable
private fun StatsCard(trades: List<TradeEntity>, executions: List<ExecutionEntity>) {
    val cols = if (LocalCompact.current) 1 else 3 // ponytail: 3-up crushes long labels on a phone
    val stats = statList(trades, executions)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text("Stats", style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.md))
            StatGrid(stats, cols)
        }
    }
}

/** One column of the Win-vs-Loss split: a dot + title, then label-over-value metric rows. */
@Composable
private fun StatColumn(title: String, dot: Color, trades: List<TradeEntity>, executions: List<ExecutionEntity>, modifier: Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(bottom = Space.md)) {
                Box(Modifier.size(8.dp).background(dot, RoundedCornerShape(2.dp)))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            statList(trades, executions).forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Text("${s.label}:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(s.value, color = s.color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Hairline-separated grid of [StatCell]s, [cols] per row; a ragged last row is padded with spacers. */
@Composable
private fun StatGrid(stats: List<Stat>, cols: Int) {
    stats.chunked(cols).forEachIndexed { i, row ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth()) {
            for (s in row) StatCell(s, Modifier.weight(1f))
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatCell(s: Stat, modifier: Modifier) {
    Row(
        modifier = modifier.padding(vertical = Space.sm, horizontal = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(s.value, color = s.color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.padding(start = Space.sm))
    }
}

/** Days/Times category: paired distribution (count) + performance (P&L) bars per time bucket. */
@Composable
private fun DaysTimesSection(trades: List<TradeEntity>, byHour: List<HourPnl>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        BreakdownPair("Trade Distribution by Day of Week", "Performance by Day of Week", remember(trades) { pnlByDayOfWeek(trades) })
        BreakdownPair("Trade Distribution by Hour of Day", "Performance by Hour of Day", remember(byHour) { hourWindow(byHour) })
        BreakdownPair("Trade Distribution by Month of Year", "Performance by Month of Year", remember(trades) { pnlByMonth(trades) })
        BreakdownPair("Trade Distribution by Duration", "Performance by Duration", remember(trades) { pnlByDuration(trades) })
        BreakdownPair("Trade Distribution by Intraday Duration", "Performance by Intraday Duration", remember(trades) { pnlByIntradayDuration(trades) })
    }
}

/** Price/Volume category: price + share-size buckets. In-trade price range needs market data (deferred). */
@Composable
private fun PriceVolumeSection(trades: List<TradeEntity>, priceById: (Long) -> BigDecimal?, qtyById: (Long) -> BigDecimal?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        BreakdownPair("Trade Distribution by Price", "Performance by Price", remember(trades) { pnlByPrice(trades, priceById) })
        BreakdownPair("Distribution by Volume Traded", "Performance by Volume Traded", remember(trades) { pnlByVolumeTraded(trades, qtyById) })
    }
}

/** Win/Loss/Expectation category: ratio donut, avg win/loss, expectancy, cumulative curve + drawdown. */
@Composable
private fun WinLossExpectationSection(trades: List<TradeEntity>, win: WinRateSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        TwoUp(
            { WinnersCard(win, it) },
            { AveragesCard(remember(trades) { averages(trades) }, it) },
        )
        TwoUp(
            { TradeExpectationCard(remember(trades) { averages(trades).perTrade }, it) },
            { CumulativeCard(remember(trades) { cumulativeSeries(trades) }, remember(trades) { exitDateLabels(trades) }, win.totalPnl, it) },
        )
        DrawdownCard(remember(trades) { drawdownSeries(trades) }, remember(trades) { exitDateLabels(trades) }, remember(trades) { maxDrawdown(trades) })
    }
}

/** Trade expectation = mean P&L per trade — the mock's "Trade Expectation" widget. */
@Composable
private fun TradeExpectationCard(expectancy: BigDecimal, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text("Trade Expectation", style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.lg))
            Text(formatMoney(expectancy), color = pnlColor(expectancy), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text("Expected P&L per trade", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.xs))
        }
    }
}

/** One breakdown card: horizontal bars either by trade count (distribution) or by P&L (performance). */
@Composable
private fun Breakdown(title: String, buckets: List<BucketPnl>, byCount: Boolean, modifier: Modifier = Modifier) {
    SectionCard(title, modifier) {
        if (buckets.isEmpty()) {
            Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        if (byCount) {
            val max = buckets.maxOf { it.trades }.coerceAtLeast(1)
            for (b in buckets) CountRow(b.label, b.trades, max)
        } else {
            val max = maxAbs(buckets.map { it.pnl })
            val total = sumAbs(buckets.map { it.pnl })
            for (b in buckets) BarRow(b.label, b.pnl, max, percentOf(b.pnl, total))
        }
    }
}

/** A distribution (count) + performance ($) pair of horizontal bar charts over the same buckets. */
@Composable
private fun BreakdownPair(distTitle: String, perfTitle: String, buckets: List<BucketPnl>) {
    TwoUp(
        { HBarChartCard(distTitle, buckets, performance = false, modifier = it) },
        { HBarChartCard(perfTitle, buckets, performance = true, modifier = it) },
    )
}

/** Count row: label + count + a neutral magnitude bar (the "distribution" half of a breakdown pair). */
@Composable
private fun CountRow(label: String, count: Int, max: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = Space.xs).height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth((count.toFloat() / max).coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Win vs Loss Days tab: trades split into winning-day vs losing-day cohorts
// ---------------------------------------------------------------------------------------------

@Composable
private fun WinLossDaysView(trades: List<TradeEntity>, executions: List<ExecutionEntity>) {
    // All durations, scoped only by the global date filter (trades is already filtered upstream).
    val dayNet = remember(trades) { trades.groupBy { it.tradeDay() }.mapValues { sumPnl(it.value) } }
    val winDays = remember(dayNet) { dayNet.filterValues { it > ZERO }.keys }
    val lossDays = remember(dayNet) { dayNet.filterValues { it < ZERO }.keys }
    val winTrades = remember(trades, winDays) { trades.filter { it.tradeDay() in winDays } }
    val lossTrades = remember(trades, lossDays) { trades.filter { it.tradeDay() in lossDays } }
    val winExecs = remember(winTrades, executions) { execsOf(winTrades, executions) }
    val lossExecs = remember(lossTrades, executions) { execsOf(lossTrades, executions) }
    val priceById = remember(executions) { executions.associate { it.id to it.price } }
    val qtyById = remember(executions) { executions.associate { it.id to it.quantity } }
    val byHour = remember(trades) { CalculatePnlByHour()(trades) }
    val tc = LocalTradeColors.current
    var cat by remember { mutableStateOf(DetailCat.WinLoss) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            "Winning/losing classified by each day's net P&L, across all trade durations in the filtered range.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        TwoUp(
            { StatColumn("${winDays.size} Winning Days", tc.gain, winTrades, winExecs, it) },
            { StatColumn("${lossDays.size} Losing Days", tc.loss, lossTrades, lossExecs, it) },
        )
        DaysDonut(winDays.size, lossDays.size)
        CategoryToggle(cat) { cat = it }
        when (cat) {
            DetailCat.WinLoss -> {
                TwoUp(
                    { WinnersCard(CalculateWinRate()(winTrades), it, title = "Win/Loss Ratio (Winning days)") },
                    { WinnersCard(CalculateWinRate()(lossTrades), it, title = "Win/Loss Ratio (Losing days)") },
                )
                TwoUp(
                    { AveragesCard(remember(trades) { averages(trades) }, it) },
                    { TradeExpectationCard(remember(trades) { averages(trades).perTrade }, it) },
                )
            }
            DetailCat.DaysTimes -> DaysTimesSection(trades, byHour)
            DetailCat.PriceVolume -> PriceVolumeSection(trades, { priceById[it] }, { qtyById[it] })
            DetailCat.Instrument -> InstrumentSection(trades, { qtyById[it] })
        }
    }
}

/** Winning-vs-losing DAY count donut (green/red), like the small dial atop the mock's report. */
@Composable
private fun DaysDonut(winningDays: Int, losingDays: Int) {
    val tc = LocalTradeColors.current
    val total = winningDays + losingDays
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RatioRing(winningDays, total)
            Column(modifier = Modifier.padding(start = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                LegendRow(tc.gain, "Winning days", winningDays)
                LegendRow(tc.loss, "Losing days", losingDays)
            }
        }
    }
}

/** Executions that belong to a set of trades (their entry + exit fills). */
private fun execsOf(trades: List<TradeEntity>, executions: List<ExecutionEntity>): List<ExecutionEntity> {
    val ids = trades.flatMap { it.entryExecutionIds + it.exitExecutionIds }.toSet()
    return executions.filter { it.id in ids }
}

// ---------------------------------------------------------------------------------------------
// Drawdown tab: episode statistics + drawdown/volatility charts
// ---------------------------------------------------------------------------------------------

@Composable
private fun DrawdownView(trades: List<TradeEntity>) {
    val daily = remember(trades) { dailyPnlPoints(trades) }
    val values = remember(daily) { daily.map { it.value } }
    val dates = remember(daily) { daily.map { it.label } }
    val tc = LocalTradeColors.current
    val amber = Color(0xFFF59E0B) // one chart-only accent for the upper volatility band

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        DrawdownStatsCard(remember(trades) { drawdownStats(trades) })
        TwoUp(
            { Breakdown("Drawdown Increase Distribution by Day of Week", remember(trades) { drawdownIncreaseByDayOfWeek(trades) }, byCount = true, modifier = it) },
            { Breakdown("Performance by Day of Week", remember(trades) { pnlByDayOfWeek(trades) }, byCount = false, modifier = it) },
        )
        TwoUp(
            {
                val ma = remember(values) { movingAverage(values, MA_WINDOW) }
                MultiLineChartCard("P&L Moving Average", dates, listOf(LineSpec(values, tc.neutralPnl), LineSpec(ma, tc.loss)), it)
            },
            {
                val mean = remember(values) { movingAverage(values, MA_WINDOW) }
                val sd = remember(values) { rollingStd(values, MA_WINDOW) }
                val upper = mean.indices.map { i -> val m = mean[i]; val s = sd[i]; if (m == null || s == null) null else m + s }
                val lower = mean.indices.map { i -> val m = mean[i]; val s = sd[i]; if (m == null || s == null) null else m - s }
                MultiLineChartCard("P&L Volatility", dates, listOf(LineSpec(values, tc.neutralPnl), LineSpec(mean, tc.loss), LineSpec(upper, amber), LineSpec(lower, tc.gain)), it)
            },
        )
        // R-expectancy needs per-trade risk (R) we don't capture — matches the mock's empty state.
        SectionCard("Average P&L (R) Expectancy (over 20 trades)") {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("Not enough data to create this chart.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DrawdownStatsCard(s: DrawdownStats) {
    val loss = LocalTradeColors.current.loss
    val plain = MaterialTheme.colorScheme.onSurface
    fun ri(x: Double) = (x + 0.5).toInt().toString()
    // Labels match the Stats grid's voice: Title Case, no trailing colon (same StatCell renders both).
    val cols = if (LocalCompact.current) 1 else 2 // ponytail: single column keeps drawdown labels legible on phone
    val stats = listOf(
        Stat("Average Drawdown", formatMoney(ZERO.subtract(s.avgDrawdown)), loss),
        Stat("Biggest Drawdown", formatMoney(ZERO.subtract(s.biggest)), loss),
        Stat("Average Number of Days in Drawdown", ri(s.avgDays), plain),
        Stat("Number of Days in Drawdown", s.totalDays.toString(), plain),
        Stat("Average Trades in Drawdown", ri(s.avgTrades), plain),
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text("Statistics", style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.md))
            StatGrid(stats, cols)
        }
    }
}

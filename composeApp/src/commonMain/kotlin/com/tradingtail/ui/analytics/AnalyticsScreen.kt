package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.pnlColor

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, onOpenCalendar: () -> Unit, modifier: Modifier = Modifier) {
    val allTrades by vm.trades.collectAsState(initial = emptyList())
    val allExecutions by vm.executions.collectAsState(initial = emptyList())
    val now = remember { nowMillis() }
    var tab by remember { mutableStateOf(0) }
    var view by remember { mutableStateOf(ReportView.Recent) }
    var fromDate by remember { mutableStateOf<BkkDate?>(null) }
    var toDate by remember { mutableStateOf<BkkDate?>(null) }
    val tabs = listOf("Overview", "Detailed", "Win vs Loss Days", "Drawdown")

    // Global From–To day filter (by trade day = entry day); every tab reads from these filtered sets.
    val hasFilter = fromDate != null || toDate != null
    val trades = remember(allTrades, fromDate, toDate) { allTrades.filter { dayInRange(it.tradeDay(), fromDate, toDate) } }
    val executions = remember(allExecutions, fromDate, toDate) { allExecutions.filter { dayInRange(bkkDate(it.timestamp), fromDate, toDate) } }
    val win = remember(trades) { CalculateWinRate()(trades) }
    val byHour = remember(trades) { CalculatePnlByHour()(trades) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ponytail: one breakpoint — <600dp (phones) stacks every 2-up and scrolls overflowing bars.
        val compact = maxWidth < 600.dp
        CompositionLocalProvider(LocalCompact provides compact) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "Reports",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        DateRangeBar(fromDate, toDate, now) { f, t -> fromDate = f; toDate = t }
        // Underlined tab bar mirroring the mock's report tabs; only tabs we have data to back.
        // ponytail: scrollable when compact so "Win vs Loss Days" isn't clipped in a ~84dp equal slot.
        val tabSlots: @Composable () -> Unit = {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        if (compact) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 0.dp, tabs = tabSlots)
        } else {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, tabs = tabSlots)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    // ponytail: skipped the cosmetic P&L Type / View mode / Report type dropdowns — v1 is
                    // gross-only / $-value / aggregate, so they'd be dead chrome. Add if Net/% ever ships.
                    ReportViewToggle(view, onOpenCalendar) { view = it }
                    when (view) {
                        ReportView.Recent -> RecentView(trades, executions, now, hasFilter)
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

// Recent charts a rolling window; Year/Month/Day drills year → month → day. (Calendar is its own nav dest.)
private enum class ReportView { Recent, YearMonthDay }

/** The mock's Recent | Year/Month/Day | Calendar view selector under the Overview tab. */
@Composable
private fun ReportViewToggle(current: ReportView, onOpenCalendar: () -> Unit, onSelect: (ReportView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleChip("Recent", current == ReportView.Recent) { onSelect(ReportView.Recent) }
        ToggleChip("Year/Month/Day", current == ReportView.YearMonthDay) { onSelect(ReportView.YearMonthDay) }
        ToggleChip("Calendar", false, onOpenCalendar) // Calendar has its own nav destination
    }
}

/** The mock's day-range filter: a preset quick-pick + manual From/To fields; scopes every report tab. */
@Composable
private fun DateRangeBar(from: BkkDate?, to: BkkDate?, now: Long, onRange: (BkkDate?, BkkDate?) -> Unit) {
    var preset by remember { mutableStateOf<RangePreset?>(null) }
    Row(
        // ponytail: preset + From/–/To/Clear overflow a phone's width, so scroll them when compact.
        modifier = Modifier.fillMaxWidth()
            .then(if (LocalCompact.current) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetPicker(preset) { p -> preset = p; presetRange(p, now).let { onRange(it.first, it.second) } }
        DateField(from, "From") { preset = null; onRange(it, to) }
        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
        DateField(to, "To") { preset = null; onRange(from, it) }
        if (from != null || to != null) TextButton(onClick = { preset = null; onRange(null, null) }) { Text("Clear") }
    }
}

/** Dropdown of the range presets (Today, Last 7 days, YTD, …). */
@Composable
private fun PresetPicker(selected: RangePreset?, onSelect: (RangePreset) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                (selected?.label ?: "Custom range") + "  ▾",
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (p in RangePreset.entries) {
                DropdownMenuItem(text = { Text(p.label) }, onClick = { open = false; onSelect(p) })
            }
        }
    }
}

/** A pill showing a chosen day (or a placeholder) that opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: BkkDate?, placeholder: String, onPick: (BkkDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { show = true }.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            date?.let { "${it.year}-${two(it.month)}-${two(it.day)}" } ?: placeholder,
            color = if (date == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
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

/** Recent window (mock's "Recent"): 30/60/90-day daily P&L, cumulative curve, volume, win%. */
@Composable
private fun RecentView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, now: Long, hasFilter: Boolean) {
    var period by remember { mutableStateOf(30) }
    val cutoff = now - period * DAY_MS
    // When a global From–To filter is set it already scoped `trades`/`executions` — honor it instead of
    // re-clamping to the rolling window, which would hide any range further back than `period` days.
    val ft = remember(trades, period, hasFilter) { if (hasFilter) trades else trades.filter { it.entryTimestamp >= cutoff } }
    val ew = remember(executions, period, hasFilter) { if (hasFilter) executions else executions.filter { it.timestamp >= cutoff } }
    val span = if (hasFilter) "Selected range" else "$period Days"
    val tc = LocalTradeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasFilter) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PeriodToggle(period) { period = it }
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartPair(
            { m, fh -> BarChartCard("Trade Distribution by Year", remember(trades) { tradesByYear(trades) }, false, MaterialTheme.colorScheme.primary, m, fh) },
            { m, fh -> BarChartCard("Performance by Year", remember(trades) { pnlByYear(trades) }, true, tc.gain, m, fh) },
        )
        ChipRow((years.ifEmpty { listOf(nowDate.year) }).map { it.toString() }, year.toString()) { year = it.toInt() }
        ChartPair(
            { m, fh -> BarChartCard("Trade Distribution by Month", remember(trades, year) { tradesByMonthOfYear(trades, year) }, false, MaterialTheme.colorScheme.primary, m, fh) },
            { m, fh -> BarChartCard("Performance by Month", remember(trades, year) { pnlByMonthOfYear(trades, year) }, true, tc.gain, m, fh) },
        )
        ChipRow(MONTHS, MONTHS[month - 1]) { month = MONTHS.indexOf(it) + 1 }
        ChartPair(
            { m, fh -> BarChartCard("Trade Distribution by Day", remember(trades, year, month) { tradesByDayOfMonth(trades, year, month) }, false, MaterialTheme.colorScheme.primary, m, fh) },
            { m, fh -> BarChartCard("Performance by Day of Month", remember(trades, year, month) { pnlByDayOfMonth(trades, year, month) }, true, tc.gain, m, fh) },
        )
    }
}

/** A horizontally scrollable row of selectable chips (year picker, month picker). */
@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) ToggleChip(item, item == selected) { onSelect(item) }
    }
}

/** A titled line-chart card (Cumulative P&L in the Recent grid) — reuses the shared line canvas. */
@Composable
private fun LineCard(title: String, series: List<Float>, dates: List<String>, line: Color, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (series.size < 2) {
                Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChartBody(series, dates, line, chartModifier(fillHeight, 150.dp), fillToBottom = false)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detailed tab: a stats grid + category breakdowns (the mock's "Detailed" report)
// ---------------------------------------------------------------------------------------------

private enum class DetailCat(val label: String) {
    DaysTimes("Days/Times"), PriceVolume("Price/Volume"), Instrument("Instrument"),
    MarketBehavior("Market Behavior"), WinLoss("Win/Loss/Expectation"), Liquidity("Liquidity"),
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
        else -> DeferredNote(cat.label) // Market Behavior / Liquidity — need a market-data source
    }
}

/** Instrument category: per-symbol ranking + share-size buckets. The rest of the mock's instrument
 *  reports (ATR, SMA, relative volume, opening gap, day type, movement) need OHLC market data — deferred. */
@Composable
private fun InstrumentSection(trades: List<TradeEntity>, qtyById: (Long) -> BigDecimal?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TwoUp(
            { HBarChartCard("Performance by Symbol – Top 20", remember(trades) { pnlBySymbolTop(trades) }, performance = true, modifier = it) },
            { HBarChartCard("Performance by Symbol – Bottom 20", remember(trades) { pnlBySymbolBottom(trades) }, performance = true, modifier = it) },
        )
        BreakdownPair("Distribution by Volume Traded", "Performance by Volume Traded", remember(trades) { pnlByVolumeTraded(trades, qtyById) })
        DeferredNote("Relative Volume · ATR · Entry vs SMA · Opening Gap · Day Type · Movement")
    }
}

/** The mock's Days/Times · Price/Volume · … category selector (scrolls horizontally on narrow widths). */
@Composable
private fun CategoryToggle(current: DetailCat, onSelect: (DetailCat) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (c in DetailCat.entries) ToggleChip(c.label, c == current) { onSelect(c) }
    }
}

private data class Stat(val label: String, val value: String, val color: Color)

/**
 * Every summary metric for a set of trades (+ their executions), colors resolved. Shared by the
 * Detailed 3-column grid and the Win-vs-Loss two-column split. Metrics that need a market-data
 * source or heavy regression stats (per-share, MAE/MFE, K-ratio, prob. of random chance) show "—".
 */
@Composable
private fun statList(trades: List<TradeEntity>, executions: List<ExecutionEntity>): List<Stat> {
    val tc = LocalTradeColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val plain = MaterialTheme.colorScheme.onSurface
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

    fun d2(x: Double) = ((x * 100).toInt() / 100.0).toString()
    fun dur(ms: Long?) = ms?.let { formatDuration(it) } ?: na
    fun count(n: Int) = if (totalTrades == 0) "$n" else "$n (${((n * 1000.0) / totalTrades).toInt() / 10.0}%)"

    return listOf(
        Stat("Total Gain/Loss", formatMoney(win.totalPnl), pnlColor(win.totalPnl)),
        Stat("Largest Gain", formatMoney(bw.largestGain), tc.gain),
        Stat("Largest Loss", formatMoney(bw.largestLoss), tc.loss),
        Stat("Average Daily Gain/Loss", formatMoney(adp), pnlColor(adp)),
        Stat("Average Daily Volume", if (days > 0) (vol.toFloat() / days).toLong().toString() else "0", plain),
        Stat("Average Per-share Gain/Loss", na, muted),
        Stat("Average Trade Gain/Loss", formatMoney(avg.perTrade), pnlColor(avg.perTrade)),
        Stat("Average Winning Trade", formatMoney(avg.perWinner), tc.gain),
        Stat("Average Losing Trade", formatMoney(avg.perLoser), tc.loss),
        Stat("Total Number of Trades", totalTrades.toString(), plain),
        Stat("Number of Winning Trades", count(win.wins), tc.gain),
        Stat("Number of Losing Trades", count(win.losses), tc.loss),
        Stat("Average Hold Time (scratch trades)", dur(avgHoldScratch(trades)), plain),
        Stat("Average Hold Time (winning trades)", dur(avgHoldMillis(trades, true)), plain),
        Stat("Average Hold Time (losing trades)", dur(avgHoldMillis(trades, false)), plain),
        Stat("Number of Scratch Trades", count(win.breakeven), plain),
        Stat("Max Consecutive Wins", streaks.maxWins.toString(), tc.gain),
        Stat("Max Consecutive Losses", streaks.maxLosses.toString(), tc.loss),
        Stat("Trade P&L Standard Deviation", sd?.let { "$CURRENCY${d2(it)}" } ?: na, plain),
        Stat("System Quality Number (SQN)", sqnV?.let { d2(it) } ?: na, plain),
        Stat("Probability of Random Chance", na, muted),
        Stat("Kelly Percentage", kellyV?.let { if (it < 0) "< 0%" else "${(it * 100).toInt()}%" } ?: na, plain),
        Stat("K-Ratio", na, muted),
        Stat("Profit factor", pf?.let { d2(it) } ?: "∞", plain),
        Stat("Total Commissions", formatMoney(ZERO), plain),
        Stat("Total Fees", formatMoney(totalFees(executions)), plain),
        Stat("", "", plain),
        Stat("Average position MAE", na, muted),
        Stat("Average Position MFE", na, muted),
        Stat("", "", plain),
    )
}

/** The mock's big Stats table: a 3-column grid of summary metrics with hairline row separators. */
@Composable
private fun StatsCard(trades: List<TradeEntity>, executions: List<ExecutionEntity>) {
    val stats = statList(trades, executions)
    // ponytail: 3-up columns crush the long labels on a phone → 1 col when compact, full width per row.
    val cols = if (LocalCompact.current) 1 else 3
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Stats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            stats.chunked(cols).forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (s in row) StatCell(s, Modifier.weight(1f))
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One column of the Win-vs-Loss split: a dot + title, then label-over-value metric rows. */
@Composable
private fun StatColumn(title: String, dot: Color, trades: List<TradeEntity>, executions: List<ExecutionEntity>, modifier: Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(9.dp).background(dot, RoundedCornerShape(3.dp)))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            statList(trades, executions).filter { it.label.isNotEmpty() }.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${s.label}:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(s.value, color = s.color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun StatCell(s: Stat, modifier: Modifier) {
    Row(
        modifier = modifier.padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(s.value, color = s.color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Days/Times category: paired distribution (count) + performance (P&L) bars per time bucket. */
@Composable
private fun DaysTimesSection(trades: List<TradeEntity>, byHour: List<HourPnl>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BreakdownPair("Trade Distribution by Price", "Performance by Price", remember(trades) { pnlByPrice(trades, priceById) })
        BreakdownPair("Distribution by Volume Traded", "Performance by Volume Traded", remember(trades) { pnlByVolumeTraded(trades, qtyById) })
        DeferredNote("In-Trade Price Range")
    }
}

/** Win/Loss/Expectation category: ratio donut, avg win/loss, expectancy, cumulative curve + drawdown. */
@Composable
private fun WinLossExpectationSection(trades: List<TradeEntity>, win: WinRateSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trade Expectation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Text(formatMoney(expectancy), color = pnlColor(expectancy), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text("Expected P&L per trade", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** One breakdown card: horizontal bars either by trade count (distribution) or by P&L (performance). */
@Composable
private fun Breakdown(title: String, buckets: List<BucketPnl>, byCount: Boolean, modifier: Modifier = Modifier) {
    SectionCard(title, modifier) {
        if (buckets.isEmpty()) {
            Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth((count.toFloat() / max).coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        }
    }
}

/** A card standing in for a market-data-dependent report we can't compute yet (deferred in CLAUDE.md). */
@Composable
private fun DeferredNote(title: String, modifier: Modifier = Modifier) {
    SectionCard(title, modifier) {
        Text("Needs a market-data source — deferred in v1 (see CLAUDE.md).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            else -> DeferredNote(cat.label)
        }
    }
}

/** Winning-vs-losing DAY count donut (green/red), like the small dial atop the mock's report. */
@Composable
private fun DaysDonut(winningDays: Int, losingDays: Int) {
    val tc = LocalTradeColors.current
    val total = winningDays + losingDays
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val stroke = 12.dp.toPx()
                    drawArc(tc.loss, -90f, 360f, false, style = Stroke(stroke))
                    if (total > 0) drawArc(tc.gain, -90f, 360f * winningDays / total, false, style = Stroke(stroke))
                }
            }
            Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val stats = listOf(
        Stat("Average drawdown:", formatMoney(ZERO.subtract(s.avgDrawdown)), loss),
        Stat("Biggest Drawdown:", formatMoney(ZERO.subtract(s.biggest)), loss),
        Stat("Average number of days in Drawdown:", ri(s.avgDays), plain),
        Stat("Number of days in Drawdown:", s.totalDays.toString(), plain),
        Stat("Average trades in Drawdown:", ri(s.avgTrades), plain),
        Stat("", "", plain),
    )
    val cols = if (LocalCompact.current) 1 else 2 // ponytail: single column keeps drawdown labels legible on phone
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            stats.chunked(cols).forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (st in row) StatCell(st, Modifier.weight(1f))
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

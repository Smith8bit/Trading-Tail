package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.YearMonth
import com.tradingtail.common.ZERO
import com.tradingtail.common.averageMoney
import com.tradingtail.common.bkkDate
import com.tradingtail.common.daysInMonth
import com.tradingtail.common.firstWeekday
import com.tradingtail.common.formatMoney
import com.tradingtail.common.formatMoneyShort
import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculatePnlBySymbol
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.HourPnl
import com.tradingtail.domain.usecase.SymbolPnl
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.pnlColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI talks to the repo only through here; the pure aggregation usecases do the work. */
class AnalyticsViewModel(
    repo: TradeRepository,
    execRepo: ExecutionRepository,
    winRate: CalculateWinRate,
    bySymbol: CalculatePnlBySymbol,
    byHour: CalculatePnlByHour,
) {
    val winRate: Flow<WinRateSummary> = repo.allFlow().map { winRate(it) }
    val bySymbol: Flow<List<SymbolPnl>> = repo.allFlow().map { bySymbol(it) }
    val byHour: Flow<List<HourPnl>> = repo.allFlow().map { byHour(it) }
    val trades: Flow<List<TradeEntity>> = repo.allFlow() // week strip, cumulative curve, recent list
    val executions: Flow<List<ExecutionEntity>> = execRepo.allFlow() // dashboard fees/volume/entry-price
}

/**
 * At-a-glance dashboard: the same widgets as Reports, but filtered to a recent window (30/60/90 days)
 * — Reports stays the full-history deep dive. Reuses the injected usecases so win-rate/hour aggregation
 * matches the rest of the app exactly (no re-implemented counting).
 */
// Widget height unit "X" = a Performance-By card's height. Large & Medium widgets are X tall,
// Small widgets are X/2. Width (1 vs 2 columns) comes from the column weight, not here. Tune X here.
private val WIDGET_UNIT = 480.dp
private val WIDGET_HALF = 240.dp

@Composable
fun DashboardScreen(vm: AnalyticsViewModel, modifier: Modifier = Modifier) {
    val trades by vm.trades.collectAsState(initial = emptyList())
    val executions by vm.executions.collectAsState(initial = emptyList())
    val now = remember { nowMillis() }
    var period by remember { mutableStateOf(30) }
    val cutoff = now - period * DAY_MS

    val filtered = remember(trades, period) { trades.filter { it.exitTimestamp >= cutoff } }
    val execWindow = remember(executions, period) { executions.filter { it.timestamp >= cutoff } }
    // Entry-price lookup spans all fills — a trade's entry may predate the window even if its exit is inside.
    val priceById = remember(executions) { executions.associate { it.id to it.price } }

    val win = remember { CalculateWinRate() }(filtered)
    val byHour = remember { CalculatePnlByHour() }(filtered)
    val streaks = maxStreaks(filtered)
    val pf = profitFactor(filtered)
    val fees = ZERO.subtract(totalFees(execWindow)) // shown as a signed cost
    val totalTrades = win.wins + win.losses + win.breakeven

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            PeriodToggle(period) { period = it }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section map (mirrors the mock's top-to-bottom flow):
            // 1 · day strip → 2 · stat tiles → 3 · win%/equity/volume/avg charts →
            // 4 · comparison & gauges → 5 · performance breakdowns → 6 · market-data cluster.
            val tc = LocalTradeColors.current
            val dates = remember(filtered) { exitDateLabels(filtered) }
            val vol = remember(execWindow) { totalVolume(execWindow) }
            val days = remember(execWindow) { tradingDays(execWindow) }
            val pfFrac = pf?.let { (it / (it + 1)).toFloat().coerceIn(0f, 1f) } ?: 1f
            val pfText = pf?.let { "${(it * 100).toInt() / 100.0}" } ?: "∞"

            WeekStrip(remember(trades, now) { weekStrip(trades, now) }, remember(now) { weekRangeLabel(now) })

            // Perfect grid: each band is a Row exactly X tall. A band = left chart (2 cols) + two
            // 1-col slots; a slot is one Medium (fills X) or a vertical pair of Smalls (X/2 + X/2 = X).
            // Uniform column weights (2,1,1) make every band's column edges line up.
            val band = Modifier.fillMaxWidth().height(WIDGET_UNIT)
            val two = Modifier.weight(2f).fillMaxHeight()
            val one = Modifier.weight(1f).fillMaxHeight()
            val fill = Modifier.fillMaxSize()
            val gap = Arrangement.spacedBy(12.dp)
            val avgDaily = if (days > 0) (vol.toFloat() / days).toLong().toString() else "0"

            Row(band, horizontalArrangement = gap) {
                CumulativeCard(remember(filtered) { cumulativeSeries(filtered) }, dates, win.totalPnl, two, fillHeight = true)
                PairCell({ WinnersCard(win, fill) }, { AveragesCard(remember(filtered) { averages(filtered) }, fill) })
                PairCell(
                    { HoldTimeCard(remember(filtered) { avgHoldMillis(filtered, true) }, remember(filtered) { avgHoldMillis(filtered, false) }, fill) },
                    { BestWorstCard(remember(filtered) { bestWorst(filtered) }, fill) },
                )
            }
            Row(band, horizontalArrangement = gap) {
                BarChartCard("Win %", remember(filtered) { winRateByDay(filtered) }, diverging = false, barColor = tc.gain, modifier = two, fillHeight = true)
                BucketSection("Performance By Day Of Week", remember(filtered) { pnlByDayOfWeek(filtered) }, one, paged = true)
                BucketSection("Performance By Price", remember(filtered, priceById) { pnlByPrice(filtered) { priceById[it] } }, one, paged = true)
            }
            Row(band, horizontalArrangement = gap) {
                DrawdownCard(remember(filtered) { drawdownSeries(filtered) }, dates, remember(filtered) { maxDrawdown(filtered) }, two, fillHeight = true)
                BucketSection("Performance By Hour Of Day", remember(byHour) { hourWindow(byHour) }, one, paged = true)
                BucketSection("Performance By Month Of Year", remember(filtered) { pnlByMonth(filtered) }, one, paged = true)
            }
            Row(band, horizontalArrangement = gap) {
                BarChartCard("Daily Volume", remember(execWindow) { volumeByDay(execWindow) }, diverging = false, barColor = MaterialTheme.colorScheme.primary, modifier = two, fillHeight = true)
                BucketSection("Performance By Duration", remember(filtered) { pnlByDuration(filtered) }, one, paged = true)
                PairCell(
                    { KpiTile("Total Fees", formatMoney(fees), pnlColor(fees), fill) },
                    {
                        GaugeCard(
                            "Profit Factor", pfText, pfFrac,
                            fg = if (pf == null || pf >= 1.0) MaterialTheme.colorScheme.primary else tc.loss,
                            track = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = fill,
                        )
                    },
                )
            }
            Row(band, horizontalArrangement = gap) {
                BarChartCard("Average Trade P&L", remember(filtered) { avgPnlByDay(filtered) }, diverging = true, barColor = tc.gain, modifier = two, fillHeight = true)
                PairCell({ PlaceholderCard("Average MFE vs MAE", fill) }, { PlaceholderCard("Average Position MAE", fill) })
                PairCell({ PlaceholderCard("Average Position MFE", fill) }, { PlaceholderCard("Performance By Instrument Opening Gap", fill) })
            }
            Row(band, horizontalArrangement = gap) {
                PairCell({ KpiTile("Max Consecutive Wins", streaks.maxWins.toString(), modifier = fill) }, { KpiTile("Max Consecutive Losses", streaks.maxLosses.toString(), modifier = fill) })
                PairCell({ KpiTile("Total Number of Trades", totalTrades.toString(), modifier = fill) }, { KpiTile("Average Daily Volume", avgDaily, modifier = fill) })
                PairCell({ PlaceholderCard("Performance By Instrument Day Type", fill) }, { PlaceholderCard("Performance By Instrument Volume", fill) })
                PairCell({ PlaceholderCard("Performance By Symbol Atr", fill) }, { PlaceholderCard("Performance By Rvol", fill) })
            }
            Row(band, horizontalArrangement = gap) {
                PairCell({ PlaceholderCard("Performance By Instrument Movement", fill) }, { PlaceholderCard("Tag Breakdown", fill) })
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One grid slot holding two Small widgets stacked (each X/2), together filling the band's X height. */
@Composable
private fun RowScope.PairCell(top: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) { top() }
        Box(Modifier.weight(1f).fillMaxWidth()) { bottom() }
    }
}

/** 30/60/90-day window selector from the mock's dashboard header. */
@Composable
private fun PeriodToggle(period: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in listOf(30, 60, 90)) ToggleChip("$p Days", p == period) { onSelect(p) }
    }
}

/** One pill in a segmented toggle / chip row — the mock's selected-vs-idle chip styling. */
@Composable
private fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

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

    // Global From–To day filter: every tab reads from these filtered sets (and stats derived from them).
    val trades = remember(allTrades, fromDate, toDate) { allTrades.filter { dayInRange(bkkDate(it.exitTimestamp), fromDate, toDate) } }
    val executions = remember(allExecutions, fromDate, toDate) { allExecutions.filter { dayInRange(bkkDate(it.timestamp), fromDate, toDate) } }
    val win = remember(trades) { CalculateWinRate()(trades) }
    val byHour = remember(trades) { CalculatePnlByHour()(trades) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "Reports",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        DateRangeBar(fromDate, toDate, now) { f, t -> fromDate = f; toDate = t }
        // Underlined tab bar mirroring the mock's report tabs; only tabs we have data to back.
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
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
                        ReportView.Recent -> RecentView(trades, executions, now)
                        ReportView.YearMonthDay -> YearMonthDayView(trades, now)
                    }
                }
                1 -> DetailedView(trades, executions, byHour, win, now)
                2 -> WinLossDaysView(trades, executions)
                3 -> DrawdownView(trades)
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

// Quick-pick presets for the date filter, computed against "today" (Bangkok).
enum class RangePreset(val label: String) {
    Today("Today"), Yesterday("Yesterday"), Last7("Last 7 days"), Last30("Last 30 days"),
    ThisMonth("This month"), LastMonth("Last month"), Last12Months("Last 12 months"),
    LastYear("Last Year"), Ytd("YTD"),
}

/** Concrete [from, to] day bounds for a preset relative to [now]'s Bangkok day. */
fun presetRange(p: RangePreset, now: Long): Pair<BkkDate, BkkDate> {
    val today = bkkDate(now)
    fun daysAgo(n: Int) = bkkDate(now - n * DAY_MS)
    fun monthFull(ym: YearMonth) = BkkDate(ym.year, ym.month, 1) to BkkDate(ym.year, ym.month, daysInMonth(ym))
    return when (p) {
        RangePreset.Today -> today to today
        RangePreset.Yesterday -> daysAgo(1) to daysAgo(1)
        RangePreset.Last7 -> daysAgo(6) to today
        RangePreset.Last30 -> daysAgo(29) to today
        RangePreset.ThisMonth -> monthFull(YearMonth(today.year, today.month))
        RangePreset.LastMonth -> monthFull(YearMonth(today.year, today.month).prev())
        RangePreset.Last12Months -> {
            val ym = YearMonth(today.year - 1, today.month) // trailing 12 months, ending today
            BkkDate(ym.year, ym.month, minOf(today.day, daysInMonth(ym))) to today
        }
        RangePreset.LastYear -> BkkDate(today.year - 1, 1, 1) to BkkDate(today.year - 1, 12, 31)
        RangePreset.Ytd -> BkkDate(today.year, 1, 1) to today
    }
}

/** The mock's day-range filter: a preset quick-pick + manual From/To fields; scopes every report tab. */
@Composable
private fun DateRangeBar(from: BkkDate?, to: BkkDate?, now: Long, onRange: (BkkDate?, BkkDate?) -> Unit) {
    var preset by remember { mutableStateOf<RangePreset?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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

/** Two charts side by side, each filling a fixed-height band (the mock's report grid row). */
@Composable
private fun ChartRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Recent window (mock's "Recent"): 30/60/90-day daily P&L, cumulative curve, volume, win%. */
@Composable
private fun RecentView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, now: Long) {
    var period by remember { mutableStateOf(30) }
    val cutoff = now - period * DAY_MS
    val ft = remember(trades, period) { trades.filter { it.exitTimestamp >= cutoff } }
    val ew = remember(executions, period) { executions.filter { it.timestamp >= cutoff } }
    val tc = LocalTradeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PeriodToggle(period) { period = it }
        }
        ChartRow {
            BarChartCard("Gross Daily P&L ($period Days)", remember(ft) { dailyPnlPoints(ft) }, diverging = true, barColor = tc.gain, modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
            LineCard("Gross Cumulative P&L ($period Days)", remember(ft) { cumulativeSeries(ft) }, remember(ft) { exitDateLabels(ft) }, tc.gain, Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
        }
        ChartRow {
            BarChartCard("Daily Volume ($period Days)", remember(ew) { volumeByDay(ew) }, diverging = false, barColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
            BarChartCard("Win % ($period Days)", remember(ft) { winRateByDay(ft) }, diverging = false, barColor = tc.gain, modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
        }
    }
}

/** Year/Month/Day drill-down (mock's "Year/Month/Day"): year → month → day, distribution + performance. */
@Composable
private fun YearMonthDayView(trades: List<TradeEntity>, now: Long) {
    val tc = LocalTradeColors.current
    val nowDate = remember(now) { bkkDate(now) }
    val years = remember(trades) { trades.map { bkkDate(it.exitTimestamp).year }.distinct().sorted() }
    var year by remember(years) { mutableStateOf(years.lastOrNull() ?: nowDate.year) }
    var month by remember { mutableStateOf(nowDate.month) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartRow {
            BarChartCard("Trade Distribution by Year", remember(trades) { tradesByYear(trades) }, false, MaterialTheme.colorScheme.primary, Modifier.weight(1f).fillMaxHeight(), true)
            BarChartCard("Performance by Year", remember(trades) { pnlByYear(trades) }, true, tc.gain, Modifier.weight(1f).fillMaxHeight(), true)
        }
        ChipRow((years.ifEmpty { listOf(nowDate.year) }).map { it.toString() }, year.toString()) { year = it.toInt() }
        ChartRow {
            BarChartCard("Trade Distribution by Month", remember(trades, year) { tradesByMonthOfYear(trades, year) }, false, MaterialTheme.colorScheme.primary, Modifier.weight(1f).fillMaxHeight(), true)
            BarChartCard("Performance by Month", remember(trades, year) { pnlByMonthOfYear(trades, year) }, true, tc.gain, Modifier.weight(1f).fillMaxHeight(), true)
        }
        ChipRow(MONTHS, MONTHS[month - 1]) { month = MONTHS.indexOf(it) + 1 }
        ChartRow {
            BarChartCard("Trade Distribution by Day", remember(trades, year, month) { tradesByDayOfMonth(trades, year, month) }, false, MaterialTheme.colorScheme.primary, Modifier.weight(1f).fillMaxHeight(), true)
            BarChartCard("Performance by Day of Month", remember(trades, year, month) { pnlByDayOfMonth(trades, year, month) }, true, tc.gain, Modifier.weight(1f).fillMaxHeight(), true)
        }
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
private fun DetailedView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, byHour: List<HourPnl>, win: WinRateSummary, now: Long) {
    val priceById = remember(executions) { executions.associate { it.id to it.price } }
    val qtyById = remember(executions) { executions.associate { it.id to it.quantity } }
    var cat by remember { mutableStateOf(DetailCat.DaysTimes) }

    StatsCard(trades, executions)
    CategoryToggle(cat) { cat = it }
    when (cat) {
        DetailCat.DaysTimes -> DaysTimesSection(trades, byHour)
        DetailCat.PriceVolume -> PriceVolumeSection(trades, { priceById[it] }, { qtyById[it] })
        DetailCat.WinLoss -> WinLossExpectationSection(trades, win)
        else -> DeferredNote(cat.label) // Instrument / Market Behavior / Liquidity — all need market data
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
        Stat("Trade P&L Standard Deviation", sd?.let { "฿${d2(it)}" } ?: na, plain),
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Stats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            stats.chunked(3).forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (s in row) StatCell(s, Modifier.weight(1f))
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WinnersCard(win, Modifier.weight(1f))
            AveragesCard(remember(trades) { averages(trades) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TradeExpectationCard(remember(trades) { averages(trades).perTrade }, Modifier.weight(1f))
            CumulativeCard(remember(trades) { cumulativeSeries(trades) }, remember(trades) { exitDateLabels(trades) }, win.totalPnl, Modifier.weight(1f))
        }
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

@Composable
private fun BreakdownPair(distTitle: String, perfTitle: String, buckets: List<BucketPnl>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Breakdown(distTitle, buckets, byCount = true, modifier = Modifier.weight(1f))
        Breakdown(perfTitle, buckets, byCount = false, modifier = Modifier.weight(1f))
    }
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
    val dayNet = remember(trades) { trades.groupBy { bkkDate(it.exitTimestamp) }.mapValues { sumPnl(it.value) } }
    val winDays = remember(dayNet) { dayNet.filterValues { it > ZERO }.keys }
    val lossDays = remember(dayNet) { dayNet.filterValues { it < ZERO }.keys }
    val winTrades = remember(trades, winDays) { trades.filter { bkkDate(it.exitTimestamp) in winDays } }
    val lossTrades = remember(trades, lossDays) { trades.filter { bkkDate(it.exitTimestamp) in lossDays } }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatColumn("${winDays.size} Winning Days", tc.gain, winTrades, winExecs, Modifier.weight(1f))
            StatColumn("${lossDays.size} Losing Days", tc.loss, lossTrades, lossExecs, Modifier.weight(1f))
        }
        DaysDonut(winDays.size, lossDays.size)
        CategoryToggle(cat) { cat = it }
        when (cat) {
            DetailCat.WinLoss -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WinnersCard(CalculateWinRate()(winTrades), Modifier.weight(1f), title = "Win/Loss Ratio (Winning days)")
                    WinnersCard(CalculateWinRate()(lossTrades), Modifier.weight(1f), title = "Win/Loss Ratio (Losing days)")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AveragesCard(remember(trades) { averages(trades) }, Modifier.weight(1f))
                    TradeExpectationCard(remember(trades) { averages(trades).perTrade }, Modifier.weight(1f))
                }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Breakdown("Drawdown Increase Distribution by Day of Week", remember(trades) { drawdownIncreaseByDayOfWeek(trades) }, byCount = true, modifier = Modifier.weight(1f))
            Breakdown("Performance by Day of Week", remember(trades) { pnlByDayOfWeek(trades) }, byCount = false, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ma = remember(values) { movingAverage(values, MA_WINDOW) }
            MultiLineChartCard("P&L Moving Average", dates, listOf(LineSpec(values, tc.neutralPnl), LineSpec(ma, tc.loss)), Modifier.weight(1f))
            val mean = remember(values) { movingAverage(values, MA_WINDOW) }
            val sd = remember(values) { rollingStd(values, MA_WINDOW) }
            val upper = mean.indices.map { i -> val m = mean[i]; val s = sd[i]; if (m == null || s == null) null else m + s }
            val lower = mean.indices.map { i -> val m = mean[i]; val s = sd[i]; if (m == null || s == null) null else m - s }
            MultiLineChartCard("P&L Volatility", dates, listOf(LineSpec(values, tc.neutralPnl), LineSpec(mean, tc.loss), LineSpec(upper, amber), LineSpec(lower, tc.gain)), Modifier.weight(1f))
        }
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            stats.chunked(2).forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (st in row) StatCell(st, Modifier.weight(1f))
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class LineSpec(val values: List<Float?>, val color: Color)

/** Multi-series line chart (nullable points break the line) sharing one value axis — the mock's
 *  P&L Moving Average / Volatility widgets. */
@Composable
private fun MultiLineChartCard(title: String, dates: List<String>, series: List<LineSpec>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val n = series.firstOrNull()?.values?.size ?: 0
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (n < 2) {
                Text("Not enough data to create this chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    val leftPad = 58f
                    val bottomPad = 24f
                    val plotW = size.width - leftPad
                    val plotH = size.height - bottomPad
                    val all = series.flatMap { it.values.filterNotNull() }
                    val minV = minOf(0f, all.minOrNull() ?: 0f)
                    val maxV = maxOf(0f, all.maxOrNull() ?: 0f)
                    val range = (maxV - minV).let { if (it == 0f) 1f else it }
                    fun px(i: Int) = leftPad + plotW * i / (n - 1)
                    fun py(v: Float) = plotH * (1f - (v - minV) / range)

                    val steps = 4
                    for (st in 0..steps) {
                        val v = maxV - range * st / steps
                        val y = py(v)
                        drawLine(grid.copy(alpha = 0.35f), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
                        val lay = measurer.measure("฿${v.toInt()}", TextStyle(fontSize = 13.sp, color = labelColor))
                        drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
                    }
                    drawLine(grid, Offset(leftPad, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1.5f)

                    for (sp in series) {
                        val path = Path()
                        var started = false
                        for (i in 0 until n) {
                            val v = sp.values[i]
                            if (v == null) { started = false } else if (!started) { path.moveTo(px(i), py(v)); started = true } else path.lineTo(px(i), py(v))
                        }
                        drawPath(path, sp.color, style = Stroke(width = 2.dp.toPx()))
                    }

                    val xn = minOf(5, n)
                    if (xn >= 2) for (k in 0 until xn) {
                        val i = k * (n - 1) / (xn - 1)
                        val lay = measurer.measure(dates.getOrElse(i) { "" }, TextStyle(fontSize = 13.sp, color = labelColor))
                        val x = (px(i) - lay.size.width / 2f).coerceIn(leftPad, size.width - lay.size.width)
                        drawText(lay, topLeft = Offset(x, plotH + 2f))
                    }
                }
            }
        }
    }
}

/**
 * A "Performance by …" card of left-anchored magnitude bars. When [paged] (a fixed-height cell) and
 * the rows overflow, it paginates with a "‹ 1/2 ›" pager; bar scale stays constant across pages.
 */
@Composable
private fun BucketSection(title: String, buckets: List<BucketPnl>, modifier: Modifier = Modifier, paged: Boolean = false) {
    val max = maxAbs(buckets.map { it.pnl })
    val total = sumAbs(buckets.map { it.pnl })

    @Composable
    fun rows(items: List<BucketPnl>) {
        if (buckets.isEmpty()) Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (b in items) BarRow("${b.label} (${b.trades}t)", b.pnl, max, percentOf(b.pnl, total))
    }

    if (!paged) {
        SectionCard(title, modifier) { rows(buckets) }
        return
    }
    val pageCount = ((buckets.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE).coerceAtLeast(1)
    var page by remember(title, buckets.size) { mutableStateOf(0) }
    val p = page.coerceIn(0, pageCount - 1)
    val shown = buckets.subList(p * ROWS_PER_PAGE, minOf((p + 1) * ROWS_PER_PAGE, buckets.size))
    SectionCard(title, modifier, fill = true) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) { rows(shown) }
        if (pageCount > 1) PageBar(p, pageCount) { page = it }
    }
}

// ---------------------------------------------------------------------------------------------
// Widgets
// ---------------------------------------------------------------------------------------------

/** Preview's top week strip: last 7 Bangkok days, each cell a day's realized P&L + trade count. */
@Composable
private fun WeekStrip(days: List<WeekDay>, rangeLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(rangeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in days) {
                val border = if (d.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Column(
                    modifier = Modifier.weight(1f).height(84.dp)
                        .border(1.dp, border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        "${d.day} ${d.dow}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatMoneyShort(d.pnl),
                        color = pnlColor(d.pnl),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        "${d.count} trades",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, valueColor: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                value,
                color = valueColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Cumulative realized P&L as a filled line, with a dated x-axis + value gridlines (mock's hero chart). */
@Composable
private fun CumulativeCard(series: List<Float>, dates: List<String>, total: BigDecimal, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val colors = LocalTradeColors.current
    val line = if (total < ZERO) colors.loss else colors.gain
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text("Cumulative P&L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                formatMoney(total),
                color = pnlColor(total),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (series.size < 2) {
                Text("Close a few trades to plot the curve.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChartBody(series, dates, line, chartModifier(fillHeight, 150.dp), fillToBottom = true)
            }
        }
    }
}

/** Underwater plot: cumulative P&L's distance below its running peak, with the max drawdown called out. */
@Composable
private fun DrawdownCard(series: List<Float>, dates: List<String>, maxDd: BigDecimal, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val colors = LocalTradeColors.current
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text("Cumulative Drawdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val shown = ZERO.subtract(maxDd) // report the max drawdown as a signed loss figure
            Text(
                formatMoney(shown),
                color = pnlColor(shown),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (series.size < 2) {
                Text("Close a few trades to plot drawdown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChartBody(series, dates, colors.loss, chartModifier(fillHeight, 120.dp), fillToBottom = false)
            }
        }
    }
}

/** Canvas sizing shared by charts: fill the card when it's a square dashboard cell, else a fixed height. */
private fun ColumnScope.chartModifier(fillHeight: Boolean, fixed: Dp): Modifier =
    if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(fixed)

/** Shared line-chart canvas: value gridlines + ฿ y-labels on the left, sampled date labels on the x-axis. */
@Composable
private fun LineChartBody(series: List<Float>, dates: List<String>, line: Color, canvasModifier: Modifier, fillToBottom: Boolean) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = canvasModifier) {
        val leftPad = 58f
        val bottomPad = 24f
        val plotW = size.width - leftPad
        val plotH = size.height - bottomPad
        val n = series.size
        val minV = minOf(0f, series.minOrNull() ?: 0f)
        val maxV = maxOf(0f, series.maxOrNull() ?: 0f)
        val range = (maxV - minV).let { if (it == 0f) 1f else it }
        fun px(i: Int) = leftPad + plotW * i / (n - 1)
        fun py(v: Float) = plotH * (1f - (v - minV) / range)

        val steps = 4
        for (s in 0..steps) {
            val v = maxV - range * s / steps
            val y = py(v)
            drawLine(grid.copy(alpha = 0.35f), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val lay = measurer.measure("฿${v.toInt()}", TextStyle(fontSize = 13.sp, color = labelColor))
            drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
        }
        drawLine(grid, Offset(leftPad, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1.5f)

        val path = Path().apply {
            moveTo(px(0), py(series[0]))
            for (i in 1 until n) lineTo(px(i), py(series[i]))
        }
        val base = if (fillToBottom) plotH else py(0f)
        val fill = Path().apply { addPath(path); lineTo(px(n - 1), base); lineTo(px(0), base); close() }
        drawPath(fill, color = line.copy(alpha = 0.16f))
        drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))

        val xn = minOf(5, n)
        if (xn >= 2) for (k in 0 until xn) {
            val i = k * (n - 1) / (xn - 1)
            val lay = measurer.measure(dates.getOrElse(i) { "" }, TextStyle(fontSize = 13.sp, color = labelColor))
            val x = (px(i) - lay.size.width / 2f).coerceIn(leftPad, size.width - lay.size.width)
            drawText(lay, topLeft = Offset(x, plotH + 2f))
        }
    }
}

/** Per-day vertical bar chart (Win %, Daily Volume, Average Trade P&L). Diverging colors green/red by sign. */
@Composable
private fun BarChartCard(title: String, points: List<DayPoint>, diverging: Boolean, barColor: Color, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val colors = LocalTradeColors.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (points.isEmpty()) {
                Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(modifier = chartModifier(fillHeight, 150.dp)) {
                    val leftPad = 58f
                    val bottomPad = 24f
                    val plotW = size.width - leftPad
                    val plotH = size.height - bottomPad
                    val n = points.size
                    val values = points.map { it.value }
                    val minV = if (diverging) minOf(0f, values.min()) else 0f
                    val maxV = maxOf(0f, values.max()).let { if (it == 0f && minV == 0f) 1f else it }
                    val range = (maxV - minV).let { if (it == 0f) 1f else it }
                    fun py(v: Float) = plotH * (1f - (v - minV) / range)

                    val steps = 4
                    for (s in 0..steps) {
                        val v = maxV - range * s / steps
                        val y = py(v)
                        drawLine(grid.copy(alpha = 0.35f), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
                        val lay = measurer.measure(v.toInt().toString(), TextStyle(fontSize = 13.sp, color = labelColor))
                        drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
                    }
                    val zeroY = py(0f)
                    val slot = plotW / n
                    val barW = slot * 0.6f
                    for (i in 0 until n) {
                        val v = values[i]
                        val cx = leftPad + slot * (i + 0.5f)
                        val top = py(maxOf(v, 0f))
                        val bot = py(minOf(v, 0f))
                        val c = if (diverging) (if (v >= 0f) colors.gain else colors.loss) else barColor
                        drawRect(color = c, topLeft = Offset(cx - barW / 2f, minOf(top, bot)), size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(bot - top).coerceAtLeast(1f)))
                    }
                    drawLine(grid, Offset(leftPad, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)

                    val xn = minOf(6, n)
                    if (xn >= 2) for (k in 0 until xn) {
                        val i = k * (n - 1) / (xn - 1)
                        val lay = measurer.measure(points[i].label, TextStyle(fontSize = 13.sp, color = labelColor))
                        val x = (leftPad + slot * (i + 0.5f) - lay.size.width / 2f).coerceIn(leftPad, size.width - lay.size.width)
                        drawText(lay, topLeft = Offset(x, plotH + 2f))
                    }
                }
            }
        }
    }
}

/** Half-donut gauge (Profit Factor, Largest gain/loss split) — foreground arc over a track. */
@Composable
private fun GaugeCard(title: String, valueText: String, fraction: Float, fg: Color, track: Color, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxHeight().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            // Centered in the remaining (square) space: the dial with its value sitting in the arc.
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, (size.height - inset) * 2f)
                        drawArc(track, 180f, 180f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                        drawArc(fg, 180f, 180f * fraction.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    }
                    Text(valueText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/**
 * Intentional dead frame for a market-data widget. ponytail: no market-price source exists yet
 * (deferred in CLAUDE.md) — kept per explicit request to mirror the mock; wire real data when a feed lands.
 */
@Composable
private fun PlaceholderCard(title: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).height(56.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("n/a — needs market data", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Mock's "Average … Trade" widgets. Money averages — the app's one sanctioned money division. */
@Composable
private fun AveragesCard(avg: Averages, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Average Winning Trade vs Losing Trade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FigureRow("Average trade", avg.perTrade)
            FigureRow("Average winner", avg.perWinner)
            FigureRow("Average loser", avg.perLoser)
        }
    }
}

/** Mock's "Hold Time Winning vs Losing Trades" widget: mean time in the position by outcome. */
@Composable
private fun HoldTimeCard(winnerMs: Long?, loserMs: Long?, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hold Time Winning Trades vs Losing Trades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DurationRow("Winners", winnerMs)
            DurationRow("Losers", loserMs)
        }
    }
}

@Composable
private fun DurationRow(label: String, ms: Long?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            ms?.let { formatDuration(it) } ?: "—",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Winners-vs-losers donut (Canvas arcs) with the win rate in the hole. */
@Composable
private fun WinnersCard(win: WinRateSummary, modifier: Modifier = Modifier, title: String = "Winning vs Losing Trades") {
    val colors = LocalTradeColors.current
    val decided = win.wins + win.losses
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(88.dp)) {
                        val stroke = 12.dp.toPx()
                        drawArc(
                            color = colors.loss,
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            style = Stroke(width = stroke),
                        )
                        if (decided > 0) drawArc(
                            color = colors.gain,
                            startAngle = -90f,
                            sweepAngle = 360f * win.wins / decided,
                            useCenter = false,
                            style = Stroke(width = stroke),
                        )
                    }
                    Text(
                        "${(win.winRate * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendRow(colors.gain, "Winners", win.wins)
                    LegendRow(colors.loss, "Losers", win.losses)
                    LegendRow(MaterialTheme.colorScheme.onSurfaceVariant, "Break-even", win.breakeven)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(dot: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(dot, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            count.toString(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Largest single-trade gain and loss — no averaging (money division stays out of the app). */
@Composable
private fun BestWorstCard(bw: BestWorst, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Largest Gain vs Largest Loss", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FigureRow("Largest gain", bw.largestGain)
            FigureRow("Largest loss", bw.largestLoss)
        }
    }
}

@Composable
private fun FigureRow(label: String, value: BigDecimal) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(value),
            color = pnlColor(value),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * TraderVue-style performance row: label on the left, ฿ value + % on the right, and a thin
 * left-anchored magnitude bar underneath (length ∝ |P&L| over the section max, colored by sign).
 */
@Composable
private fun BarRow(label: String, pnl: BigDecimal, max: BigDecimal, percent: Float? = null) {
    val colors = LocalTradeColors.current
    val frac = fraction(pnl, max)
    val barColor = when {
        pnl > ZERO -> colors.gain
        pnl < ZERO -> colors.loss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatMoney(pnl),
                color = pnlColor(pnl),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            if (percent != null) Text(
                "  ${(percent * 10).toInt() / 10.0}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
        ) {
            Box(Modifier.fillMaxWidth(frac).height(4.dp).background(barColor, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fill) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

// Rows shown per page before a fixed-height Performance-By card paginates. Tune with WIDGET_UNIT.
private const val ROWS_PER_PAGE = 8

/** "‹ 1/2 ›" pager pinned at the bottom of an overflowing card. */
@Composable
private fun PageBar(page: Int, count: Int, onPage: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Arrow("‹", page > 0) { onPage(page - 1) }
        Text(
            "${page + 1} / $count",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Arrow("›", page < count - 1) { onPage(page + 1) }
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Pure view-state derivations (testable; see AnalyticsTest)
// ---------------------------------------------------------------------------------------------

data class WeekDay(val day: Int, val dow: String, val pnl: BigDecimal, val count: Int, val isToday: Boolean)
data class BestWorst(val largestGain: BigDecimal, val largestLoss: BigDecimal)

/** One bar row in a "Performance by …" section: a label, its trade count, and summed P&L. */
data class BucketPnl(val label: String, val trades: Int, val pnl: BigDecimal)
data class Streaks(val maxWins: Int, val maxLosses: Int)
data class DayTally(val winningDays: Int, val losingDays: Int, val breakevenDays: Int, val bestDay: BigDecimal, val worstDay: BigDecimal)

private val DOW_FULL = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private const val DAY_MS = 86_400_000L

/** Weekday label for a date, reusing the calendar's month-first-weekday helper (Bangkok has no DST). */
private fun dowLabel(date: BkkDate): Int =
    ((firstWeekday(YearMonth(date.year, date.month)) - 1 + (date.day - 1)) % 7) + 1

/** The last 7 Bangkok-local days ending today, each with its realized P&L and trade count. */
fun weekStrip(trades: List<TradeEntity>, now: Long): List<WeekDay> {
    val today = bkkDate(now)
    return (6 downTo 0).map { i ->
        val date = bkkDate(now - i * DAY_MS)
        val dayTrades = trades.filter { bkkDate(it.exitTimestamp) == date }
        WeekDay(
            day = date.day,
            dow = DOW_FULL[dowLabel(date) - 1],
            pnl = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) },
            count = dayTrades.size,
            isToday = date == today,
        )
    }
}

/** "Jun - Jul 2026" range spanning the 7-day strip (or "Jul 2026" within one month), like the mock. */
fun weekRangeLabel(now: Long): String {
    val start = bkkDate(now - 6 * DAY_MS)
    val end = bkkDate(now)
    val s = MONTHS[start.month - 1]
    val e = MONTHS[end.month - 1]
    return if (start.month == end.month) "$s ${end.year}" else "$s - $e ${end.year}"
}

/** Running total of realized P&L, trades in exit order — the cumulative curve's y-values. */
fun cumulativeSeries(trades: List<TradeEntity>): List<Float> =
    trades.sortedBy { it.exitTimestamp }
        .runningFold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
        .drop(1)
        .map { it.toFloat() }

fun bestWorst(trades: List<TradeEntity>): BestWorst = BestWorst(
    largestGain = trades.map { it.realizedPnl }.filter { it > ZERO }.maxOrNull() ?: ZERO,
    largestLoss = trades.map { it.realizedPnl }.filter { it < ZERO }.minOrNull() ?: ZERO,
)

/** Sum realized P&L over a group of trades. */
private fun sumPnl(trades: List<TradeEntity>): BigDecimal = trades.fold(ZERO) { a, t -> a.add(t.realizedPnl) }

private fun bucket(label: String, trades: List<TradeEntity>) = BucketPnl(label, trades.size, sumPnl(trades))

// Sunday-first weekday order (ISO dow 7,1..6), matching the mock and the calendar's Sun-first grid.
private val DOW_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

/** P&L grouped by Bangkok weekday of exit — all 7 days, Sun..Sat, empty days shown as ฿0. */
fun pnlByDayOfWeek(trades: List<TradeEntity>): List<BucketPnl> =
    DOW_ORDER.map { dow -> bucket(DOW_FULL[dow - 1], trades.filter { dowLabel(bkkDate(it.exitTimestamp)) == dow }) }

/** Intraday (entry and exit share a Bangkok day) vs multiday holds. */
fun pnlByDuration(trades: List<TradeEntity>): List<BucketPnl> {
    val (intraday, multiday) = trades.partition { bkkDate(it.entryTimestamp) == bkkDate(it.exitTimestamp) }
    return listOf(bucket("Intraday", intraday), bucket("Multiday", multiday)).filter { it.trades > 0 }
}

/** P&L grouped by Bangkok calendar month of exit — all 12 months, Jan..Dec, empty months as ฿0. */
fun pnlByMonth(trades: List<TradeEntity>): List<BucketPnl> {
    val byMonth = trades.groupBy { bkkDate(it.exitTimestamp).month }
    return (1..12).map { m -> bucket(MONTHS[m - 1], byMonth[m] ?: emptyList()) }
}

// Bangkok-local hour window shown on the Hour chart: pre-market (06:00) through after-market (20:00).
const val HOUR_START = 6
const val HOUR_END = 20

/** [byHour] laid onto the fixed pre-market→after-market hour axis; hours with no trades show as ฿0. */
fun hourWindow(byHour: List<HourPnl>): List<BucketPnl> {
    val m = byHour.associateBy { it.hour }
    return (HOUR_START..HOUR_END).map { h ->
        val hp = m[h]
        BucketPnl(h.toString().padStart(2, '0') + ":00", hp?.trades ?: 0, hp?.pnl ?: ZERO)
    }
}

/** Total fees across executions in scope. */
fun totalFees(executions: List<ExecutionEntity>): BigDecimal = executions.fold(ZERO) { a, e -> a.add(e.fees) }

/** Total shares/contracts traded (summed execution quantity). */
fun totalVolume(executions: List<ExecutionEntity>): BigDecimal = executions.fold(ZERO) { a, e -> a.add(e.quantity) }

/** Distinct Bangkok days that had at least one fill — the denominator for average daily volume. */
fun tradingDays(executions: List<ExecutionEntity>): Int = executions.map { bkkDate(it.timestamp) }.distinct().size

// Entry-price buckets from the mock (THB). Upper bound exclusive; anything ≥ last falls in the catch-all.
private val PRICE_BUCKETS = listOf(
    "< ฿2" to 2f, "฿2–4.99" to 5f, "฿5–9.99" to 10f, "฿10–19.99" to 20f,
    "฿20–49.99" to 50f, "฿50–99.99" to 100f, "฿100–199.99" to 200f, "฿200–499.99" to 500f,
)
private const val PRICE_TOP = "฿500+"

/**
 * P&L bucketed by a trade's entry price (first entry fill), via a lookup from execution id → price.
 * All price brackets are shown (empty ones as ฿0), matching the mock's fixed price ladder.
 */
fun pnlByPrice(trades: List<TradeEntity>, priceById: (Long) -> BigDecimal?): List<BucketPnl> {
    fun label(p: Float): String = PRICE_BUCKETS.firstOrNull { p < it.second }?.first ?: PRICE_TOP
    val groups = trades.groupBy { t ->
        t.entryExecutionIds.firstOrNull()?.let { priceById(it) }?.let { label(it.toFloat()) }
    }
    val order = PRICE_BUCKETS.map { it.first } + PRICE_TOP
    return order.map { lab -> bucket(lab, groups[lab] ?: emptyList()) }
}

// Share-size buckets from the mock. Upper bound exclusive; anything ≥ last falls in the catch-all.
private val VOLUME_BUCKETS = listOf(
    "10 - 19" to 20f, "20 - 49" to 50f, "50 - 99" to 100f, "100 - 500" to 501f,
    "500 - 999" to 1000f, "1,000 - 1,999" to 2000f, "2,000 - 2,999" to 3000f, "3,000 - 4,999" to 5000f,
)
private const val VOLUME_TOP = "5,000+"

/** Shares in a trade = summed quantity of its entry fills. */
private fun tradeShares(t: TradeEntity, qtyById: (Long) -> BigDecimal?): Float =
    t.entryExecutionIds.mapNotNull { qtyById(it) }.fold(ZERO) { a, q -> a.add(q) }.toFloat()

/** P&L bucketed by share size (summed entry-fill quantity); fixed ladder, empties shown as ฿0. */
fun pnlByVolumeTraded(trades: List<TradeEntity>, qtyById: (Long) -> BigDecimal?): List<BucketPnl> {
    fun label(s: Float): String = VOLUME_BUCKETS.firstOrNull { s < it.second }?.first ?: VOLUME_TOP
    val groups = trades.groupBy { label(tradeShares(it, qtyById)) }
    val order = VOLUME_BUCKETS.map { it.first } + VOLUME_TOP
    return order.map { lab -> bucket(lab, groups[lab] ?: emptyList()) }
}

/** Average of per-Bangkok-day summed P&L (money average; empty → ฿0). */
fun avgDailyPnl(trades: List<TradeEntity>): BigDecimal =
    averageMoney(trades.groupBy { bkkDate(it.exitTimestamp) }.values.map { sumPnl(it) })

/** Average hold time (ms) of break-even (scratch) trades, or null when there are none. */
fun avgHoldScratch(trades: List<TradeEntity>): Long? {
    val sel = trades.filter { it.realizedPnl == ZERO }
    return if (sel.isEmpty()) null else sel.map { it.exitTimestamp - it.entryTimestamp }.average().toLong()
}

/**
 * Population standard deviation of trade P&L (Double), or null with < 2 trades.
 * ponytail: a dimensionless spread statistic, so Double is fine — same last-step float call the app
 * already makes for profit factor / win rate; the money itself stays BigDecimal up to here.
 */
fun pnlStdDev(trades: List<TradeEntity>): Double? {
    if (trades.size < 2) return null
    val xs = trades.map { it.realizedPnl.toFloat().toDouble() }
    val mean = xs.average()
    return kotlin.math.sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
}

/** System Quality Number: √N · mean / stdev. Null when stdev is 0 or < 2 trades. */
fun sqn(trades: List<TradeEntity>): Double? {
    val sd = pnlStdDev(trades) ?: return null
    if (sd == 0.0) return null
    val mean = trades.map { it.realizedPnl.toFloat().toDouble() }.average()
    return kotlin.math.sqrt(trades.size.toDouble()) * mean / sd
}

/** Kelly fraction: W − (1−W)/R, R = avgWin/|avgLoss|. Null when there are no losers (R undefined). */
fun kelly(trades: List<TradeEntity>): Double? {
    val avg = averages(trades)
    val avgLossAbs = -avg.perLoser.toFloat().toDouble()
    val decided = trades.count { it.realizedPnl > ZERO } + trades.count { it.realizedPnl < ZERO }
    if (avgLossAbs <= 0.0 || decided == 0) return null
    val w = trades.count { it.realizedPnl > ZERO }.toDouble() / decided
    val avgWin = avg.perWinner.toFloat().toDouble()
    return if (avgWin <= 0.0) -1.0 else w - (1 - w) / (avgWin / avgLossAbs) // no winners → deeply negative edge
}

data class DrawdownStats(
    val avgDrawdown: BigDecimal, val biggest: BigDecimal,
    val avgDays: Double, val totalDays: Int, val avgTrades: Double,
)

/**
 * Drawdown episodes over the equity curve (trades in exit order): an episode runs from the first
 * trade that drops cumulative P&L below its running peak until cum recovers to a new peak. Reports
 * the mean & worst trough depth (positive magnitudes) plus mean/total days and mean trades per episode.
 */
fun drawdownStats(trades: List<TradeEntity>): DrawdownStats {
    var cum = ZERO; var peak = ZERO; var trough = ZERO; var count = 0; var active = false
    var days = mutableSetOf<BkkDate>()
    val depths = mutableListOf<BigDecimal>(); val dayCounts = mutableListOf<Int>(); val tradeCounts = mutableListOf<Int>()
    fun close() { depths.add(peak.subtract(trough)); dayCounts.add(days.size); tradeCounts.add(count); active = false }
    for (t in trades.sortedBy { it.exitTimestamp }) {
        cum = cum.add(t.realizedPnl)
        if (cum >= peak) {
            if (active) close()
            peak = cum
        } else {
            if (!active) { active = true; trough = cum; days = mutableSetOf(); count = 0 }
            if (cum < trough) trough = cum
            days.add(bkkDate(t.exitTimestamp)); count++
        }
    }
    if (active) close() // episode still open at the end still counts
    return DrawdownStats(
        avgDrawdown = averageMoney(depths),
        biggest = depths.maxOrNull() ?: ZERO,
        avgDays = if (dayCounts.isEmpty()) 0.0 else dayCounts.average(),
        totalDays = dayCounts.sum(),
        avgTrades = if (tradeCounts.isEmpty()) 0.0 else tradeCounts.average(),
    )
}

/** Count, per Bangkok weekday (Sun..Sat), of trades that deepened the running drawdown. */
fun drawdownIncreaseByDayOfWeek(trades: List<TradeEntity>): List<BucketPnl> {
    var cum = ZERO; var peak = ZERO; var prevDd = ZERO
    val counts = IntArray(8) // index by ISO dow 1..7
    for (t in trades.sortedBy { it.exitTimestamp }) {
        cum = cum.add(t.realizedPnl)
        if (cum > peak) peak = cum
        val dd = peak.subtract(cum)
        if (dd > prevDd) counts[dowLabel(bkkDate(t.exitTimestamp))]++
        prevDd = dd
    }
    return DOW_ORDER.map { dow -> BucketPnl(DOW_FULL[dow - 1], counts[dow], ZERO) }
}

// Rolling window (in daily points) for the P&L moving-average / volatility overlays.
private const val MA_WINDOW = 5

/** Trailing simple moving average; null until [window] points are available. */
fun movingAverage(xs: List<Float>, window: Int): List<Float?> =
    xs.indices.map { i -> if (i + 1 < window) null else xs.subList(i + 1 - window, i + 1).average().toFloat() }

/** Trailing population standard deviation over [window] points; null until the window fills. */
fun rollingStd(xs: List<Float>, window: Int): List<Float?> =
    xs.indices.map { i ->
        if (i + 1 < window) null else {
            val w = xs.subList(i + 1 - window, i + 1).map { it.toDouble() }
            val m = w.average()
            kotlin.math.sqrt(w.sumOf { (it - m) * (it - m) } / w.size).toFloat()
        }
    }

/** True if [d] falls within [from]..[to] inclusive; a null bound is open-ended on that side. */
fun dayInRange(d: BkkDate, from: BkkDate?, to: BkkDate?): Boolean {
    fun ord(x: BkkDate) = x.year * 10000 + x.month * 100 + x.day
    val o = ord(d)
    return (from == null || o >= ord(from)) && (to == null || o <= ord(to))
}

/** A single vertical bar in a per-day chart: its axis label and height value. */
data class DayPoint(val label: String, val value: Float)

private fun two(n: Int) = n.toString().padStart(2, '0')
private fun md(d: BkkDate) = "${two(d.month)}-${two(d.day)}"

private fun <T> byDaySorted(items: List<T>, day: (T) -> BkkDate): List<Pair<BkkDate, List<T>>> =
    items.groupBy(day).entries.sortedWith(compareBy({ it.key.year }, { it.key.month }, { it.key.day })).map { it.toPair() }

/** Per-day win rate (0..100) over the period — the mock's Win % bar chart. */
fun winRateByDay(trades: List<TradeEntity>): List<DayPoint> =
    byDaySorted(trades) { bkkDate(it.exitTimestamp) }.map { (d, g) ->
        val decided = g.count { it.realizedPnl != ZERO }
        DayPoint(md(d), if (decided == 0) 0f else 100f * g.count { it.realizedPnl > ZERO } / decided)
    }

/** Per-day traded volume (shares) — the mock's Daily Volume bar chart. */
fun volumeByDay(executions: List<ExecutionEntity>): List<DayPoint> =
    byDaySorted(executions) { bkkDate(it.timestamp) }.map { (d, g) -> DayPoint(md(d), g.fold(ZERO) { a, e -> a.add(e.quantity) }.toFloat()) }

/** Per-day mean trade P&L — the mock's Average Trade P&L diverging bar chart. */
fun avgPnlByDay(trades: List<TradeEntity>): List<DayPoint> =
    byDaySorted(trades) { bkkDate(it.exitTimestamp) }.map { (d, g) -> DayPoint(md(d), averageMoney(g.map { it.realizedPnl }).toFloat()) }

/** Per-day summed realized P&L — the mock's Gross Daily P&L diverging bar chart. */
fun dailyPnlPoints(trades: List<TradeEntity>): List<DayPoint> =
    byDaySorted(trades) { bkkDate(it.exitTimestamp) }.map { (d, g) -> DayPoint(md(d), sumPnl(g).toFloat()) }

/** Trade count per calendar year of exit, ascending (mock's Trade Distribution by Year). */
fun tradesByYear(trades: List<TradeEntity>): List<DayPoint> =
    trades.groupBy { bkkDate(it.exitTimestamp).year }.entries.sortedBy { it.key }
        .map { (y, g) -> DayPoint(y.toString(), g.size.toFloat()) }

/** Summed realized P&L per calendar year of exit, ascending (mock's Performance by Year). */
fun pnlByYear(trades: List<TradeEntity>): List<DayPoint> =
    trades.groupBy { bkkDate(it.exitTimestamp).year }.entries.sortedBy { it.key }
        .map { (y, g) -> DayPoint(y.toString(), sumPnl(g).toFloat()) }

/** Trade count for each month Jan..Dec of [year]; empty months show as 0 (full ladder, like the mock). */
fun tradesByMonthOfYear(trades: List<TradeEntity>, year: Int): List<DayPoint> {
    val inYear = trades.filter { bkkDate(it.exitTimestamp).year == year }
    return (1..12).map { m -> DayPoint(MONTHS[m - 1], inYear.count { bkkDate(it.exitTimestamp).month == m }.toFloat()) }
}

/** Summed P&L for each month Jan..Dec of [year]. */
fun pnlByMonthOfYear(trades: List<TradeEntity>, year: Int): List<DayPoint> {
    val inYear = trades.filter { bkkDate(it.exitTimestamp).year == year }
    return (1..12).map { m -> DayPoint(MONTHS[m - 1], sumPnl(inYear.filter { bkkDate(it.exitTimestamp).month == m }).toFloat()) }
}

/** Trade count per day 1..daysInMonth of [year]/[month]; empty days show as 0. */
fun tradesByDayOfMonth(trades: List<TradeEntity>, year: Int, month: Int): List<DayPoint> {
    val inMonth = trades.filter { val d = bkkDate(it.exitTimestamp); d.year == year && d.month == month }
    return (1..daysInMonth(YearMonth(year, month))).map { day -> DayPoint(day.toString(), inMonth.count { bkkDate(it.exitTimestamp).day == day }.toFloat()) }
}

/** Summed P&L per day 1..daysInMonth of [year]/[month]. */
fun pnlByDayOfMonth(trades: List<TradeEntity>, year: Int, month: Int): List<DayPoint> {
    val inMonth = trades.filter { val d = bkkDate(it.exitTimestamp); d.year == year && d.month == month }
    return (1..daysInMonth(YearMonth(year, month))).map { day -> DayPoint(day.toString(), sumPnl(inMonth.filter { bkkDate(it.exitTimestamp).day == day }).toFloat()) }
}

/** Exit-date labels parallel to [cumulativeSeries]/[drawdownSeries] (both sort by exit). */
fun exitDateLabels(trades: List<TradeEntity>): List<String> =
    trades.sortedBy { it.exitTimestamp }.map { md(bkkDate(it.exitTimestamp)) }

/** Sum of magnitudes — denominator for a bar's % contribution. */
private fun sumAbs(pnls: List<BigDecimal>): BigDecimal = pnls.fold(ZERO) { a, p -> a.add(abs(p)) }

private fun percentOf(pnl: BigDecimal, totalAbs: BigDecimal): Float =
    if (totalAbs <= ZERO) 0f else abs(pnl).toFloat() / totalAbs.toFloat() * 100f

/**
 * Gross profit ÷ gross loss. Null when there are no losing trades (an "infinite" factor).
 * ponytail: a dimensionless ratio, not a money value — float division is fine here (same call the
 * project already makes for win-rate), money itself stays BigDecimal right up to this last step.
 */
fun profitFactor(trades: List<TradeEntity>): Double? {
    val gross = sumPnl(trades.filter { it.realizedPnl > ZERO })
    val lossAbs = abs(sumPnl(trades.filter { it.realizedPnl < ZERO }))
    return if (lossAbs <= ZERO) null else gross.toFloat().toDouble() / lossAbs.toFloat()
}

data class Averages(val perTrade: BigDecimal, val perWinner: BigDecimal, val perLoser: BigDecimal)

/** Mean P&L across all trades, and separately across winners / losers (money division; see averageMoney). */
fun averages(trades: List<TradeEntity>): Averages = Averages(
    perTrade = averageMoney(trades.map { it.realizedPnl }),
    perWinner = averageMoney(trades.map { it.realizedPnl }.filter { it > ZERO }),
    perLoser = averageMoney(trades.map { it.realizedPnl }.filter { it < ZERO }),
)

/** Average hold time (exit − entry, millis) for winners or losers; null when that bucket is empty. */
fun avgHoldMillis(trades: List<TradeEntity>, winners: Boolean): Long? {
    val sel = trades.filter { if (winners) it.realizedPnl > ZERO else it.realizedPnl < ZERO }
    return if (sel.isEmpty()) null else sel.map { it.exitTimestamp - it.entryTimestamp }.average().toLong()
}

/** Compact human hold-time: "45m" / "2h 15m" / "3d 4h". */
fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 60 -> "${m}m"
        m < 1_440 -> "${m / 60}h ${m % 60}m"
        else -> "${m / 1_440}d ${(m % 1_440) / 60}h"
    }
}

/** Largest peak-to-trough decline of cumulative realized P&L, returned as a positive magnitude. */
fun maxDrawdown(trades: List<TradeEntity>): BigDecimal {
    var cum = ZERO; var peak = ZERO; var maxDd = ZERO
    for (t in trades.sortedBy { it.exitTimestamp }) {
        cum = cum.add(t.realizedPnl)
        if (cum > peak) peak = cum
        val dd = peak.subtract(cum)
        if (dd > maxDd) maxDd = dd
    }
    return maxDd
}

/** Underwater curve: how far cumulative P&L sits below its running peak at each close (values ≤ 0). */
fun drawdownSeries(trades: List<TradeEntity>): List<Float> {
    var cum = ZERO; var peak = ZERO
    return trades.sortedBy { it.exitTimestamp }.map { t ->
        cum = cum.add(t.realizedPnl)
        if (cum > peak) peak = cum
        cum.subtract(peak).toFloat()
    }
}

/** Win/loss/break-even tally at the day level (a day is won if its net realized P&L is positive). */
fun dayTally(trades: List<TradeEntity>): DayTally {
    val byDay = trades.groupBy { bkkDate(it.exitTimestamp) }
        .mapValues { (_, g) -> sumPnl(g) }.values
    var w = 0; var l = 0; var b = 0
    for (p in byDay) when { p > ZERO -> w++; p < ZERO -> l++; else -> b++ }
    return DayTally(w, l, b, byDay.filter { it > ZERO }.maxOrNull() ?: ZERO, byDay.filter { it < ZERO }.minOrNull() ?: ZERO)
}

/** Longest run of consecutive winners and losers in exit order; a break-even trade resets both. */
fun maxStreaks(trades: List<TradeEntity>): Streaks {
    var maxW = 0; var maxL = 0; var w = 0; var l = 0
    for (t in trades.sortedBy { it.exitTimestamp }) when {
        t.realizedPnl > ZERO -> { w++; l = 0; if (w > maxW) maxW = w }
        t.realizedPnl < ZERO -> { l++; w = 0; if (l > maxL) maxL = l }
        else -> { w = 0; l = 0 }
    }
    return Streaks(maxW, maxL)
}

private fun abs(x: BigDecimal): BigDecimal = if (x < ZERO) ZERO.subtract(x) else x

private fun maxAbs(pnls: List<BigDecimal>): BigDecimal =
    pnls.fold(ZERO) { m, p -> abs(p).let { if (it > m) it else m } }

private fun fraction(pnl: BigDecimal, max: BigDecimal): Float =
    if (max <= ZERO) 0f else (abs(pnl).toFloat() / max.toFloat()).coerceIn(0f, 1f)

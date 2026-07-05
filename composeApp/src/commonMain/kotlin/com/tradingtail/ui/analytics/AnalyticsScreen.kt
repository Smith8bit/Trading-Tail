package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.tradingtail.common.firstWeekday
import com.tradingtail.common.formatBangkok
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
        for (p in listOf(30, 60, 90)) {
            val selected = p == period
            val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
                    .clickable { onSelect(p) }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("$p Days", color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, modifier: Modifier = Modifier) {
    val win by vm.winRate.collectAsState(initial = WinRateSummary(0, 0, 0, 0.0, ZERO))
    val bySymbol by vm.bySymbol.collectAsState(initial = emptyList())
    val byHour by vm.byHour.collectAsState(initial = emptyList())
    val trades by vm.trades.collectAsState(initial = emptyList())
    val now = remember { nowMillis() }
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Drawdown", "Win vs Loss Days")

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "Reports",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
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
                    WeekStrip(remember(trades, now) { weekStrip(trades, now) }, remember(now) { weekRangeLabel(now) })
                    KpiGrid(win)
                    SecondaryKpis(remember(trades) { profitFactor(trades) }, remember(trades) { maxStreaks(trades) })
                    CumulativeCard(remember(trades) { cumulativeSeries(trades) }, remember(trades) { exitDateLabels(trades) }, win.totalPnl)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WinnersCard(win, Modifier.weight(1f))
                        BestWorstCard(remember(trades) { bestWorst(trades) }, Modifier.weight(1f))
                    }
                    SectionCard("P&L by Symbol") {
                        if (bySymbol.isEmpty()) Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val max = maxAbs(bySymbol.map { it.pnl })
                        val total = sumAbs(bySymbol.map { it.pnl })
                        for (s in bySymbol) BarRow("${s.symbol} (${s.trades}t)", s.pnl, max, percentOf(s.pnl, total))
                    }
                    RecentTradesCard(remember(trades) { recentTrades(trades) })
                }
                1 -> {
                    CumulativeCard(remember(trades) { cumulativeSeries(trades) }, remember(trades) { exitDateLabels(trades) }, win.totalPnl)
                    DrawdownCard(remember(trades) { drawdownSeries(trades) }, remember(trades) { exitDateLabels(trades) }, remember(trades) { maxDrawdown(trades) })
                }
                2 -> {
                    WinLossDaysCard(remember(trades) { dayTally(trades) })
                    BucketSection("P&L by Day of Week", remember(trades) { pnlByDayOfWeek(trades) })
                    SectionCard("P&L by Hour (Bangkok)") {
                        if (byHour.isEmpty()) Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val max = maxAbs(byHour.map { it.pnl })
                        val total = sumAbs(byHour.map { it.pnl })
                        for (h in byHour) {
                            BarRow(h.hour.toString().padStart(2, '0') + ":00 (${h.trades}t)", h.pnl, max, percentOf(h.pnl, total))
                        }
                    }
                    BucketSection("P&L by Duration", remember(trades) { pnlByDuration(trades) })
                    BucketSection("P&L by Month", remember(trades) { pnlByMonth(trades) })
                }
            }
        }
    }
}

/** Second KPI row from the mock: profit factor + longest win/loss streaks. */
@Composable
private fun SecondaryKpis(profitFactor: Double?, streaks: Streaks) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiTile("Profit factor", profitFactor?.let { "${(it * 100).toInt() / 100.0}" } ?: "∞", modifier = Modifier.weight(1f))
        KpiTile("Win streak", streaks.maxWins.toString(), modifier = Modifier.weight(1f))
        KpiTile("Loss streak", streaks.maxLosses.toString(), modifier = Modifier.weight(1f))
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

/** Preview's KPI row: four summary tiles, figures leading, built from data already on hand. */
@Composable
private fun KpiGrid(win: WinRateSummary) {
    val trades = win.wins + win.losses + win.breakeven
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile("Net P&L", formatMoney(win.totalPnl), pnlColor(win.totalPnl), Modifier.weight(1f))
            KpiTile("Win rate", "${(win.winRate * 100).toInt()}%", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile("Trades", trades.toString(), modifier = Modifier.weight(1f))
            KpiTile("Record", "${win.wins}W · ${win.losses}L", modifier = Modifier.weight(1f))
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
            val lay = measurer.measure("฿${v.toInt()}", TextStyle(fontSize = 11.sp, color = labelColor))
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
            val lay = measurer.measure(dates.getOrElse(i) { "" }, TextStyle(fontSize = 11.sp, color = labelColor))
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
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
                        val lay = measurer.measure(v.toInt().toString(), TextStyle(fontSize = 11.sp, color = labelColor))
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
                        val lay = measurer.measure(points[i].label, TextStyle(fontSize = 11.sp, color = labelColor))
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

/** Day-level win/loss split (the "Win vs Loss Days" report): counts of green/red days + extremes. */
@Composable
private fun WinLossDaysCard(tally: DayTally) {
    val colors = LocalTradeColors.current
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Winning vs losing days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LegendRow(colors.gain, "Winning days", tally.winningDays)
            LegendRow(colors.loss, "Losing days", tally.losingDays)
            LegendRow(MaterialTheme.colorScheme.onSurfaceVariant, "Break-even days", tally.breakevenDays)
            FigureRow("Best day", tally.bestDay)
            FigureRow("Worst day", tally.worstDay)
        }
    }
}

/** Winners-vs-losers donut (Canvas arcs) with the win rate in the hole. */
@Composable
private fun WinnersCard(win: WinRateSummary, modifier: Modifier = Modifier) {
    val colors = LocalTradeColors.current
    val decided = win.wins + win.losses
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Winning vs Losing Trades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

/** Preview's recent-trades table (symbol · exit time · P&L). Prices live on executions — omitted here. */
@Composable
private fun RecentTradesCard(recent: List<TradeEntity>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Recent trades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (recent.isEmpty()) {
                Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            for (t in recent) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(t.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatBangkok(t.exitTimestamp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        formatMoney(t.realizedPnl),
                        color = pnlColor(t.realizedPnl),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
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

fun recentTrades(trades: List<TradeEntity>): List<TradeEntity> =
    trades.sortedByDescending { it.exitTimestamp }.take(6)

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

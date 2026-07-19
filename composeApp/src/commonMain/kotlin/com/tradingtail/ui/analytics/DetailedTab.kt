package com.tradingtail.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.CURRENCY
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.HourPnl
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import kotlin.math.roundToInt


// ---------------------------------------------------------------------------------------------
// Detailed tab: a stats grid + category breakdowns (the mock's "Detailed" report)
// ---------------------------------------------------------------------------------------------

// Four categories, every one of which plots real data. "Market Behavior" and "Liquidity" used to sit
// here and resolve to nothing but a "needs a market-data source" card — two of six segments that were
// dead ends, on a control that scrolls at 411dp. Add them back alongside the feed that feeds them.
internal enum class DetailCat(val label: String) {
    DaysTimes("Days/Times"), PriceVolume("Price/Volume"), Instrument("Instrument"),
    WinLoss("Win/Loss/Expectation"),
}

@Composable
internal fun DetailedView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, byHour: List<HourPnl>, win: WinRateSummary) {
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
internal fun InstrumentSection(trades: List<TradeEntity>, qtyById: (Long) -> BigDecimal?) {
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
internal fun CategoryToggle(current: DetailCat, onSelect: (DetailCat) -> Unit) {
    val cats = DetailCat.entries.toList()
    SegmentedControl(cats.map { it.label }, cats.indexOf(current)) { onSelect(cats[it]) }
}

internal data class Stat(val label: String, val value: String, val color: Color)

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
internal fun StatColumn(title: String, dot: Color, trades: List<TradeEntity>, executions: List<ExecutionEntity>, modifier: Modifier) {
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
internal fun StatGrid(stats: List<Stat>, cols: Int) {
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
internal fun DaysTimesSection(trades: List<TradeEntity>, byHour: List<HourPnl>) {
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
internal fun PriceVolumeSection(trades: List<TradeEntity>, priceById: (Long) -> BigDecimal?, qtyById: (Long) -> BigDecimal?) {
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
internal fun TradeExpectationCard(expectancy: BigDecimal, modifier: Modifier = Modifier) {
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
internal fun Breakdown(title: String, buckets: List<BucketPnl>, byCount: Boolean, modifier: Modifier = Modifier) {
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

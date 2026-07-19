package com.tradingtail.ui.analytics

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.common.nowMillis
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.ui.theme.FAB_CLEARANCE
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor

/**
 * At-a-glance dashboard over a recent window (30/60/90 days) — Reports stays the full-history deep
 * dive. Reuses the injected usecases so win-rate/hour aggregation matches the rest of the app exactly
 * (no re-implemented counting).
 *
 * The two layouts answer different questions on purpose. Wide is the bento grid: the recent window
 * rendered at Reports' depth, because a 1440dp window can hold it. Compact answers only "how am I
 * doing right now" — week strip, equity curve, outcome pair, dense figures — and hands every
 * breakdown off to Reports rather than restating it in a second 18-card column.
 */
// Widget height unit "X" = a Performance-By card's height, sized so 8 bucket rows + title + pager
// fill it with no dead glass (~392dp of content). Every wide band is a Row exactly X tall.
private val WIDGET_UNIT = 420.dp

@Composable
fun DashboardScreen(vm: AnalyticsViewModel, modifier: Modifier = Modifier) {
    // initial = null, not empty: "no data yet" and "haven't asked the database yet" are different
    // facts. Conflating them made cold start render $0.00 across the board before Room's first
    // emission — a figure the user could read and believe. Nothing renders during the gap.
    val tradesOrNull by vm.trades.collectAsState(initial = null)
    val executionsOrNull by vm.executions.collectAsState(initial = null)
    val trades = tradesOrNull ?: return
    val executions = executionsOrNull ?: return
    val now = remember { nowMillis() }
    var period by remember { mutableStateOf(30) }
    val cutoff = now - period * DAY_MS

    val filtered = remember(trades, period) { trades.filter { it.exitTimestamp >= cutoff } }
    val execWindow = remember(executions, period) { executions.filter { it.timestamp >= cutoff } }
    // Entry-price lookup spans all fills — a trade's entry may predate the window even if its exit is inside.
    val priceById = remember(executions) { executions.associate { it.id to it.price } }

    // Each of these is a full pass over the window, so they're keyed on the data, not left bare.
    // `remember { CalculateWinRate() }(filtered)` — the previous shape — cached the *usecase object*
    // (which is stateless and free to construct) and re-ran the pass it wraps on every recomposition.
    // It read like caching while caching the one thing that didn't cost anything.
    val win = remember(filtered) { CalculateWinRate()(filtered) }
    val byHour = remember(filtered) { CalculatePnlByHour()(filtered) }
    val streaks = remember(filtered) { maxStreaks(filtered) }
    val pf = remember(filtered) { profitFactor(filtered) }
    val fees = remember(execWindow) { ZERO.subtract(totalFees(execWindow)) } // shown as a signed cost
    val totalTrades = win.wins + win.losses + win.breakeven

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ponytail: one breakpoint — <600dp (phones) collapses the fixed 480dp bands to a single column.
        val compact = maxWidth < 600.dp
        CompositionLocalProvider(LocalCompact provides compact) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Space.md)) {
        if (compact) {
            // Stacked header — the period toggle needs the full width on a phone, and a trailing toggle
            // beside the title would crush both. (It also used to dodge the floating pill, which now
            // lives bottom-left.)
            Column(modifier = Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.sm)) {
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Space.sm))
                PeriodToggle(period) { period = it }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                PeriodToggle(period) { period = it }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = Space.xs, bottom = if (compact) FAB_CLEARANCE else Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Section map: day strip → equity + outcome pairs → chart-and-bucket bands →
            // dense stats + gauge → the closing chart → planned-widget strip (natural height).
            val tc = LocalTradeColors.current
            val dates = remember(filtered) { exitDateLabels(filtered) }
            val vol = remember(execWindow) { totalVolume(execWindow) }
            val days = remember(execWindow) { tradingDays(execWindow) }

            WeekStrip(remember(trades, now) { weekStrip(trades, now) }, remember(now) { weekRangeLabel(now) })

            // Perfect grid (wide): each band is a Row exactly X tall. A band = left chart (2 cols) +
            // two 1-col slots; a slot is one Medium (fills X) or a vertical pair of Smalls. When compact,
            // Band drops the Row and every slot stacks full-width at natural height (see Band/PairCell).
            val fh = !compact // charts fill the band when wide; self-size when stacked
            val pg = !compact // pagination needs a fixed-height cell; stacked cells just show every row
            val two = if (compact) Modifier.fillMaxWidth() else Modifier.weight(2f).fillMaxHeight()
            val one = if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxHeight()
            val fill = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
            val gaugeMod = if (compact) Modifier.fillMaxWidth().height(200.dp) else fill // GaugeCard fillMaxHeight needs a bound
            val avgDaily = if (days > 0) (vol.toFloat() / days).toLong().toString() else "0"

            Band(compact) {
                CumulativeCard(remember(filtered) { cumulativeSeries(filtered) }, dates, win.totalPnl, two, fillHeight = fh)
                PairCell(compact, one, { WinnersCard(win, fill) }, { AveragesCard(remember(filtered) { averages(filtered) }, fill) })
                PairCell(
                    compact, one,
                    { HoldTimeCard(remember(filtered) { avgHoldMillis(filtered, true) }, remember(filtered) { avgHoldMillis(filtered, false) }, fill) },
                    { BestWorstCard(remember(filtered) { bestWorst(filtered) }, fill) },
                )
            }

            if (compact) {
                // The phone dashboard stops at the at-a-glance set + the dense figures. Every
                // breakdown the wide grid shows below (day/hour/month/duration/price, win%, volume,
                // drawdown) is already plotted over the same trades by Reports → Detailed, so
                // stacking them here only bought 18 sibling cards on a 411dp screen saying what
                // Reports already says. Redundancy cut, not information: nothing here is unreachable.
                StatsCard(streaks.maxWins, streaks.maxLosses, totalTrades, avgDaily, fees, Modifier.fillMaxWidth())
                ProfitFactorGauge(pf, gaugeMod)
            } else {
                Band(compact) {
                    BarChartCard("Win %", remember(filtered) { winRateByDay(filtered) }, diverging = false, barColor = tc.gain, modifier = two, fillHeight = fh)
                    BucketSection("Performance By Day Of Week", remember(filtered) { pnlByDayOfWeek(filtered) }, one, paged = pg)
                    BucketSection("Performance By Price", remember(filtered, priceById) { pnlByPrice(filtered) { priceById[it] } }, one, paged = pg)
                }
                Band(compact) {
                    DrawdownCard(remember(filtered) { drawdownSeries(filtered) }, dates, remember(filtered) { maxDrawdown(filtered) }, two, fillHeight = fh)
                    BucketSection("Performance By Hour Of Day", remember(byHour) { hourWindow(byHour) }, one, paged = pg)
                    BucketSection("Performance By Month Of Year", remember(filtered) { pnlByMonth(filtered) }, one, paged = pg)
                }
                Band(compact) {
                    BarChartCard("Daily Volume", remember(execWindow) { volumeByDay(execWindow) }, diverging = false, barColor = MaterialTheme.colorScheme.primary, modifier = two, fillHeight = fh)
                    BucketSection("Performance By Duration", remember(filtered) { pnlByDuration(filtered) }, one, paged = pg)
                    PairCell(
                        compact, one,
                        { StatsCard(streaks.maxWins, streaks.maxLosses, totalTrades, avgDaily, fees, fill) },
                        { ProfitFactorGauge(pf, gaugeMod) },
                    )
                }
                Band(compact) {
                    BarChartCard("Average Trade P&L", remember(filtered) { avgPnlByDay(filtered) }, diverging = true, barColor = tc.gain, modifier = two, fillHeight = fh)
                    // The band's right half stays open ground — the page tapers out instead of padding
                    // itself with more frames.
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        }
        }
    }
}

/**
 * A grid band. Wide: a Row exactly [WIDGET_UNIT] tall whose children carry weight/fillHeight modifiers.
 * Compact: no Row — the children emit straight into the parent scroll Column, so every slot becomes a
 * full-width card at natural height (the enclosing Column already spaces them 12dp apart).
 */
@Composable
private fun Band(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) {
        content()
    } else {
        Row(Modifier.fillMaxWidth().height(WIDGET_UNIT), horizontalArrangement = Arrangement.spacedBy(Space.md)) { content() }
    }
}

/**
 * One grid slot holding two Small widgets. Wide: stacked in a [cell]-sized Column (each X/2, filling the
 * band's height). Compact: emitted directly so the two cards stack full-width at natural height.
 */
@Composable
private fun PairCell(compact: Boolean, cell: Modifier, top: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    if (compact) {
        top(); bottom()
    } else {
        Column(modifier = cell, verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { top() }
            Box(Modifier.weight(1f).fillMaxWidth()) { bottom() }
        }
    }
}

/** Profit Factor half-donut. ∞ (no losers) fills the arc; a factor below 1 turns it loss-red. */
@Composable
private fun ProfitFactorGauge(pf: Double?, modifier: Modifier) {
    GaugeCard(
        "Profit Factor",
        valueText = pf?.let { "${(it * 100).toInt() / 100.0}" } ?: "∞",
        fraction = pf?.let { (it / (it + 1)).toFloat().coerceIn(0f, 1f) } ?: 1f,
        fg = if (pf == null || pf >= 1.0) MaterialTheme.colorScheme.primary else LocalTradeColors.current.loss,
        track = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    )
}

/** Streak/count figures + fees in one dense tile — five small numbers don't earn five cards. */
@Composable
private fun StatsCard(maxWins: Int, maxLosses: Int, trades: Int, avgDaily: String, fees: BigDecimal, modifier: Modifier = Modifier) {
    SectionCard("Streaks & Activity", modifier, fill = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            LabeledFigureRow("Max consecutive wins", maxWins.toString())
            LabeledFigureRow("Max consecutive losses", maxLosses.toString())
            LabeledFigureRow("Total trades", trades.toString())
            LabeledFigureRow("Average daily volume", avgDaily)
            LabeledFigureRow("Total fees", formatMoney(fees), pnlColor(fees))
        }
    }
}

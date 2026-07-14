package com.tradingtail.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.common.nowMillis
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor

/**
 * At-a-glance dashboard: the same widgets as Reports, but filtered to a recent window (30/60/90 days)
 * — Reports stays the full-history deep dive. Reuses the injected usecases so win-rate/hour aggregation
 * matches the rest of the app exactly (no re-implemented counting).
 */
// Widget height unit "X" = a Performance-By card's height, sized so 8 bucket rows + title + pager
// fill it with no dead glass (~392dp of content). Every wide band is a Row exactly X tall.
private val WIDGET_UNIT = 420.dp

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ponytail: one breakpoint — <600dp (phones) collapses the fixed 480dp bands to a single column.
        val compact = maxWidth < 600.dp
        CompositionLocalProvider(LocalCompact provides compact) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Space.md)) {
        if (compact) {
            // Stacked header: the trailing corner belongs to the floating theme/Import pill on
            // phones — a trailing toggle would sit underneath it.
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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Section map: day strip → equity + outcome pairs → chart-and-bucket bands →
            // dense stats + gauge → the closing chart → planned-widget strip (natural height).
            val tc = LocalTradeColors.current
            val dates = remember(filtered) { exitDateLabels(filtered) }
            val vol = remember(execWindow) { totalVolume(execWindow) }
            val days = remember(execWindow) { tradingDays(execWindow) }
            val pfFrac = pf?.let { (it / (it + 1)).toFloat().coerceIn(0f, 1f) } ?: 1f
            val pfText = pf?.let { "${(it * 100).toInt() / 100.0}" } ?: "∞"

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
                    {
                        GaugeCard(
                            "Profit Factor", pfText, pfFrac,
                            fg = if (pf == null || pf >= 1.0) MaterialTheme.colorScheme.primary else tc.loss,
                            track = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = gaugeMod,
                        )
                    },
                )
            }
            Band(compact) {
                BarChartCard("Average Trade P&L", remember(filtered) { avgPnlByDay(filtered) }, diverging = true, barColor = tc.gain, modifier = two, fillHeight = fh)
                // The band's right half stays open ground — the page tapers out instead of padding
                // itself with more frames.
                if (!compact) {
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
            PendingWidgetsCard()
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

/** Streak/count figures + fees in one dense tile — five small numbers don't earn five cards. */
@Composable
private fun StatsCard(maxWins: Int, maxLosses: Int, trades: Int, avgDaily: String, fees: BigDecimal, modifier: Modifier = Modifier) {
    SectionCard("Streaks & Activity", modifier, fill = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatRow("Max consecutive wins", maxWins.toString())
            StatRow("Max consecutive losses", maxLosses.toString())
            StatRow("Total trades", trades.toString())
            StatRow("Average daily volume", avgDaily)
            StatRow("Total fees", formatMoney(fees), pnlColor(fees))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The mock's market-data widgets, demoted from ten empty frames to one quiet strip — they stay
 * visible on purpose (they're the roadmap; see CLAUDE.md's deferred price-feed decision), but as
 * names, not dead glass. Reinstate a real card per widget as feeds land.
 */
private val PENDING_WIDGETS = listOf(
    "Average MFE vs MAE", "Average Position MAE", "Average Position MFE",
    "Performance By Instrument Opening Gap", "Performance By Instrument Day Type",
    "Performance By Instrument Volume", "Performance By Symbol Atr", "Performance By Rvol",
    "Performance By Instrument Movement", "Tag Breakdown",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingWidgetsCard() {
    SectionCard("Awaiting market data") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            for (title in PENDING_WIDGETS) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radii.pill))
                        .padding(horizontal = Space.md, vertical = Space.xs),
                )
            }
        }
    }
}

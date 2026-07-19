package com.tradingtail.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Space


// ---------------------------------------------------------------------------------------------
// Win vs Loss Days tab: trades split into winning-day vs losing-day cohorts
// ---------------------------------------------------------------------------------------------

@Composable
internal fun WinLossDaysView(trades: List<TradeEntity>, executions: List<ExecutionEntity>) {
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
internal fun DrawdownView(trades: List<TradeEntity>) {
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

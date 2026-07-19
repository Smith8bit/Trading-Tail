package com.tradingtail.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.bkkDate
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Space


// ---------------------------------------------------------------------------------------------
// Overview tab: the Recent rolling window and the Year/Month/Day drill-down.
// Split from AnalyticsScreen.kt (2026-07-19).
// ---------------------------------------------------------------------------------------------

/** Recent window (mock's "Recent"): 30/60/90-day daily P&L, cumulative curve, volume, win%.
 *  [period] lives in the Overview header row (OverviewControls), grouped with the view selector. */
@Composable
internal fun RecentView(trades: List<TradeEntity>, executions: List<ExecutionEntity>, now: Long, hasFilter: Boolean, period: Int) {
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
internal fun YearMonthDayView(trades: List<TradeEntity>, now: Long) {
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

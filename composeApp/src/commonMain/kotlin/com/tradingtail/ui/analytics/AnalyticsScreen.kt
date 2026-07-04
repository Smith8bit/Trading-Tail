package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.YearMonth
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkDate
import com.tradingtail.common.firstWeekday
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.formatMoney
import com.tradingtail.common.formatMoneyShort
import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.entity.TradeEntity
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
    winRate: CalculateWinRate,
    bySymbol: CalculatePnlBySymbol,
    byHour: CalculatePnlByHour,
) {
    val winRate: Flow<WinRateSummary> = repo.allFlow().map { winRate(it) }
    val bySymbol: Flow<List<SymbolPnl>> = repo.allFlow().map { bySymbol(it) }
    val byHour: Flow<List<HourPnl>> = repo.allFlow().map { byHour(it) }
    val trades: Flow<List<TradeEntity>> = repo.allFlow() // week strip, cumulative curve, recent list
}

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, modifier: Modifier = Modifier) {
    val win by vm.winRate.collectAsState(initial = WinRateSummary(0, 0, 0, 0.0, ZERO))
    val bySymbol by vm.bySymbol.collectAsState(initial = emptyList())
    val byHour by vm.byHour.collectAsState(initial = emptyList())
    val trades by vm.trades.collectAsState(initial = emptyList())
    val now = remember { nowMillis() }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeekStrip(remember(trades, now) { weekStrip(trades, now) })
        KpiGrid(win)
        CumulativeCard(remember(trades) { cumulativeSeries(trades) }, win.totalPnl)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WinnersCard(win, Modifier.weight(1f))
            BestWorstCard(remember(trades) { bestWorst(trades) }, Modifier.weight(1f))
        }

        SectionCard("P&L by Symbol") {
            if (bySymbol.isEmpty()) Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val max = maxAbs(bySymbol.map { it.pnl })
            for (s in bySymbol) BarRow("${s.symbol} (${s.trades}t)", s.pnl, max)
        }

        SectionCard("P&L by Hour (Bangkok)") {
            if (byHour.isEmpty()) Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val max = maxAbs(byHour.map { it.pnl })
            for (h in byHour) {
                BarRow(h.hour.toString().padStart(2, '0') + ":00 (${h.trades}t)", h.pnl, max)
            }
        }

        RecentTradesCard(remember(trades) { recentTrades(trades) })
    }
}

// ---------------------------------------------------------------------------------------------
// Widgets
// ---------------------------------------------------------------------------------------------

/** Preview's top week strip: last 7 Bangkok days, each cell a day's realized P&L + trade count. */
@Composable
private fun WeekStrip(days: List<WeekDay>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in days) {
            // Artifact's top-down wash: the gain/loss tint fades to transparent down the cell.
            val tint = Brush.verticalGradient(listOf(pnlFillTint(d.pnl), Color.Transparent))
            var cell = Modifier.weight(1f).height(76.dp)
                .background(tint, RoundedCornerShape(10.dp))
            cell = if (d.isToday) {
                cell.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            } else {
                cell.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            }
            Column(modifier = cell.padding(8.dp)) {
                Text(
                    "${d.day} ${d.dow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (d.count == 0) "·" else formatMoneyShort(d.pnl),
                    color = if (d.count == 0) MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(d.pnl),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
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

/** Preview's hero: cumulative realized P&L as a filled line. Hand-drawn on Canvas, no chart lib. */
@Composable
private fun CumulativeCard(series: List<Float>, total: BigDecimal) {
    val colors = LocalTradeColors.current
    val line = if (total < ZERO) colors.loss else colors.gain
    val zeroAxis = MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val n = series.size
                    val minV = minOf(0f, series.minOrNull() ?: 0f)
                    val maxV = maxOf(0f, series.maxOrNull() ?: 0f)
                    val range = (maxV - minV).let { if (it == 0f) 1f else it }
                    fun px(i: Int) = size.width * i / (n - 1)
                    fun py(v: Float) = size.height * (1f - (v - minV) / range)

                    drawLine(zeroAxis, Offset(0f, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1f)
                    val path = Path().apply {
                        moveTo(px(0), py(series[0]))
                        for (i in 1 until n) lineTo(px(i), py(series[i]))
                    }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(px(n - 1), size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(fill, color = line.copy(alpha = 0.16f))
                    drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
                }
            }
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
            Text("Winners vs losers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text("Best & worst", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
 * Diverging magnitude bar: a shared zero axis down the middle, losses growing left of it and gains
 * right, each fill length proportional to |P&L| over the section max. The solid fill matches the value.
 */
@Composable
private fun BarRow(label: String, pnl: BigDecimal, max: BigDecimal) {
    val colors = LocalTradeColors.current
    val frac = fraction(pnl, max)
    val barH = 24.dp

    Row(modifier = Modifier.fillMaxWidth().height(barH), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.weight(1f).height(barH), contentAlignment = Alignment.CenterEnd) {
            if (pnl < ZERO) Box(
                Modifier.fillMaxWidth(frac).height(barH)
                    .background(colors.loss, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
            )
        }
        Box(Modifier.width(1.dp).height(barH).background(MaterialTheme.colorScheme.outlineVariant))
        Box(modifier = Modifier.weight(1f).height(barH), contentAlignment = Alignment.CenterStart) {
            if (pnl > ZERO) Box(
                Modifier.fillMaxWidth(frac).height(barH)
                    .background(colors.gain, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
            )
        }
        Text(
            formatMoney(pnl),
            modifier = Modifier.weight(1.3f),
            color = pnlColor(pnl),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

/** Faint gain/loss wash behind a week cell — the number, not the fill, is the primary signal here. */
@Composable
private fun pnlFillTint(pnl: BigDecimal): Color = LocalTradeColors.current.let {
    when {
        pnl > ZERO -> it.gainFill
        pnl < ZERO -> it.lossFill
        else -> Color.Transparent
    }
}

// ---------------------------------------------------------------------------------------------
// Pure view-state derivations (testable; see AnalyticsTest)
// ---------------------------------------------------------------------------------------------

data class WeekDay(val day: Int, val dow: String, val pnl: BigDecimal, val count: Int, val isToday: Boolean)
data class BestWorst(val largestGain: BigDecimal, val largestLoss: BigDecimal)

private val DOW = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
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
            dow = DOW[dowLabel(date) - 1],
            pnl = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) },
            count = dayTrades.size,
            isToday = date == today,
        )
    }
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

private fun abs(x: BigDecimal): BigDecimal = if (x < ZERO) ZERO.subtract(x) else x

private fun maxAbs(pnls: List<BigDecimal>): BigDecimal =
    pnls.fold(ZERO) { m, p -> abs(p).let { if (it > m) it else m } }

private fun fraction(pnl: BigDecimal, max: BigDecimal): Float =
    if (max <= ZERO) 0f else (abs(pnl).toFloat() / max.toFloat()).coerceIn(0f, 1f)

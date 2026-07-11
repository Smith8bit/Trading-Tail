package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.tradingtail.common.CURRENCY
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.common.formatMoneyShort
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.pnlColor

// ---------------------------------------------------------------------------------------------
// Shared presentational widgets (cards / rows / charts / chips) used across the analytics screens.
// All `internal` so ReportsScreen.kt and DashboardScreen.kt in this package can reuse them.
// ---------------------------------------------------------------------------------------------

// ponytail: one responsive breakpoint drives every 2-up collapse, so a CompositionLocal beats
// prop-drilling a `compact` flag through a dozen view fns. 600dp splits phone (~360dp) from desktop.
internal val LocalCompact = staticCompositionLocalOf { false }

/** Card title size — one step down when compact so half-width chart titles stop wrapping to 3 lines. */
@Composable
internal fun cardTitleStyle() =
    if (LocalCompact.current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium

/** Two cards: side-by-side (each weight 1) on wide screens, stacked full-width when compact. */
@Composable
internal fun TwoUp(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    if (LocalCompact.current) {
        a(Modifier.fillMaxWidth()); b(Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            a(Modifier.weight(1f)); b(Modifier.weight(1f))
        }
    }
}

/**
 * Two charts sharing a fixed-height band (wide) or stacked at natural height (compact). The lambda's
 * Boolean is the card's `fillHeight` — true only inside the wide fixed-height band.
 */
@Composable
internal fun ChartPair(a: @Composable (Modifier, Boolean) -> Unit, b: @Composable (Modifier, Boolean) -> Unit) {
    if (LocalCompact.current) {
        a(Modifier.fillMaxWidth(), false); b(Modifier.fillMaxWidth(), false)
    } else {
        Row(Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            a(Modifier.weight(1f).fillMaxHeight(), true); b(Modifier.weight(1f).fillMaxHeight(), true)
        }
    }
}

/** One pill in a segmented toggle / chip row — the mock's selected-vs-idle chip styling. */
@Composable
internal fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/** 30/60/90-day window selector from the mock's dashboard header. */
@Composable
internal fun PeriodToggle(period: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in listOf(30, 60, 90)) ToggleChip("$p Days", p == period) { onSelect(p) }
    }
}

/** Preview's top week strip: last 7 Bangkok days, each cell a day's realized P&L + trade count. */
@Composable
internal fun WeekStrip(days: List<WeekDay>, rangeLabel: String) {
    // ponytail: on a phone, 7 equal cells = ~41dp each and the ฿ value clips — scroll + min-width instead.
    val compact = LocalCompact.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(rangeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (d in days) {
                val border = if (d.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Column(
                    modifier = (if (compact) Modifier.widthIn(min = 92.dp) else Modifier.weight(1f))
                        .height(84.dp)
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
internal fun KpiTile(label: String, value: String, valueColor: Color = Color.Unspecified, modifier: Modifier = Modifier) {
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
                // ponytail: large ฿ figures wrap rather than ellipsize — the primary number stays readable.
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Cumulative realized P&L as a filled line, with a dated x-axis + value gridlines (mock's hero chart). */
@Composable
internal fun CumulativeCard(series: List<Float>, dates: List<String>, total: BigDecimal, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
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
internal fun DrawdownCard(series: List<Float>, dates: List<String>, maxDd: BigDecimal, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
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
internal fun ColumnScope.chartModifier(fillHeight: Boolean, fixed: Dp): Modifier =
    if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(fixed)

/** Shared line-chart canvas: value gridlines + ฿ y-labels on the left, sampled date labels on the x-axis. */
@Composable
internal fun LineChartBody(series: List<Float>, dates: List<String>, line: Color, canvasModifier: Modifier, fillToBottom: Boolean) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = canvasModifier) {
        val leftPad = 66f // ponytail: room for ฿k/฿M + long ฿ tick labels
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
            val lay = measurer.measure("$CURRENCY${v.toInt()}", TextStyle(fontSize = 13.sp, color = labelColor))
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
internal fun BarChartCard(title: String, points: List<DayPoint>, diverging: Boolean, barColor: Color, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val colors = LocalTradeColors.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (points.isEmpty()) {
                Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(modifier = chartModifier(fillHeight, 150.dp)) {
                    // ponytail: 66px fits abbreviated ฿k/฿M and 5–6 digit ฿ tick labels without clipping.
                    val leftPad = 66f
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

/**
 * Horizontal bar chart matching the TraderVue "Detailed" report style: category labels down the left,
 * a value axis along the bottom. [performance] false = distribution (trade counts, single green bar
 * from the left); true = performance (summed P&L, bars diverging green/red from a center zero line).
 */
@Composable
internal fun HBarChartCard(title: String, buckets: List<BucketPnl>, performance: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = LocalTradeColors.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zeroLine = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard(title, modifier) {
        if (buckets.isEmpty()) {
            Text("No trades yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val n = buckets.size
        val rowH = 24.dp
        val axisH = 22.dp
        Canvas(modifier = Modifier.fillMaxWidth().height(rowH * n + axisH)) {
            val axisPx = axisH.toPx()
            val plotBottom = size.height - axisPx
            val labelStyle = TextStyle(fontSize = 12.sp, color = labelColor)
            val tickStyle = TextStyle(fontSize = 11.sp, color = labelColor)
            val maxLabelW = buckets.maxOf { measurer.measure(it.label, labelStyle).size.width }
            val leftPad = (maxLabelW + 10f).coerceAtMost(size.width * 0.42f)
            val plotW = size.width - leftPad

            val values = buckets.map { if (performance) it.pnl.toFloat() else it.trades.toFloat() }
            val rawMin = if (performance) minOf(0f, values.min()) else 0f
            val rawMax = maxOf(if (performance) 0f else 1f, values.max())
            val ticks = niceTicks(rawMin, rawMax)
            val axisMin = ticks.first()
            val axisMax = ticks.last()
            val span = (axisMax - axisMin).let { if (it == 0f) 1f else it }
            fun x(v: Float) = leftPad + plotW * (v - axisMin) / span
            val zeroX = x(0f)

            // Vertical gridlines + bottom axis labels.
            for (t in ticks) {
                val gx = x(t)
                drawLine(grid.copy(alpha = 0.35f), Offset(gx, 0f), Offset(gx, plotBottom), strokeWidth = 1f)
                val lab = measurer.measure(axisLabel(t, performance), tickStyle)
                val lx = (gx - lab.size.width / 2f).coerceIn(leftPad, size.width - lab.size.width)
                drawText(lab, topLeft = Offset(lx, plotBottom + 4f))
            }
            if (performance) drawLine(zeroLine, Offset(zeroX, 0f), Offset(zeroX, plotBottom), strokeWidth = 1.5f)

            // Bars + left category labels.
            val rowHpx = plotBottom / n
            val barH = rowHpx * 0.62f
            buckets.forEachIndexed { i, b ->
                val v = values[i]
                val cy = rowHpx * (i + 0.5f)
                val left = if (!performance) leftPad else minOf(zeroX, x(v))
                val right = if (!performance) x(v) else maxOf(zeroX, x(v))
                val c = if (!performance) colors.gain else if (v >= 0f) colors.gain else colors.loss
                val w = right - left
                if (w >= 0.5f) drawRect(c, topLeft = Offset(left, cy - barH / 2f), size = androidx.compose.ui.geometry.Size(w, barH))
                val lab = measurer.measure(b.label, labelStyle)
                drawText(lab, topLeft = Offset((leftPad - lab.size.width - 6f).coerceAtLeast(0f), cy - lab.size.height / 2f))
            }
        }
    }
}

internal data class LineSpec(val values: List<Float?>, val color: Color)

/** Multi-series line chart (nullable points break the line) sharing one value axis — the mock's
 *  P&L Moving Average / Volatility widgets. */
@Composable
internal fun MultiLineChartCard(title: String, dates: List<String>, series: List<LineSpec>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val n = series.firstOrNull()?.values?.size ?: 0
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (n < 2) {
                Text("Not enough data to create this chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    val leftPad = 66f // ponytail: room for ฿k/฿M + long ฿ tick labels
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
                        val lay = measurer.measure("$CURRENCY${v.toInt()}", TextStyle(fontSize = 13.sp, color = labelColor))
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

/** Half-donut gauge (Profit Factor, Largest gain/loss split) — foreground arc over a track. */
@Composable
internal fun GaugeCard(title: String, valueText: String, fraction: Float, fg: Color, track: Color, modifier: Modifier = Modifier) {
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
internal fun PlaceholderCard(title: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).height(56.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("n/a — needs market data", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Mock's "Average … Trade" widgets. Money averages — the app's one sanctioned money division. */
@Composable
internal fun AveragesCard(avg: Averages, modifier: Modifier = Modifier) {
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
internal fun HoldTimeCard(winnerMs: Long?, loserMs: Long?, modifier: Modifier = Modifier) {
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
internal fun WinnersCard(win: WinRateSummary, modifier: Modifier = Modifier, title: String = "Winning vs Losing Trades") {
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
internal fun LegendRow(dot: Color, label: String, count: Int) {
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
internal fun BestWorstCard(bw: BestWorst, modifier: Modifier = Modifier) {
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
internal fun BarRow(label: String, pnl: BigDecimal, max: BigDecimal, percent: Float? = null) {
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
internal fun SectionCard(title: String, modifier: Modifier = Modifier, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fill) Modifier.fillMaxHeight() else Modifier).padding(16.dp)) {
            Text(
                title,
                style = cardTitleStyle(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

// Rows shown per page before a fixed-height Performance-By card paginates. Tune with WIDGET_UNIT.
private const val ROWS_PER_PAGE = 8

/**
 * A "Performance by …" card of left-anchored magnitude bars. When [paged] (a fixed-height cell) and
 * the rows overflow, it paginates with a "‹ 1/2 ›" pager; bar scale stays constant across pages.
 */
@Composable
internal fun BucketSection(title: String, buckets: List<BucketPnl>, modifier: Modifier = Modifier, paged: Boolean = false) {
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

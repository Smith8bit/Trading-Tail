package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.ui.theme.FontSize
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Canvas charts: the shared Axis/ChartFrame machinery, hover/tooltip layer, and every chart
// card (line / bar / h-bar / multi-line / gauge). Split from AnalyticsWidgets.kt (2026-07-19).
// ---------------------------------------------------------------------------------------------

/** Cumulative realized P&L as a filled line, with a dated x-axis + value gridlines (mock's hero chart). */
@Composable
internal fun CumulativeCard(series: List<Float>, dates: List<String>, total: BigDecimal, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val colors = LocalTradeColors.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(Space.lg)) {
            Text("Cumulative P&L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                formatMoney(total),
                color = pnlColor(total),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Space.sm, bottom = Space.md),
            )
            if (series.size < 2) {
                Text("Close a few trades to plot the curve.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChartBody(series, dates, colors.gain, chartModifier(fillHeight, 190.dp), fillToBottom = true, negColor = colors.loss)
            }
        }
    }
}

/** Underwater plot: cumulative P&L's distance below its running peak, with the max drawdown called out. */
@Composable
internal fun DrawdownCard(series: List<Float>, dates: List<String>, maxDd: BigDecimal, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val colors = LocalTradeColors.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(Space.lg)) {
            Text("Cumulative Drawdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val shown = ZERO.subtract(maxDd) // report the max drawdown as a signed loss figure
            Text(
                formatMoney(shown),
                color = pnlColor(shown),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Space.sm, bottom = Space.md),
            )
            if (series.size < 2) {
                Text("Close a few trades to plot drawdown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChartBody(series, dates, colors.loss, chartModifier(fillHeight, 150.dp), fillToBottom = false)
            }
        }
    }
}

/** Canvas sizing shared by charts: fill the card when it's a square dashboard cell, else a fixed height. */
internal fun ColumnScope.chartModifier(fillHeight: Boolean, fixed: Dp): Modifier =
    if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(fixed)

/** Band under the plot: tick labels, plus room for the axis caption when there is one. */
private fun bottomPadOf(xLabel: String): Dp = if (xLabel.isEmpty()) 28.dp else 48.dp

/** Gap between the plot and its labels — under the x axis, and after the y gutter. */
private val LABEL_GAP = 6.dp

/** A chart's value axis: the "nice" ticks, and the left gutter their widest label actually needs (px). */
private class Axis(val ticks: List<Float>, val gutter: Float)

/**
 * The value axis, shared by a chart's draw pass and its hover hit-test (they must agree on the gutter
 * or tooltips point at the wrong bar).
 *
 * ponytail: the gutter is MEASURED, not a constant. It was a flat `LEFT_PAD = 84.dp` — about a quarter
 * of a chart card's width on a phone, handed to labels that usually read "10" or "5". The plot got
 * whatever was left, which is what made the charts feel cramped and squeezed the date labels into each
 * other. The constant wasn't safe either: a count axis isn't abbreviated, so Daily Volume can print
 * "300000" and overrun 84dp. HBarChartCard already sizes its gutter from its measured labels — this is
 * the same trick for the vertical charts, and it deletes the magic number rather than re-tuning it.
 *
 * Ticks depend only on the data (never the canvas size), so they hoist out of the draw lambda for free.
 */
@Composable
private fun rememberAxis(values: List<Float>, money: Boolean, zeroCentered: Boolean, measurer: TextMeasurer): Axis {
    val gap = with(LocalDensity.current) { LABEL_GAP.toPx() }
    // Keyed on `values` by structural equality, so an inline list rebuilt each recomposition still hits.
    return remember(values, money, zeroCentered, gap) {
        val lo: Float
        val hi: Float
        if (zeroCentered) {
            val b = zeroCenteredBounds(values); lo = b.first; hi = b.second
        } else {
            lo = 0f; hi = maxOf(0f, values.maxOrNull() ?: 0f)
        }
        val ticks = niceTicks(lo, hi)
        val widest = ticks.maxOfOrNull {
            measurer.measure(axisLabel(it, money), TextStyle(fontSize = FontSize.sm)).size.width
        } ?: 0
        Axis(ticks, widest + gap)
    }
}

/**
 * How many evenly-spaced x labels fit across [plotW] without colliding — a label plus 40% of its own
 * width as breathing room. The count used to be a flat 5 or 6 regardless of width, which is why a
 * phone's date axis ran together ("06-2406-24").
 */
private fun maxXLabels(plotW: Float, labelW: Float): Int =
    if (labelW <= 0f) 0 else (plotW / (labelW * 1.4f)).toInt()

/**
 * Shared geometry of a vertical chart's plot area — the [Axis] gutter, the label band below, and the
 * value→y / index→x mappings. Every vertical chart used to re-declare this block inline, and the
 * hover hit-tests ([barIndexAt]/[lineIndexAt]) assume the same gutter — one definition means the
 * geometry can't drift per chart. Constructed from plain floats so the mapping is unit-testable.
 */
internal class ChartFrame(
    width: Float,
    height: Float,
    val leftPad: Float,
    bottomPad: Float,
    val ticks: List<Float>,
    private val n: Int,
) {
    val plotW = width - leftPad
    val plotH = height - bottomPad
    private val minV = ticks.first()
    private val range = (ticks.last() - minV).let { if (it == 0f) 1f else it }

    /** y of a value: ticks.first() → plotH (bottom edge), ticks.last() → 0 (top edge). */
    fun py(v: Float) = plotH * (1f - (v - minV) / range)

    /** x of sample [i] of the n points, evenly spread across the plot after the gutter. */
    fun px(i: Int) = leftPad + plotW * i / (n - 1)
}

private fun DrawScope.chartFrame(axis: Axis, xLabel: String, n: Int) =
    ChartFrame(size.width, size.height, axis.gutter, bottomPadOf(xLabel).toPx(), axis.ticks, n)

/** Horizontal gridline + left tick label per axis tick — the value axis every vertical chart draws. */
private fun DrawScope.drawValueAxis(f: ChartFrame, money: Boolean, measurer: TextMeasurer, grid: Color, labelColor: Color) {
    val hair = 1.dp.toPx()
    for (t in f.ticks) {
        val y = f.py(t)
        drawLine(grid.copy(alpha = 0.35f), Offset(f.leftPad, y), Offset(size.width, y), strokeWidth = hair)
        val lay = measurer.measure(axisLabel(t, money), TextStyle(fontSize = FontSize.sm, color = labelColor))
        drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
    }
}

/** The emphasized zero line. Callers pick the moment: over bars, under line paths. */
private fun DrawScope.drawZeroLine(f: ChartFrame, color: Color) {
    drawLine(color, Offset(f.leftPad, f.py(0f)), Offset(size.width, f.py(0f)), strokeWidth = 1.5.dp.toPx())
}

/** Evenly-spread sample of up to [cap] of [n] indices whose labels fit [plotW]; empty when < 2 fit. */
private fun sampledIndices(n: Int, cap: Int, plotW: Float, labelW: Float): List<Int> =
    minOf(cap, n, maxXLabels(plotW, labelW)).let { xn ->
        if (xn < 2) emptyList() else (0 until xn).map { it * (n - 1) / (xn - 1) }
    }

/** X-axis labels at [indices], centered on [xOf]'s position and clamped inside the plot. */
private fun DrawScope.drawXLabels(
    f: ChartFrame,
    indices: List<Int>,
    label: (Int) -> String,
    xOf: (Int) -> Float,
    measurer: TextMeasurer,
    labelColor: Color,
) {
    for (i in indices) {
        val lay = measurer.measure(label(i), TextStyle(fontSize = FontSize.sm, color = labelColor))
        val x = (xOf(i) - lay.size.width / 2f).coerceIn(f.leftPad, size.width - lay.size.width)
        drawText(lay, topLeft = Offset(x, f.plotH + LABEL_GAP.toPx()))
    }
}

/**
 * Wraps a chart canvas so hovering the mouse over a bar/point floats a tooltip describing it. [indexAt]
 * maps the pointer position to a data index (-1 = nothing under the cursor); [tooltip] renders it.
 * ponytail: hover is desktop-only — Android has no pointer-move, so this stays inert there (no tap sheet).
 */
@Composable
internal fun HoverChart(
    modifier: Modifier,
    itemCount: Int,
    indexAt: (Offset, IntSize) -> Int,
    tooltip: @Composable (Int) -> Unit,
    draw: DrawScope.(hoverIdx: Int) -> Unit,
) {
    // rememberUpdatedState so the long-lived pointer coroutine always hit-tests against the CURRENT
    // data/geometry — not the sizes captured when the chart first composed, which would map hover to
    // the wrong bar after the data changes.
    val currentIndexAt by rememberUpdatedState(indexAt)
    var box by remember { mutableStateOf(IntSize.Zero) }
    var idx by remember { mutableStateOf(-1) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val pos = e.changes.first().position
                        idx = if (e.type == PointerEventType.Exit) -1 else { pointer = pos; currentIndexAt(pos, box) }
                    }
                }
            },
    ) {
        // Clamp to the current count: when the data shrinks between pointer events a stale idx would
        // otherwise index out of bounds (crash) or point at the wrong datum. draw reads it, so the
        // canvas repaints as the hovered bar/point changes (highlight follows the cursor).
        val shown = if (idx in 0 until itemCount) idx else -1
        Canvas(Modifier.matchParentSize()) { draw(shown) }
        if (shown >= 0) ChartTooltip(pointer, box) { tooltip(shown) }
    }
}

/** Floating card pinned just above-right of the cursor, clamped to stay inside the chart. */
@Composable
private fun ChartTooltip(pointer: Offset, box: IntSize, content: @Composable () -> Unit) {
    var tip by remember { mutableStateOf(IntSize.Zero) }
    val x = (pointer.x.toInt() + 14).coerceIn(0, (box.width - tip.width).coerceAtLeast(0))
    val y = (pointer.y.toInt() - tip.height - 10).coerceAtLeast(0)
    Column(
        Modifier.offset { IntOffset(x, y) }
            .onSizeChanged { tip = it }
            .clip(RoundedCornerShape(Radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radii.md))
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

/** Vertical-bar hit-test: which of [n] equal slots (after the [leftPad] gutter) the cursor x sits in. */
private fun barIndexAt(pos: Offset, box: IntSize, n: Int, leftPad: Float): Int {
    val plotW = box.width - leftPad
    if (n <= 0 || plotW <= 0f || pos.x < leftPad) return -1
    return ((pos.x - leftPad) / (plotW / n)).toInt().coerceIn(0, n - 1)
}

/** Line hit-test: the sample nearest the cursor x across [n] evenly spaced points. */
private fun lineIndexAt(pos: Offset, box: IntSize, n: Int, leftPad: Float): Int {
    val plotW = box.width - leftPad
    if (n < 1 || plotW <= 0f || pos.x < leftPad) return -1
    if (n == 1) return 0
    return (((pos.x - leftPad) / plotW) * (n - 1)).roundToInt().coerceIn(0, n - 1)
}

/** Horizontal-bar hit-test: which row band (above the bottom axis) the cursor y sits in. */
private fun hbarIndexAt(pos: Offset, box: IntSize, n: Int, axisPx: Float): Int {
    val plotBottom = box.height - axisPx
    if (n <= 0 || plotBottom <= 0f || pos.y < 0f || pos.y > plotBottom) return -1
    return (pos.y / (plotBottom / n)).toInt().coerceIn(0, n - 1)
}

/**
 * Value-axis (min, max) for a vertical chart, padded to include 0. When the data straddles zero
 * (gains and losses both present) the bounds are made symmetric so the zero line lands at the
 * vertical middle of the graph; one-sided data keeps zero at the baseline (no wasted half).
 */
private fun zeroCenteredBounds(values: List<Float>): Pair<Float, Float> {
    val lo = minOf(0f, values.minOrNull() ?: 0f)
    val hi = maxOf(0f, values.maxOrNull() ?: 0f)
    if (lo < 0f && hi > 0f) { val b = maxOf(-lo, hi); return -b to b }
    return lo to hi
}

/** Centered x-axis caption pinned to the bottom of the canvas, under the tick labels. */
private fun DrawScope.drawAxisTitle(measurer: TextMeasurer, text: String, color: Color, leftPad: Float) {
    if (text.isEmpty()) return
    val lay = measurer.measure(text, TextStyle(fontSize = FontSize.sm, color = color, fontWeight = FontWeight.Medium))
    val plotW = size.width - leftPad
    val x = (leftPad + (plotW - lay.size.width) / 2f).coerceAtLeast(leftPad)
    drawText(lay, topLeft = Offset(x, size.height - lay.size.height))
}

/** Small muted caption + a label→value line, the shared shape of every chart tooltip. */
@Composable
private fun TooltipHead(text: String) {
    if (text.isNotEmpty()) Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun TooltipValue(text: String, color: Color = MaterialTheme.colorScheme.onSurface) = Text(
    text,
    color = color,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    style = MaterialTheme.typography.labelMedium,
)

/** Vertical-bar tooltip: chart title as context, then the bar's label + value ($-prefixed when money). */
@Composable
private fun BarTooltip(title: String, p: DayPoint, money: Boolean) {
    TooltipHead(title)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(p.label, style = MaterialTheme.typography.labelMedium)
        TooltipValue(tickLabel(p.value, money))
    }
}

/** Line tooltip: the point's date and its $ value, colored by sign. */
@Composable
private fun LineTooltip(date: String, value: Float) {
    val c = LocalTradeColors.current.let { if (value > 0f) it.gain else if (value < 0f) it.loss else it.neutralPnl }
    TooltipHead(date)
    TooltipValue(tickLabel(value, money = true), c)
}

/** Multi-series tooltip: the date, then a color swatch + $ value per line that has a point here. */
@Composable
private fun MultiLineTooltip(date: String, series: List<LineSpec>, i: Int) {
    TooltipHead(date)
    for (sp in series) {
        val v = sp.values.getOrNull(i) ?: continue
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.size(8.dp).background(sp.color, RoundedCornerShape(2.dp)))
            TooltipValue(tickLabel(v, money = true))
        }
    }
}

/** Horizontal-bar tooltip: bucket label, its $ P&L (performance charts) and trade count. */
@Composable
private fun BucketTooltip(b: BucketPnl, performance: Boolean) {
    Text(b.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    if (performance) TooltipValue(formatMoney(b.pnl), pnlColor(b.pnl))
    Text(
        tradeCount(b.trades),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shared line-chart canvas: value gridlines + $ y-labels on the left, sampled date labels on the x-axis. */
@Composable
internal fun LineChartBody(series: List<Float>, dates: List<String>, line: Color, canvasModifier: Modifier, fillToBottom: Boolean, xLabel: String = "Date", negColor: Color? = null) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotRing = MaterialTheme.colorScheme.surface // halo so the hover dot reads over the line
    // Axis spans nice round ticks (not the raw data bounds) so every gridline label is a clean
    // integer-ish value instead of "4.81"; the gutter is sized to the widest of those labels.
    val axis = rememberAxis(series, money = true, zeroCentered = true, measurer = measurer)
    HoverChart(
        modifier = canvasModifier,
        itemCount = series.size,
        indexAt = { pos, sz -> lineIndexAt(pos, sz, series.size, axis.gutter) },
        tooltip = { i -> LineTooltip(dates.getOrElse(i) { "" }, series[i]) },
    ) { hoverIdx ->
        val n = series.size
        val f = chartFrame(axis, xLabel, n) // same axis as the hover hit-test — the gutter can't drift
        drawValueAxis(f, money = true, measurer = measurer, grid = grid, labelColor = labelColor)
        drawZeroLine(f, grid)

        val path = Path().apply {
            moveTo(f.px(0), f.py(series[0]))
            for (i in 1 until n) lineTo(f.px(i), f.py(series[i]))
        }
        if (negColor == null) {
            val base = if (fillToBottom) f.plotH else f.py(0f)
            val fill = Path().apply { addPath(path); lineTo(f.px(n - 1), base); lineTo(f.px(0), base); close() }
            drawPath(fill, color = line.copy(alpha = 0.16f))
            drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
        } else {
            // Sign-colored equity curve: fill/line to the zero line, [line] above it, [negColor] below.
            val zeroY = f.py(0f)
            val fill = Path().apply { addPath(path); lineTo(f.px(n - 1), zeroY); lineTo(f.px(0), zeroY); close() }
            clipRect(top = 0f, bottom = zeroY) {
                drawPath(fill, color = line.copy(alpha = 0.16f))
                drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
            }
            clipRect(top = zeroY, bottom = size.height) {
                drawPath(fill, color = negColor.copy(alpha = 0.16f))
                drawPath(path, color = negColor, style = Stroke(width = 2.dp.toPx()))
            }
        }

        if (hoverIdx in 0 until n) {
            val hx = f.px(hoverIdx); val hy = f.py(series[hoverIdx])
            drawLine(grid, Offset(hx, 0f), Offset(hx, f.plotH), strokeWidth = 1.dp.toPx())
            drawCircle(dotRing, radius = 5.5.dp.toPx(), center = Offset(hx, hy))
            drawCircle(if (negColor != null && series[hoverIdx] < 0f) negColor else line, radius = 4.dp.toPx(), center = Offset(hx, hy))
        }

        val dateW = measurer.measure(dates.firstOrNull() ?: "", TextStyle(fontSize = FontSize.sm)).size.width
        drawXLabels(f, sampledIndices(n, 5, f.plotW, dateW.toFloat()), { dates.getOrElse(it) { "" } }, f::px, measurer, labelColor)
        drawAxisTitle(measurer, xLabel, labelColor, f.leftPad)
    }
}

/** Per-day vertical bar chart (Win %, Daily Volume, Average Trade P&L). Diverging colors green/red by sign. */
@Composable
internal fun BarChartCard(title: String, points: List<DayPoint>, diverging: Boolean, barColor: Color, modifier: Modifier = Modifier, fillHeight: Boolean = false, xLabel: String = "Date", labelEveryBar: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val colors = LocalTradeColors.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hoverTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) // brightens the hovered bar
    // Nice round tick values as the axis bounds — labels read "5", not "4.81" — and a gutter sized to them.
    val axis = rememberAxis(points.map { it.value }, money = diverging, zeroCentered = diverging, measurer = measurer)
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(Space.lg)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.lg))
            if (points.isEmpty()) {
                Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                HoverChart(
                    modifier = chartModifier(fillHeight, 190.dp),
                    itemCount = points.size,
                    indexAt = { pos, sz -> barIndexAt(pos, sz, points.size, axis.gutter) },
                    tooltip = { i -> BarTooltip(title, points[i], money = diverging) },
                ) { hoverIdx ->
                    val n = points.size
                    val f = chartFrame(axis, xLabel, n) // same axis as the hover hit-test — the gutter can't drift
                    val values = points.map { it.value }
                    drawValueAxis(f, money = diverging, measurer = measurer, grid = grid, labelColor = labelColor)
                    val slot = f.plotW / n
                    // ponytail: cap width so a 2–3 bar chart draws bars, not fat blocks.
                    val barW = minOf(slot * 0.6f, 48.dp.toPx())
                    fun barX(i: Int) = f.leftPad + slot * (i + 0.5f)
                    fun drawBar(i: Int, color: Color) {
                        val top = f.py(maxOf(values[i], 0f))
                        val bot = f.py(minOf(values[i], 0f))
                        drawRect(color, topLeft = Offset(barX(i) - barW / 2f, minOf(top, bot)), size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(bot - top).coerceAtLeast(1f)))
                    }
                    for (i in 0 until n) {
                        val v = values[i]
                        drawBar(i, if (diverging) (if (v >= 0f) colors.gain else colors.loss) else barColor)
                    }
                    drawZeroLine(f, grid)

                    if (hoverIdx in 0 until n) drawBar(hoverIdx, hoverTint)

                    // Every bar labeled (years/months) or evenly sampled to however many actually fit.
                    val labelW = measurer.measure(points[0].label, TextStyle(fontSize = FontSize.sm)).size.width
                    val xIdx = if (labelEveryBar) (0 until n).toList() else sampledIndices(n, 6, f.plotW, labelW.toFloat())
                    drawXLabels(f, xIdx, { points[it].label }, ::barX, measurer, labelColor)
                    drawAxisTitle(measurer, xLabel, labelColor, f.leftPad)
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
    val hoverTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) // brightens the hovered bar
    SectionCard(title, modifier) {
        if (buckets.isEmpty()) {
            Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val n = buckets.size
        val rowH = 36.dp
        val axisH = 44.dp // tick labels + the x-axis caption below them
        val axisPxHit = with(LocalDensity.current) { axisH.toPx() }
        HoverChart(
            modifier = Modifier.fillMaxWidth().height(rowH * n + axisH),
            itemCount = n,
            indexAt = { pos, sz -> hbarIndexAt(pos, sz, n, axisPxHit) },
            tooltip = { i -> BucketTooltip(buckets[i], performance) },
        ) { hoverIdx ->
            val axisPx = axisH.toPx()
            val hair = 1.dp.toPx()
            val gap = LABEL_GAP.toPx()
            val plotBottom = size.height - axisPx
            val labelStyle = TextStyle(fontSize = FontSize.sm, color = labelColor)
            val tickStyle = TextStyle(fontSize = FontSize.sm, color = labelColor)
            val maxLabelW = buckets.maxOf { measurer.measure(it.label, labelStyle).size.width }
            // maxLabelW is measured px so it scales itself; the trailing gap has to be converted.
            val leftPad = (maxLabelW + 14.dp.toPx()).coerceAtMost(size.width * 0.42f)
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
                drawLine(grid.copy(alpha = 0.35f), Offset(gx, 0f), Offset(gx, plotBottom), strokeWidth = hair)
                val lab = measurer.measure(axisLabel(t, performance), tickStyle)
                val lx = (gx - lab.size.width / 2f).coerceIn(leftPad, size.width - lab.size.width)
                drawText(lab, topLeft = Offset(lx, plotBottom + gap))
            }
            if (performance) drawLine(zeroLine, Offset(zeroX, 0f), Offset(zeroX, plotBottom), strokeWidth = 1.5.dp.toPx())
            drawAxisTitle(measurer, if (performance) "P&L" else "Trades", labelColor, leftPad)

            // Bars + left category labels.
            val rowHpx = plotBottom / n
            val barH = rowHpx * 0.5f
            buckets.forEachIndexed { i, b ->
                val v = values[i]
                val cy = rowHpx * (i + 0.5f)
                val left = if (!performance) leftPad else minOf(zeroX, x(v))
                val right = if (!performance) x(v) else maxOf(zeroX, x(v))
                val c = if (!performance) colors.gain else if (v >= 0f) colors.gain else colors.loss
                val w = right - left
                if (w >= 0.5f) drawRect(c, topLeft = Offset(left, cy - barH / 2f), size = androidx.compose.ui.geometry.Size(w, barH))
                val lab = measurer.measure(b.label, labelStyle)
                drawText(lab, topLeft = Offset((leftPad - lab.size.width - gap).coerceAtLeast(0f), cy - lab.size.height / 2f))
            }

            if (hoverIdx in buckets.indices) {
                val v = values[hoverIdx]
                val cy = rowHpx * (hoverIdx + 0.5f)
                val left = if (!performance) leftPad else minOf(zeroX, x(v))
                val right = if (!performance) x(v) else maxOf(zeroX, x(v))
                drawRect(hoverTint, topLeft = Offset(left, cy - barH / 2f), size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), barH))
            }
        }
    }
}

internal data class LineSpec(val values: List<Float?>, val color: Color)

/** Multi-series line chart (nullable points break the line) sharing one value axis — the mock's
 *  P&L Moving Average / Volatility widgets. */
@Composable
internal fun MultiLineChartCard(title: String, dates: List<String>, series: List<LineSpec>, modifier: Modifier = Modifier, xLabel: String = "Date") {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotRing = MaterialTheme.colorScheme.surface // halo so the hover dots read over the lines
    val n = series.firstOrNull()?.values?.size ?: 0
    // Nice round tick values as the axis bounds — labels read "200", not "173.91" — gutter sized to them.
    val axis = rememberAxis(series.flatMap { it.values.filterNotNull() }, money = true, zeroCentered = true, measurer = measurer)
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.lg))
            if (n < 2) {
                Text("Not enough data to create this chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                HoverChart(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    itemCount = n,
                    indexAt = { pos, sz -> lineIndexAt(pos, sz, n, axis.gutter) },
                    tooltip = { i -> MultiLineTooltip(dates.getOrElse(i) { "" }, series, i) },
                ) { hoverIdx ->
                    val f = chartFrame(axis, xLabel, n) // same axis as the hover hit-test — the gutter can't drift
                    drawValueAxis(f, money = true, measurer = measurer, grid = grid, labelColor = labelColor)
                    drawZeroLine(f, grid)

                    for (sp in series) {
                        val path = Path()
                        var started = false
                        for (i in 0 until n) {
                            val v = sp.values[i]
                            if (v == null) { started = false } else if (!started) { path.moveTo(f.px(i), f.py(v)); started = true } else path.lineTo(f.px(i), f.py(v))
                        }
                        drawPath(path, sp.color, style = Stroke(width = 2.dp.toPx()))
                    }

                    if (hoverIdx in 0 until n) {
                        val hx = f.px(hoverIdx)
                        drawLine(grid, Offset(hx, 0f), Offset(hx, f.plotH), strokeWidth = 1.dp.toPx())
                        for (sp in series) {
                            val v = sp.values.getOrNull(hoverIdx) ?: continue
                            drawCircle(dotRing, radius = 5.dp.toPx(), center = Offset(hx, f.py(v)))
                            drawCircle(sp.color, radius = 3.5.dp.toPx(), center = Offset(hx, f.py(v)))
                        }
                    }

                    val dateW = measurer.measure(dates.firstOrNull() ?: "", TextStyle(fontSize = FontSize.sm)).size.width
                    drawXLabels(f, sampledIndices(n, 5, f.plotW, dateW.toFloat()), { dates.getOrElse(it) { "" } }, f::px, measurer, labelColor)
                    drawAxisTitle(measurer, xLabel, labelColor, f.leftPad)
                }
            }
        }
    }
}

/** Half-donut gauge (Profit Factor, Largest gain/loss split) — foreground arc over a track. */
@Composable
internal fun GaugeCard(title: String, valueText: String, fraction: Float, fg: Color, track: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxHeight().padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            // Centered in the remaining (square) space: the dial with its value sitting in the arc.
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = Space.md), contentAlignment = Alignment.Center) {
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

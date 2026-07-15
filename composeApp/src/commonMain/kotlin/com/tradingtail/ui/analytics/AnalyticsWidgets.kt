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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.FontSize
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Shared presentational widgets (cards / rows / charts / chips) used across the analytics screens.
// All `internal` so ReportsScreen.kt and DashboardScreen.kt in this package can reuse them.
// ---------------------------------------------------------------------------------------------

// ponytail: one responsive breakpoint drives every 2-up collapse, so a CompositionLocal beats
// prop-drilling a `compact` flag through a dozen view fns. 600dp splits phone (~360dp) from desktop.
internal val LocalCompact = staticCompositionLocalOf { false }

/** "1 trade" / "3 trades" — never "1 trades". (CalendarScreen keeps its own copy; one line each.) */
internal fun tradeCount(n: Int) = if (n == 1) "1 trade" else "$n trades"

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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
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
        Row(Modifier.fillMaxWidth().height(380.dp), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            a(Modifier.weight(1f).fillMaxHeight(), true); b(Modifier.weight(1f).fillMaxHeight(), true)
        }
    }
}

/** One pill in a chip row — the mock's selected-vs-idle chip styling (the year / month pickers). */
@Composable
internal fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.clip(RoundedCornerShape(Radii.md)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * Chrome-style **tab sheet**: tab heads sitting on a translucent content panel. One continuous path
 * traces the active head *and* the panel with no line between them — filled with glass and stroked
 * with a sheen hairline, so the two read as a single sheet of paper with a tab at its head. The idle
 * heads are muted fills that tuck behind the sheet's top edge. Scrolls horizontally when compact.
 *
 * The sheet is translucent (the aurora glows through it) and the tiles inside it are opaque — that
 * ordering is deliberate, and it's why tiles can't be frosted: see the note in Glass.kt.
 */
@Composable
internal fun TabSheet(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tc = LocalTradeColors.current
    val radius = with(LocalDensity.current) { Radii.md.toPx() }
    val stroke = with(LocalDensity.current) { 1.dp.toPx() }
    // Plain holder, not State: it's only written and read inside layout callbacks, and a State read
    // there would re-invalidate layout every pass. `head` IS State — written in layout, read in draw.
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var head by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier
            .onGloballyPositioned { root[0] = it }
            .drawWithContent {
                val sheet = if (head.width > 1f) sheetOutline(head, size, radius) else null
                // Fill under the content, stroke over it: the edge then crosses the idle heads'
                // bottoms, which is what tucks them behind the sheet.
                sheet?.let { drawPath(it, tc.glass) }
                drawContent()
                sheet?.let { drawPath(it, tc.sheen, style = Stroke(stroke)) }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items.forEachIndexed { i, label ->
                    val isSel = i == selected
                    Box(
                        modifier = Modifier
                            // Only the active head reports; the outline routes around wherever it lands
                            // (localBoundingBoxOf resolves the strip's scroll for free).
                            .then(
                                if (isSel) Modifier.onGloballyPositioned { c ->
                                    root[0]?.let { head = it.localBoundingBoxOf(c, clipBounds = false) }
                                } else Modifier,
                            )
                            .clip(RoundedCornerShape(topStart = Radii.md, topEnd = Radii.md))
                            .background(if (isSel) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelect(i) }
                            .padding(horizontal = Space.lg, vertical = Space.md),
                    ) {
                        Text(
                            label,
                            // The active head has no fill of its own — the sheet's glass is drawn
                            // under it, so its label is on-surface like the sheet's contents.
                            color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** The tab-sheet silhouette: up and over the active head, then around the panel — open under the head. */
private fun sheetOutline(head: Rect, size: Size, r: Float): Path {
    val top = head.bottom // the panel's top edge — the seam the head opens into
    val w = size.width
    val h = size.height
    val l = head.left.coerceIn(0f, w) // a part-scrolled head still yields a sane path
    val rt = head.right.coerceIn(l, w)
    val hr = r.coerceAtMost(head.height / 2f)
    return Path().apply {
        moveTo(0f, h - r)
        lineTo(0f, top)                                   // panel's left side
        lineTo(l, top)                                    // top edge, left of the head
        lineTo(l, head.top + hr)                          // up the head's left side
        quadraticBezierTo(l, head.top, l + hr, head.top)
        lineTo(rt - hr, head.top)                         // across the head's top
        quadraticBezierTo(rt, head.top, rt, head.top + hr)
        lineTo(rt, top)                                   // down the head's right side
        lineTo(w, top)                                    // top edge, right of the head
        lineTo(w, h - r)                                  // panel's right side
        quadraticBezierTo(w, h, w - r, h)
        lineTo(r, h)                                      // panel's bottom
        quadraticBezierTo(0f, h, 0f, h - r)
    }
}

/**
 * A **segmented control** for the sub-level switchers (view mode, period, category): one recessed
 * surfaceVariant track, segments butted together, the selected one raised to a surface pill with a
 * sheen edge + primary label. Deliberately subordinate to the filled [CardTabs] above. [selected] may
 * be out of range so a nav-only segment (Calendar) shows no highlight; [scrollable] lets a long track
 * (the category selector) scroll on narrow widths.
 */
@Composable
internal fun SegmentedControl(items: List<String>, selected: Int, scrollable: Boolean = false, onSelect: (Int) -> Unit) {
    val tc = LocalTradeColors.current
    val track = RoundedCornerShape(Radii.md)
    Row(
        modifier = Modifier
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .clip(track).background(MaterialTheme.colorScheme.surfaceVariant).padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items.forEachIndexed { i, label ->
            val isSel = i == selected
            val seg = RoundedCornerShape(Radii.sm)
            Box(
                modifier = Modifier.clip(seg)
                    .then(if (isSel) Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, tc.sheen, seg) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(horizontal = Space.md, vertical = Space.sm),
            ) {
                Text(
                    label,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 30/60/90-day window selector — a segmented control (the mock's dashboard header period toggle). */
@Composable
internal fun PeriodToggle(period: Int, onSelect: (Int) -> Unit) {
    val opts = listOf(30, 60, 90)
    SegmentedControl(opts.map { "$it Days" }, opts.indexOf(period)) { onSelect(opts[it]) }
}

/** Preview's top week strip: last 7 Bangkok days, each cell a day's realized P&L + trade count. */
@Composable
internal fun WeekStrip(days: List<WeekDay>, rangeLabel: String) {
    // ponytail: on a phone, 7 equal cells = ~41dp each and the $ value clips — scroll + min-width instead.
    val compact = LocalCompact.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(rangeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            for (d in days) {
                // Opaque like every other data surface — a bare outline here left the aurora showing
                // through a strip of figures that sits right above a row of solid tiles.
                val shape = RoundedCornerShape(Radii.md)
                val border = if (d.isToday) MaterialTheme.colorScheme.primary else LocalTradeColors.current.sheen
                Column(
                    modifier = (if (compact) Modifier.widthIn(min = 92.dp) else Modifier.weight(1f))
                        .height(84.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, border, shape)
                        .padding(Space.md),
                ) {
                    Text(
                        "${d.day} ${d.dow}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatMoney(d.pnl), // exact figure, no abbreviation/round-up
                        color = pnlColor(d.pnl),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Text(
                        tradeCount(d.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

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

// Every canvas chart uses this left gutter for $ tick labels; hit-testing needs the same value.
// ponytail: 84px fits two-decimal k/M abbreviations at 14sp without clipping.
private const val LEFT_PAD = 84f

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

/** Vertical-bar hit-test: which of [n] equal slots (after the left gutter) the cursor x sits in. */
private fun barIndexAt(pos: Offset, box: IntSize, n: Int): Int {
    val plotW = box.width - LEFT_PAD
    if (n <= 0 || plotW <= 0f || pos.x < LEFT_PAD) return -1
    return ((pos.x - LEFT_PAD) / (plotW / n)).toInt().coerceIn(0, n - 1)
}

/** Line hit-test: the sample nearest the cursor x across [n] evenly spaced points. */
private fun lineIndexAt(pos: Offset, box: IntSize, n: Int): Int {
    val plotW = box.width - LEFT_PAD
    if (n < 1 || plotW <= 0f || pos.x < LEFT_PAD) return -1
    if (n == 1) return 0
    return (((pos.x - LEFT_PAD) / plotW) * (n - 1)).roundToInt().coerceIn(0, n - 1)
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
    HoverChart(
        modifier = canvasModifier,
        itemCount = series.size,
        indexAt = { pos, sz -> lineIndexAt(pos, sz, series.size) },
        tooltip = { i -> LineTooltip(dates.getOrElse(i) { "" }, series[i]) },
    ) { hoverIdx ->
        val leftPad = LEFT_PAD // shared with the hover hit-test — drift here breaks tooltips
        val bottomPad = if (xLabel.isEmpty()) 28f else 48f
        val plotW = size.width - leftPad
        val plotH = size.height - bottomPad
        val n = series.size
        // Axis spans nice round ticks (not the raw data bounds) so every gridline label is a
        // clean integer-ish value instead of "4.81".
        val (loRaw, hiRaw) = zeroCenteredBounds(series)
        val ticks = niceTicks(loRaw, hiRaw)
        val minV = ticks.first()
        val maxV = ticks.last()
        val range = (maxV - minV).let { if (it == 0f) 1f else it }
        fun px(i: Int) = leftPad + plotW * i / (n - 1)
        fun py(v: Float) = plotH * (1f - (v - minV) / range)

        for (t in ticks) {
            val y = py(t)
            drawLine(grid.copy(alpha = 0.35f), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val lay = measurer.measure(axisLabel(t, money = true), TextStyle(fontSize = FontSize.sm, color = labelColor))
            drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
        }
        drawLine(grid, Offset(leftPad, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1.5f)

        val path = Path().apply {
            moveTo(px(0), py(series[0]))
            for (i in 1 until n) lineTo(px(i), py(series[i]))
        }
        if (negColor == null) {
            val base = if (fillToBottom) plotH else py(0f)
            val fill = Path().apply { addPath(path); lineTo(px(n - 1), base); lineTo(px(0), base); close() }
            drawPath(fill, color = line.copy(alpha = 0.16f))
            drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
        } else {
            // Sign-colored equity curve: fill/line to the zero line, [line] above it, [negColor] below.
            val zeroY = py(0f)
            val fill = Path().apply { addPath(path); lineTo(px(n - 1), zeroY); lineTo(px(0), zeroY); close() }
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
            val hx = px(hoverIdx); val hy = py(series[hoverIdx])
            drawLine(grid, Offset(hx, 0f), Offset(hx, plotH), strokeWidth = 1f)
            drawCircle(dotRing, radius = 5.5.dp.toPx(), center = Offset(hx, hy))
            drawCircle(if (negColor != null && series[hoverIdx] < 0f) negColor else line, radius = 4.dp.toPx(), center = Offset(hx, hy))
        }

        val xn = minOf(5, n)
        if (xn >= 2) for (k in 0 until xn) {
            val i = k * (n - 1) / (xn - 1)
            val lay = measurer.measure(dates.getOrElse(i) { "" }, TextStyle(fontSize = FontSize.sm, color = labelColor))
            val x = (px(i) - lay.size.width / 2f).coerceIn(leftPad, size.width - lay.size.width)
            drawText(lay, topLeft = Offset(x, plotH + 6f))
        }
        drawAxisTitle(measurer, xLabel, labelColor, leftPad)
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
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(Space.lg)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.lg))
            if (points.isEmpty()) {
                Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                HoverChart(
                    modifier = chartModifier(fillHeight, 190.dp),
                    itemCount = points.size,
                    indexAt = { pos, sz -> barIndexAt(pos, sz, points.size) },
                    tooltip = { i -> BarTooltip(title, points[i], money = diverging) },
                ) { hoverIdx ->
                    val leftPad = LEFT_PAD // shared with the hover hit-test — drift here breaks tooltips
                    val bottomPad = if (xLabel.isEmpty()) 28f else 48f
                    val plotW = size.width - leftPad
                    val plotH = size.height - bottomPad
                    val n = points.size
                    val values = points.map { it.value }
                    // Nice round tick values as the axis bounds — labels read "5", not "4.81".
                    val (loRaw, hiRaw) = if (diverging) zeroCenteredBounds(values) else (0f to maxOf(0f, values.max()))
                    val ticks = niceTicks(loRaw, hiRaw)
                    val minV = ticks.first()
                    val maxV = ticks.last()
                    val range = (maxV - minV).let { if (it == 0f) 1f else it }
                    fun py(v: Float) = plotH * (1f - (v - minV) / range)

                    for (t in ticks) {
                        val y = py(t)
                        drawLine(grid.copy(alpha = 0.35f), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
                        val lay = measurer.measure(axisLabel(t, diverging), TextStyle(fontSize = FontSize.sm, color = labelColor))
                        drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
                    }
                    val zeroY = py(0f)
                    val slot = plotW / n
                    // ponytail: cap width so a 2–3 bar chart draws bars, not fat blocks.
                    val barW = minOf(slot * 0.6f, 48.dp.toPx())
                    for (i in 0 until n) {
                        val v = values[i]
                        val cx = leftPad + slot * (i + 0.5f)
                        val top = py(maxOf(v, 0f))
                        val bot = py(minOf(v, 0f))
                        val c = if (diverging) (if (v >= 0f) colors.gain else colors.loss) else barColor
                        drawRect(color = c, topLeft = Offset(cx - barW / 2f, minOf(top, bot)), size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(bot - top).coerceAtLeast(1f)))
                    }
                    drawLine(grid, Offset(leftPad, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)

                    if (hoverIdx in 0 until n) {
                        val v = values[hoverIdx]
                        val cx = leftPad + slot * (hoverIdx + 0.5f)
                        val top = py(maxOf(v, 0f)); val bot = py(minOf(v, 0f))
                        drawRect(hoverTint, topLeft = Offset(cx - barW / 2f, minOf(top, bot)), size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(bot - top).coerceAtLeast(1f)))
                    }

                    // Every bar labeled (years/months) or evenly sampled to ≤6 (dense day/date axes).
                    val xIdx = if (labelEveryBar) (0 until n).toList()
                        else minOf(6, n).let { xn -> if (xn < 2) emptyList() else (0 until xn).map { it * (n - 1) / (xn - 1) } }
                    for (i in xIdx) {
                        val lay = measurer.measure(points[i].label, TextStyle(fontSize = FontSize.sm, color = labelColor))
                        val x = (leftPad + slot * (i + 0.5f) - lay.size.width / 2f).coerceIn(leftPad, size.width - lay.size.width)
                        drawText(lay, topLeft = Offset(x, plotH + 6f))
                    }
                    drawAxisTitle(measurer, xLabel, labelColor, leftPad)
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
            val plotBottom = size.height - axisPx
            val labelStyle = TextStyle(fontSize = FontSize.sm, color = labelColor)
            val tickStyle = TextStyle(fontSize = FontSize.sm, color = labelColor)
            val maxLabelW = buckets.maxOf { measurer.measure(it.label, labelStyle).size.width }
            val leftPad = (maxLabelW + 14f).coerceAtMost(size.width * 0.42f)
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
                drawText(lab, topLeft = Offset(lx, plotBottom + 6f))
            }
            if (performance) drawLine(zeroLine, Offset(zeroX, 0f), Offset(zeroX, plotBottom), strokeWidth = 1.5f)
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
                drawText(lab, topLeft = Offset((leftPad - lab.size.width - 6f).coerceAtLeast(0f), cy - lab.size.height / 2f))
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
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(title, style = cardTitleStyle(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.lg))
            if (n < 2) {
                Text("Not enough data to create this chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                HoverChart(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    itemCount = n,
                    indexAt = { pos, sz -> lineIndexAt(pos, sz, n) },
                    tooltip = { i -> MultiLineTooltip(dates.getOrElse(i) { "" }, series, i) },
                ) { hoverIdx ->
                    val leftPad = LEFT_PAD // shared with the hover hit-test — drift here breaks tooltips
                    val bottomPad = if (xLabel.isEmpty()) 28f else 48f
                    val plotW = size.width - leftPad
                    val plotH = size.height - bottomPad
                    val all = series.flatMap { it.values.filterNotNull() }
                    // Nice round tick values as the axis bounds — labels read "200", not "173.91".
                    val (loRaw, hiRaw) = zeroCenteredBounds(all)
                    val ticks = niceTicks(loRaw, hiRaw)
                    val minV = ticks.first()
                    val maxV = ticks.last()
                    val range = (maxV - minV).let { if (it == 0f) 1f else it }
                    fun px(i: Int) = leftPad + plotW * i / (n - 1)
                    fun py(v: Float) = plotH * (1f - (v - minV) / range)

                    for (t in ticks) {
                        val y = py(t)
                        drawLine(grid.copy(alpha = 0.35f), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
                        val lay = measurer.measure(axisLabel(t, money = true), TextStyle(fontSize = FontSize.sm, color = labelColor))
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

                    if (hoverIdx in 0 until n) {
                        val hx = px(hoverIdx)
                        drawLine(grid, Offset(hx, 0f), Offset(hx, plotH), strokeWidth = 1f)
                        for (sp in series) {
                            val v = sp.values.getOrNull(hoverIdx) ?: continue
                            drawCircle(dotRing, radius = 5.dp.toPx(), center = Offset(hx, py(v)))
                            drawCircle(sp.color, radius = 3.5.dp.toPx(), center = Offset(hx, py(v)))
                        }
                    }

                    val xn = minOf(5, n)
                    if (xn >= 2) for (k in 0 until xn) {
                        val i = k * (n - 1) / (xn - 1)
                        val lay = measurer.measure(dates.getOrElse(i) { "" }, TextStyle(fontSize = FontSize.sm, color = labelColor))
                        val x = (px(i) - lay.size.width / 2f).coerceIn(leftPad, size.width - lay.size.width)
                        drawText(lay, topLeft = Offset(x, plotH + 6f))
                    }
                    drawAxisTitle(measurer, xLabel, labelColor, leftPad)
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

/** Mock's "Average … Trade" widgets. Money averages — the app's one sanctioned money division. */
@Composable
internal fun AveragesCard(avg: Averages, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
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
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
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
    // No data ≠ all losses: a neutral ring + "—" until there's a decided trade to classify.
    val ring = if (decided > 0) colors.loss else MaterialTheme.colorScheme.surfaceVariant
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val stroke = 12.dp.toPx()
                        drawArc(
                            color = ring,
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
                        if (decided > 0) "${(win.winRate * 100).roundToInt()}%" else "—",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    LegendRow(colors.gain, "Winners", win.wins)
                    LegendRow(colors.loss, "Losers", win.losses)
                    LegendRow(MaterialTheme.colorScheme.onSurfaceVariant, "Scratch", win.breakeven)
                }
            }
        }
    }
}

@Composable
internal fun LegendRow(dot: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.size(8.dp).background(dot, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
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
 * TraderVue-style performance row: label on the left, $ value + % on the right, and a thin
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
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
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs).height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
        ) {
            Box(Modifier.fillMaxWidth(frac).height(4.dp).background(barColor, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
internal fun SectionCard(title: String, modifier: Modifier = Modifier, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = (if (fill) Modifier.fillMaxHeight() else Modifier).padding(Space.lg)) {
            Text(
                title,
                style = cardTitleStyle(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Space.md),
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
        if (buckets.isEmpty()) Text("No trades in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Arrow("‹", "Previous page", page > 0) { onPage(page - 1) }
        Text(
            "${page + 1} / $count",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Space.sm),
        )
        Arrow("›", "Next page", page < count - 1) { onPage(page + 1) }
    }
}

@Composable
private fun Arrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Radii.md)).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description } // the ‹/› glyphs say nothing to a reader
            .padding(horizontal = Space.sm, vertical = 2.dp),
    )
}

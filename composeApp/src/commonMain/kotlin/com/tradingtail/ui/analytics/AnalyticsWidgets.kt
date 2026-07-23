package com.tradingtail.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Stat cards and rows (week strip, averages, winners, magnitude bars) shared across the
// analytics screens. Charts live in Charts.kt, controls in Controls.kt. All `internal`.
// ---------------------------------------------------------------------------------------------

/** Preview's top week strip: last 7 Bangkok days, each cell a day's realized P&L + trade count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekStrip(days: List<WeekDay>, rangeLabel: String) {
    val compact = LocalCompact.current

    // Seven cells wide enough for an exact figure is ~700dp of content on a 411dp phone, so this used
    // to scroll — and it opened scrolled to today, because at position zero the one figure the user
    // came for sat ~340dp off-screen. That fixed the default view but not the problem: three days were
    // always unreachable without a sideways swipe nothing advertised. Wrapping 4+3 shows the whole week
    // at once; a wrapped cell is ~91dp, the width the min was protecting, so the figure still doesn't
    // clip. Chronological order survives (reversing it would put Saturday before Friday).
    val perRow = if (compact) 4 else days.size

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(rangeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            maxItemsInEachRow = perRow,
        ) {
            for (d in days) {
                // Opaque like every other data surface — a bare outline here left the aurora showing
                // through a strip of figures that sits right above a row of solid tiles.
                val shape = RoundedCornerShape(Radii.md)
                val border = if (d.isToday) MaterialTheme.colorScheme.primary else LocalTradeColors.current.sheen
                Column(
                    modifier = Modifier.weight(1f)
                        // heightIn(min=), not height(): a hard 84dp box over three stacked Text lines
                        // clipped its own contents once the user's font scale grew. Measured on a Pixel 9
                        // at scale 1.5, the "N trades" line vanished from the view hierarchy entirely.
                        // Text scales with the system setting (sp); a fixed dp container doesn't.
                        .heightIn(min = 84.dp)
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
            // Fill the last row's empty slots so a wrapped week stays a grid: without these, FlowRow
            // shares row 2 between 3 cells and they render wider than row 1's 4.
            if (days.size % perRow != 0) repeat(perRow - days.size % perRow) { Spacer(Modifier.weight(1f)) }
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

/** Label left, mono-bold figure right — the shared row shape of every dense stat tile. */
@Composable
internal fun LabeledFigureRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
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

@Composable
private fun DurationRow(label: String, ms: Long?) = LabeledFigureRow(label, ms?.let { formatDuration(it) } ?: "—")

/**
 * Share-of-total donut ring: [part] sweeps gain-green over a loss-red remainder. No data ≠ all
 * losses — the ring stays neutral until there's something to classify.
 */
@Composable
internal fun RatioRing(part: Int, total: Int) {
    val colors = LocalTradeColors.current
    val ring = if (total > 0) colors.loss else MaterialTheme.colorScheme.surfaceVariant
    val reveal = rememberReveal()
    Canvas(modifier = Modifier.size(72.dp)) {
        val stroke = 12.dp.toPx()
        drawArc(ring, -90f, 360f, false, style = Stroke(stroke))
        if (total > 0) drawArc(colors.gain, -90f, 360f * part / total * EaseOutQuart.transform(reveal), false, style = Stroke(stroke))
    }
}

/** Winners-vs-losers donut with the win rate in the hole. */
@Composable
internal fun WinnersCard(win: WinRateSummary, modifier: Modifier = Modifier, title: String = "Winning vs Losing Trades") {
    val colors = LocalTradeColors.current
    val decided = win.wins + win.losses
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RatioRing(win.wins, decided)
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
private fun FigureRow(label: String, value: BigDecimal) = LabeledFigureRow(label, formatMoney(value), pnlColor(value))

/**
 * TraderVue-style performance row: label on the left, $ value + % on the right, and a thin
 * left-anchored magnitude bar underneath (length ∝ |P&L| over the section max, colored by sign).
 */
@Composable
internal fun BarRow(label: String, pnl: BigDecimal, max: BigDecimal, percent: Float? = null) {
    val colors = LocalTradeColors.current
    val frac = fraction(pnl, max) * EaseOutQuart.transform(rememberReveal()) // bar grows from zero
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

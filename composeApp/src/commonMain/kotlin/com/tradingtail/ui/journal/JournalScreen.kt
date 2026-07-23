package com.tradingtail.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.tradingtail.ui.theme.GlassCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkDate
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.formatMoney
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.ui.theme.FAB_CLEARANCE
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import kotlinx.coroutines.flow.Flow

/** UI reaches the repo/usecase only through here (per the architecture rule). */
class JournalViewModel(repo: TradeRepository) {
    val trades: Flow<List<TradeEntity>> = repo.allFlow()
}

/**
 * Home screen — reactive list of matched round-trip trades, grouped by Bangkok day.
 *
 * [selectedAnchor] is the execution id the desktop detail panel is showing, or null when the detail
 * isn't beside the list (mobile pushes it full-screen instead, where there's no list left to correlate
 * a highlight against). An EXECUTION id, not a trade id, for the same reason the panel is anchored on
 * one: trade rows are re-derived — and re-keyed — every time a fill is corrected.
 */
@Composable
fun JournalScreen(
    vm: JournalViewModel,
    onOpenTrade: (TradeEntity) -> Unit,
    onNewTrade: () -> Unit,
    selectedAnchor: Long? = null,
    modifier: Modifier = Modifier,
) {
    // initial = null, not emptyList(): "no trades yet" and "haven't asked the database yet" are
    // different facts, and conflating them made the first frame of every session assert the user had
    // no trades. In a product whose promise is trusting the figures, the empty state must be an
    // answer, never a placeholder. Nothing renders during the (single-digit-millisecond, local
    // SQLite) gap — a skeleton for a frame or two would only flash.
    val trades by vm.trades.collectAsState(initial = null)
    val loaded = trades ?: return

    if (loaded.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No trades yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Record a closed trade — entry, exit, quantity — and it lands here with its P&L worked out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.lg),
            )
            // An empty state's whole job is naming the next action, and this one deliberately named
            // neither path ("Record your first trade to get started") to stay device-neutral — buying
            // neutrality with the one thing it existed to say. The button IS the path, on both
            // platforms, so there's nothing left to be neutral about.
            Button(onClick = onNewTrade) { Text("Record a trade") }
            Text(
                "Or import a Webull statement to backfill.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.md),
            )
        }
        return
    }

    // Grouped by trade day (entry/open day); trades arrive newest-first (DAO orders by exit DESC).
    val byDay = loaded.groupBy { bkkDate(it.entryTimestamp) }.toList()

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compact = maxWidth < 600.dp
        // A journal is chronological (newest day first), so it stays a single column — a multi-column
        // grid scrambles the reading order. On a wide desktop window the list would otherwise stretch
        // every row across ~1100dp, floating the symbol and its P&L to opposite edges with dead gutter
        // between; the cap holds rows at a comfortable reading width and centers them instead. Same
        // move the Calendar month card uses. (widthIn.fillMaxWidth: cap when wide, fill when narrow.)
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight()
                .padding(if (compact) Space.sm else Space.lg),
            // Bottom clearance so the FAB (56dp + margin) and the Import pill stop landing on the last
            // row's figure — Scaffold's innerPadding covers the bars but never the FAB. The old 48dp of
            // TOP padding is gone with the pill that needed it (App.kt).
            contentPadding = PaddingValues(bottom = if (compact) FAB_CLEARANCE else 0.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            byDay.forEach { (day, dayTrades) ->
                val subtotal = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
                item(key = "h-$day") { DayHeader(day, subtotal) }
                items(dayTrades, key = { it.id }) { trade ->
                    TradeRow(
                        trade,
                        // Containment, not `entryExecutionIds.first() == anchor`: the same test the
                        // detail's own ViewModel uses to find its trade, so the highlight can't drift
                        // from the panel when a correction reshuffles FIFO matching.
                        selected = selectedAnchor != null &&
                            (selectedAnchor in trade.entryExecutionIds || selectedAnchor in trade.exitExecutionIds),
                        onClick = { onOpenTrade(trade) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: BkkDate, subtotal: BigDecimal) {
    Row(
        // end inset = TradeRow's own end padding, so the subtotal sits on the same column edge as the
        // trade P&L figures below it (money columns align to the digit). Was 56dp when the row carried
        // a 48dp delete button; that button now lives on the trade detail screen, so the figures moved
        // right and this moved with them. Keep these two in step.
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xs, end = Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${day.year}-${pad(day.month)}-${pad(day.day)}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatMoney(subtotal),
            color = pnlColor(subtotal),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * One round-trip. The whole row opens the trade — the record is the point, so inspect is the row's
 * affordance and delete lives inside, next to the fills it destroys. It used to be the reverse: a
 * 48dp delete button was the only thing a row could do.
 */
@Composable
private fun TradeRow(trade: TradeEntity, selected: Boolean, onClick: () -> Unit) {
    // Accent wash at 0.15 — the same selection idiom as the sidebar's NavItem. Painted on the INNER
    // column so the card's own clip rounds it; a background on GlassCard's modifier would land outside
    // the clip and square off the corners.
    val bg = if (selected) LocalTradeColors.current.accent.copy(alpha = 0.15f) else Color.Transparent
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().background(bg).clickable(onClick = onClick)
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // weight + maxLines on the symbol, not on the money: an option symbol
                // (TSLA240119C00250000) would otherwise measure first and squeeze the P&L figure
                // toward zero width. The figure is the one thing that must never be clipped.
                Text(
                    trade.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = Space.sm),
                )
                Text(
                    formatMoney(trade.realizedPnl),
                    color = pnlColor(trade.realizedPnl),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                "${formatBangkok(trade.entryTimestamp)}  →  ${formatBangkok(trade.exitTimestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun pad(n: Int): String = n.toString().padStart(2, '0')

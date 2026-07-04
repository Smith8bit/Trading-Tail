package com.tradingtail.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkDate
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.formatMoney
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.DeleteTrade
import com.tradingtail.ui.theme.pnlColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** UI reaches the repo/usecase only through here (per the architecture rule). */
class JournalViewModel(repo: TradeRepository, private val deleteTrade: DeleteTrade) {
    val trades: Flow<List<TradeEntity>> = repo.allFlow()
    suspend fun delete(trade: TradeEntity) = deleteTrade(trade)
}

/** Home screen — reactive list of matched round-trip trades, grouped by Bangkok day. */
@Composable
fun JournalScreen(vm: JournalViewModel, modifier: Modifier = Modifier) {
    val trades by vm.trades.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (trades.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No trades yet", style = MaterialTheme.typography.titleMedium)
            Text("Tap ＋ to record your first.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Trades arrive newest-first (DAO orders by exit DESC); groupBy keeps that order per day.
    val byDay = trades.groupBy { bkkDate(it.exitTimestamp) }.toList()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        byDay.forEach { (day, dayTrades) ->
            val subtotal = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
            item(key = "h-$day") { DayHeader(day, subtotal) }
            items(dayTrades, key = { it.id }) { trade ->
                TradeRow(trade, onDelete = { scope.launch { vm.delete(trade) } })
            }
        }
    }
}

@Composable
private fun DayHeader(day: BkkDate, subtotal: BigDecimal) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
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

@Composable
private fun TradeRow(trade: TradeEntity, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(trade.symbol, fontWeight = FontWeight.Bold)
                    Text(
                        formatMoney(trade.realizedPnl),
                        color = pnlColor(trade.realizedPnl),
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; confirmOpen = true },
                    )
                }
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Delete trade?") },
            text = { Text("${trade.symbol} ${formatMoney(trade.realizedPnl)} — this can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmOpen = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancel") } },
        )
    }
}

private fun pad(n: Int): String = n.toString().padStart(2, '0')

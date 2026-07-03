package com.tradingtail.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.formatBangkok
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.DeleteTrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** UI reaches the repo/usecase only through here (per the architecture rule). */
class JournalViewModel(repo: TradeRepository, private val deleteTrade: DeleteTrade) {
    val trades: Flow<List<TradeEntity>> = repo.allFlow()
    suspend fun delete(trade: TradeEntity) = deleteTrade(trade)
}

/** Home screen — reactive list of matched round-trip trades. Proves the entry -> match -> list loop. */
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
            Text(
                "Record one from Quick Entry.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(trades, key = { it.id }) { trade ->
            TradeRow(trade, onDelete = { scope.launch { vm.delete(trade) } })
        }
    }
}

@Composable
private fun TradeRow(trade: TradeEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(
                    trade.symbol + "  " + trade.direction.name,
                    "P&L " + trade.realizedPnl.toString(),
                )
                Text(
                    "entry ${formatBangkok(trade.entryTimestamp)}  →  exit ${formatBangkok(trade.exitTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun Row(left: String, right: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(left, fontWeight = FontWeight.Bold)
        Text(right, fontWeight = FontWeight.Bold)
    }
}

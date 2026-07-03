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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.formatBangkok
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeRepository

/** Home screen — reactive list of matched round-trip trades. Proves the entry -> match -> list loop. */
@Composable
fun JournalScreen(repo: TradeRepository, modifier: Modifier = Modifier) {
    val trades by repo.allFlow().collectAsState(initial = emptyList())

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
        items(trades, key = { it.id }) { TradeRow(it) }
    }
}

@Composable
private fun TradeRow(trade: TradeEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                trade.symbol + "  " + trade.direction.name,
                "P&L " + trade.realizedPnl.toString(),
            )
            Text(
                "entry ${formatBangkok(trade.entryTimestamp)}  →  exit ${formatBangkok(trade.exitTimestamp)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Row(left: String, right: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(left, fontWeight = FontWeight.Bold)
        Text(right, fontWeight = FontWeight.Bold)
    }
}

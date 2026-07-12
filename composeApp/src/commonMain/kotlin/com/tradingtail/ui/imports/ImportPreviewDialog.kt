package com.tradingtail.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.CURRENCY
import com.tradingtail.data.imports.ParsedFill
import com.tradingtail.data.local.entity.Side
import com.tradingtail.ui.theme.LocalTradeColors

/**
 * Preview of the fills parsed from a statement PDF, shown before anything is written. The user confirms
 * (or cancels) — a deliberate gate on a money import, since the executions become trades on commit.
 */
@Composable
fun ImportPreviewContent(
    fills: List<ParsedFill>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Import statement", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
        }

        val symbols = fills.map { it.symbol }.distinct().size
        Text(
            if (fills.isEmpty()) "No trades found in this PDF." else "${fills.size} fills · $symbols symbols",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (fills.isNotEmpty()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
            ) {
                fills.forEach { FillRow(it) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onConfirm, enabled = fills.isNotEmpty()) { Text("Import ${fills.size}") }
        }
    }
}

@Composable
private fun FillRow(f: ParsedFill) {
    val tc = LocalTradeColors.current
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.symbol, style = mono.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(56.dp))
        Text(
            f.side.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (f.side == Side.BUY) tc.gain else tc.loss,
            modifier = Modifier.width(40.dp),
        )
        Text(
            "${f.quantity} @ $CURRENCY${f.price}",
            style = mono,
            modifier = Modifier.weight(1f),
        )
        Text(
            f.bangkokDateTime.substring(5).dropLast(3), // "yyyy-MM-dd HH:mm:ss" → "MM-dd HH:mm"
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

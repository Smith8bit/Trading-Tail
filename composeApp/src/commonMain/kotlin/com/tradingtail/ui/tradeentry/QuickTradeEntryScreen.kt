package com.tradingtail.ui.tradeentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tradingtail.common.ZERO
import com.tradingtail.common.bigDecimal
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.nowMillis
import com.tradingtail.common.parseBangkok
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.domain.usecase.RecordQuickTrade
import kotlinx.coroutines.launch

/**
 * Plain state holder — parses raw form text and delegates to the RecordQuickTrade usecase.
 * runCatching turns both parse failures (NumberFormatException) and validator rejections
 * (IllegalArgumentException from ExecutionValidator) into a surfaceable message; nothing crashes.
 */
class QuickTradeEntryViewModel(private val record: RecordQuickTrade) {
    suspend fun submit(
        symbol: String,
        quantity: String,
        entryPrice: String,
        exitPrice: String,
        entryTimestamp: String,
        exitTimestamp: String,
        entryFees: String,
        exitFees: String,
    ): Result<Unit> = runCatching {
        record(
            symbol = symbol,
            direction = Direction.LONG, // v1: long positions only
            quantity = bigDecimal(quantity.trim()),
            entryPrice = bigDecimal(entryPrice.trim()),
            exitPrice = bigDecimal(exitPrice.trim()),
            entryTimestamp = parseBangkok(entryTimestamp),
            exitTimestamp = parseBangkok(exitTimestamp),
            entryFees = if (entryFees.isBlank()) ZERO else bigDecimal(entryFees.trim()),
            exitFees = if (exitFees.isBlank()) ZERO else bigDecimal(exitFees.trim()),
        )
    }
}

@Composable
fun QuickTradeEntryScreen(vm: QuickTradeEntryViewModel, modifier: Modifier = Modifier) {
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var entryPrice by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    // ponytail: Bangkok-local text fields ("yyyy-MM-dd HH:mm"), prefilled with now. Swap for a real
    // date/time picker later — no picker lib is pulled in for Stage 1.
    var entryTs by remember { mutableStateOf(formatBangkok(nowMillis() - 3_600_000)) }
    var exitTs by remember { mutableStateOf(formatBangkok(nowMillis())) }
    var entryFees by remember { mutableStateOf("") }
    var exitFees by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Quick Trade Entry", style = MaterialTheme.typography.titleLarge)
        // ponytail: long-only (v1) — direction is always LONG, so no direction picker. Single quantity:
        // a quick trade is a full round-trip (entry qty == exit qty).
        Field(symbol, { symbol = it }, "Symbol")
        Field(quantity, { quantity = it }, "Quantity")
        Field(entryPrice, { entryPrice = it }, "Entry price")
        Field(exitPrice, { exitPrice = it }, "Exit price")
        Field(entryTs, { entryTs = it }, "Entry time (Bangkok, yyyy-MM-dd HH:mm)")
        Field(exitTs, { exitTs = it }, "Exit time (Bangkok, yyyy-MM-dd HH:mm)")
        Field(entryFees, { entryFees = it }, "Entry fee (optional)")
        Field(exitFees, { exitFees = it }, "Exit fee (optional)")

        Button(
            onClick = {
                scope.launch {
                    val result = vm.submit(symbol, quantity, entryPrice, exitPrice, entryTs, exitTs, entryFees, exitFees)
                    status = result.fold(
                        onSuccess = {
                            symbol = ""; quantity = ""; entryPrice = ""; exitPrice = ""
                            entryFees = ""; exitFees = ""
                            "Saved ✓"
                        },
                        onFailure = { it.message ?: "Invalid input" },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record trade") }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

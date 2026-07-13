package com.tradingtail.ui.tradeentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.tradingtail.common.ZERO
import com.tradingtail.common.bigDecimal
import com.tradingtail.common.bkkDate
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.nowMillis
import com.tradingtail.common.parseBangkok
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.domain.usecase.RecordQuickTrade
import com.tradingtail.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Plain state holder — parses raw form text and delegates to the RecordQuickTrade usecase.
 * runCatching turns parse failures + validator rejections into a surfaceable message; nothing crashes.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTradeEntryScreen(
    vm: QuickTradeEntryViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var entryPrice by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    var entryTs by remember { mutableStateOf(formatBangkok(nowMillis() - 3_600_000)) }
    var exitTs by remember { mutableStateOf(formatBangkok(nowMillis())) }
    var entryFees by remember { mutableStateOf("") }
    var exitFees by remember { mutableStateOf("") }

    var symbolErr by remember { mutableStateOf<String?>(null) }
    var qtyErr by remember { mutableStateOf<String?>(null) }
    var entryErr by remember { mutableStateOf<String?>(null) }
    var exitErr by remember { mutableStateOf<String?>(null) }
    var entryTsErr by remember { mutableStateOf<String?>(null) }
    var exitTsErr by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val symbolFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { symbolFocus.requestFocus() }

    // ponytail: hosted in a Dialog by the caller (App.kt), so no Scaffold/TopAppBar — just a compact
    // header + a scrolling field area. `weight(1f, fill = false)` lets the card wrap short forms yet
    // scroll internally once it hits the dialog's heightIn cap. `imePadding` keeps the fields/button
    // above the on-screen keyboard instead of letting the fixed-height dialog hide them behind it.
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New trade", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        Column(
            modifier = Modifier.verticalScroll(scrollState).weight(1f, fill = false).imePadding(),
            verticalArrangement = Arrangement.spacedBy(if (compact) Space.sm else Space.md),
        ) {
            // ponytail: long-only (v1) — direction is always LONG, so no direction picker. Single quantity:
            // a quick trade is a full round-trip (entry qty == exit qty).
            Field(
                symbol, { symbol = it }, "Symbol", error = symbolErr,
                capitalizeWords = true, focusRequester = symbolFocus,
            )
            Field(quantity, { quantity = it }, "Quantity", keyboardType = KeyboardType.Decimal, error = qtyErr)
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm)) {
                Field(entryPrice, { entryPrice = it }, "Entry price", Modifier.weight(1f), KeyboardType.Decimal, entryErr)
                DateTimeField(entryTs, { entryTs = it }, "Entry time", Modifier.weight(1f), entryTsErr)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm)) {
                Field(exitPrice, { exitPrice = it }, "Exit price", Modifier.weight(1f), KeyboardType.Decimal, exitErr)
                DateTimeField(exitTs, { exitTs = it }, "Exit time", Modifier.weight(1f), exitTsErr)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm)) {
                Field(entryFees, { entryFees = it }, "Entry fee", Modifier.weight(1f), KeyboardType.Decimal)
                Field(exitFees, { exitFees = it }, "Exit fee", Modifier.weight(1f), KeyboardType.Decimal, imeAction = ImeAction.Done)
            }

            Button(
                onClick = {
                    symbolErr = if (symbol.isBlank()) "Required" else null
                    qtyErr = numError(quantity)
                    entryErr = numError(entryPrice)
                    exitErr = numError(exitPrice)
                    entryTsErr = dateError(entryTs)
                    exitTsErr = dateError(exitTs)
                    if (listOf(symbolErr, qtyErr, entryErr, exitErr, entryTsErr, exitTsErr).all { it == null }) {
                        scope.launch {
                            status = vm.submit(symbol, quantity, entryPrice, exitPrice, entryTs, exitTs, entryFees, exitFees)
                                .fold(onSuccess = { onSaved(); null }, onFailure = { it.message ?: "Invalid input" })
                        }
                    } else {
                        scope.launch { scrollState.animateScrollTo(0) } // first error (Symbol) may be scrolled out of view
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            ) { Text("Record trade") }

            status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    capitalizeWords: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = if (capitalizeWords) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier,
    )
}

/** Editable Bangkok datetime text (fallback) with a calendar icon that launches date + time pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, error: String? = null) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        trailingIcon = {
            IconButton(onClick = { showDate = true }) { Icon(Icons.Filled.DateRange, contentDescription = "Pick date/time") }
        },
        modifier = modifier,
    )

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { showDate = false; showTime = true }) { Text("Next") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    dateState.selectedDateMillis?.let { ms ->
                        // ponytail: reuse bkkDate for the picked calendar day (00:00 UTC → 07:00 same day BKK),
                        // combine with the picked hour:minute, format to the string parseBangkok already reads.
                        val d = bkkDate(ms)
                        onChange("${d.year}-${pad(d.month)}-${pad(d.day)} ${pad(timeState.hour)}:${pad(timeState.minute)}")
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

private fun numError(s: String): String? {
    val v = runCatching { bigDecimal(s.trim()) }.getOrNull() ?: return "Enter a number"
    return if (v > ZERO) null else "Must be > 0"
}

private fun dateError(s: String): String? =
    if (runCatching { parseBangkok(s) }.isSuccess) null else "Invalid date/time — use YYYY-MM-DD HH:MM"

private fun pad(n: Int): String = n.toString().padStart(2, '0')

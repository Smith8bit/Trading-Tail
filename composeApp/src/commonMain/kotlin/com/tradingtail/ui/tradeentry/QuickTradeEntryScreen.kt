package com.tradingtail.ui.tradeentry

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BackHandler
import com.tradingtail.common.ZERO
import com.tradingtail.common.bigDecimal
import com.tradingtail.common.bkkDate
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuickTradeEntryScreen(
    vm: QuickTradeEntryViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // rememberSaveable with the form's own Saver: the fields are hand-typed money, entered one-handed
    // right after a trade closes, and plain `remember` lost the lot on an Activity recreation. State
    // and validation live in [QuickTradeForm]; this composable is layout + effects only.
    val form = rememberSaveable(saver = QuickTradeForm.Saver) { QuickTradeForm() }
    var submitting by remember { mutableStateOf(false) } // in-flight only; never restored mid-write
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    // Back exits the form, but never silently drops typed money — that was the app's worst bug: an
    // outside tap on the old Dialog discarded all eight fields with no confirmation and no recovery.
    BackHandler(enabled = true) { if (form.dirty) confirmDiscard = true else onBack() }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val symbolFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { symbolFocus.requestFocus() }

    // One requester per form row so a failed submit scrolls the *first failing* field into view,
    // not always the top (which would hide any error below Symbol).
    val symbolBiv = remember { BringIntoViewRequester() }
    val qtyBiv = remember { BringIntoViewRequester() }
    val entryRowBiv = remember { BringIntoViewRequester() }
    val exitRowBiv = remember { BringIntoViewRequester() }
    val feesRowBiv = remember { BringIntoViewRequester() }

    // Hoisted so both the button and the keyboard's Done key run the same path — two ways to submit
    // must not be two implementations of submitting.
    val submit: () -> Unit = {
        if (form.validate()) {
            submitting = true
            scope.launch {
                status = vm.submit(form.symbol, form.quantity, form.entryPrice, form.exitPrice, form.entryTs, form.exitTs, form.entryFees, form.exitFees)
                    .fold(onSuccess = { onSaved(); null }, onFailure = { it.message ?: "Invalid input" })
                submitting = false
            }
        } else {
            // Scroll the first failing field (top-to-bottom) into view, not always the top.
            val target = when {
                form.symbolErr != null -> symbolBiv
                form.qtyErr != null -> qtyBiv
                form.entryErr != null || form.entryTsErr != null -> entryRowBiv
                form.exitErr != null || form.exitTsErr != null -> exitRowBiv
                else -> feesRowBiv
            }
            scope.launch { target.bringIntoView() }
        }
    }

    // A pushed full-screen surface (App.kt), so no Scaffold/TopAppBar: header, a scrolling field area,
    // and a PINNED submit below it. Symbol autofocuses (capture in seconds), so the keyboard is up the
    // moment the form opens — with the button inside the scroll area it started life behind the IME,
    // and the primary action of the app's highest-traffic path was never visible on open. `imePadding`
    // on the outer Column lifts header + fields + button together, so the CTA rides above the keyboard
    // instead of under it.
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.lg).imePadding(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New trade", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { if (form.dirty) confirmDiscard = true else onBack() }) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        // weight(1f) — the fields take whatever's left after the header and the pinned submit, and
        // scroll inside it. (Was weight(1f, fill = false), which let the whole form wrap to content
        // inside the old Dialog's height cap; there's no cap on a full-screen surface.)
        Column(
            modifier = Modifier.verticalScroll(scrollState).weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compact) Space.sm else Space.md),
        ) {
            // ponytail: long-only (v1) — direction is always LONG, so no direction picker. Single quantity:
            // a quick trade is a full round-trip (entry qty == exit qty).
            Field(
                form.symbol, { form.symbol = it }, "Symbol",
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(symbolBiv),
                error = form.symbolErr, capitalizeWords = true, focusRequester = symbolFocus,
            )
            Field(
                form.quantity, { form.quantity = it }, "Quantity",
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(qtyBiv),
                keyboardType = KeyboardType.Decimal, error = form.qtyErr,
            )
            PriceTimeRow(
                compact, Modifier.bringIntoViewRequester(entryRowBiv),
                { Field(form.entryPrice, { form.entryPrice = it }, "Entry price", it, KeyboardType.Decimal, form.entryErr) },
                { DateTimeField(form.entryTs, { form.entryTs = it }, "Entry time", it, form.entryTsErr) },
            )
            PriceTimeRow(
                compact, Modifier.bringIntoViewRequester(exitRowBiv),
                { Field(form.exitPrice, { form.exitPrice = it }, "Exit price", it, KeyboardType.Decimal, form.exitErr) },
                { DateTimeField(form.exitTs, { form.exitTs = it }, "Exit time", it, form.exitTsErr) },
            )
            Row(
                modifier = Modifier.bringIntoViewRequester(feesRowBiv),
                horizontalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm),
            ) {
                Field(form.entryFees, { form.entryFees = it }, "Entry fee", Modifier.weight(1f), KeyboardType.Decimal, form.entryFeesErr)
                Field(
                    form.exitFees, { form.exitFees = it }, "Exit fee", Modifier.weight(1f), KeyboardType.Decimal,
                    form.exitFeesErr, imeAction = ImeAction.Done, onDone = submit,
                )
            }

        }

        // Pinned below the scroll area, so both are always on screen. The form-level error used to
        // render inside the scroll, under the button — a rejected submit could leave it off-screen and
        // the trade simply looked like it hadn't saved. Out here it can't hide, which is also why the
        // bring-into-view requester it needed is gone.
        status?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = submit,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Record trade")
            }
        }
    }

    // Only reachable with something typed — an untouched form closes silently.
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this trade?") },
            text = { Text("You've typed details for ${form.symbol.ifBlank { "this trade" }}. Closing now loses them.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDiscard = false; onBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

/**
 * A price and its timestamp: side by side on desktop, stacked when compact. A half-width field on a
 * 360dp phone gives the text ~87dp once the label pad and the picker icon take their cut, and a
 * "yyyy-MM-dd HH:mm" needs ~141dp — singleLine meant it just scrolled, showing half a timestamp at
 * rest. [modifier] carries the row's BringIntoViewRequester, so error-scroll still lands on the pair.
 */
@Composable
private fun PriceTimeRow(
    compact: Boolean,
    modifier: Modifier,
    price: @Composable (Modifier) -> Unit,
    time: @Composable (Modifier) -> Unit,
) {
    if (compact) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            price(Modifier.fillMaxWidth()); time(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            price(Modifier.weight(1f)); time(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    capitalizeWords: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
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
        // onDone was missing, so the last field's ImeAction.Done was a dead key: the keyboard showed a
        // Done button that did nothing, and the user had to dismiss the IME by hand to reach Submit.
        // Declaring an IME action without handling it is a promise the form doesn't keep.
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { onDone?.invoke() ?: focusManager.clearFocus() },
        ),
        modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier,
    )
}

/** Editable Bangkok datetime text (fallback) with a calendar icon that launches date + time pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, error: String? = null) {
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

private fun pad(n: Int): String = n.toString().padStart(2, '0')

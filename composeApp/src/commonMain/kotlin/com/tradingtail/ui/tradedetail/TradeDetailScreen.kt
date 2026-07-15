package com.tradingtail.ui.tradedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.bigDecimal
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.formatMoney
import com.tradingtail.common.parseBangkok
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.local.entity.naturalKey
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeNoteRepository
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.DeleteTrade
import com.tradingtail.domain.usecase.UpdateExecution
import com.tradingtail.ui.theme.GlassCard
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import com.tradingtail.ui.tradeentry.DateTimeField
import com.tradingtail.ui.tradeentry.Field
import com.tradingtail.ui.tradeentry.feeError
import com.tradingtail.ui.tradeentry.numError
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * UI reaches repos/usecases only through here (per the architecture rule).
 *
 * Anchored on an EXECUTION id, never a trade id. `trades` rows are re-derived (deleted + reinserted)
 * on every rebuild, so a trade's id — and even its entry/exit instants, if a correction reorders FIFO
 * matching — churn underneath this screen the moment the user edits a fill. Executions are the durable
 * rows, so the anchor is "the trade that currently contains this fill". If that trade stops existing
 * (deleted, or an edit left the position open), [trade] emits null and the screen says so.
 */
class TradeDetailViewModel(
    private val anchorExecutionId: Long,
    tradeRepo: TradeRepository,
    executionRepo: ExecutionRepository,
    private val notesRepo: TradeNoteRepository,
    private val deleteTrade: DeleteTrade,
    private val updateExecution: UpdateExecution,
) {
    val trade: Flow<TradeEntity?> = tradeRepo.allFlow().map { trades ->
        trades.firstOrNull { anchorExecutionId in it.entryExecutionIds || anchorExecutionId in it.exitExecutionIds }
    }

    /** The fills behind the number, oldest first — entry legs read before exit legs. */
    val fills: Flow<List<ExecutionEntity>> = combine(trade, executionRepo.allFlow()) { t, all ->
        if (t == null) emptyList() else {
            val ids = (t.entryExecutionIds + t.exitExecutionIds).toSet()
            all.filter { it.id in ids }.sortedWith(compareBy({ it.timestamp }, { it.id }))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val note: Flow<String?> = trade.flatMapLatest { t ->
        if (t == null) flowOf(null) else notesRepo.noteFlow(t.naturalKey)
    }

    suspend fun saveNote(trade: TradeEntity, text: String) = notesRepo.save(trade.naturalKey, text)
    suspend fun delete(trade: TradeEntity) = deleteTrade(trade)
    suspend fun editFill(original: ExecutionEntity, price: BigDecimal, qty: BigDecimal, ts: Long, fees: BigDecimal) =
        updateExecution(original, price, qty, ts, fees)
}

/**
 * The record behind one derived P&L figure: the fills that produced it, the arithmetic that
 * reconciles them to it, the note the trader attached, and the two destructive-ish actions
 * (correct a fill, delete the trade).
 *
 * This screen exists because the journal's only affordance was Delete — the trader could destroy a
 * record but not examine it, in a product whose whole promise is trusting a number it computed for
 * him. Nothing here is decorative: every figure is an input to, or an output of, the FIFO matcher.
 */
@Composable
fun TradeDetailScreen(vm: TradeDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val trade by vm.trade.collectAsState(initial = null)
    val fills by vm.fills.collectAsState(initial = emptyList())
    val savedNote by vm.note.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Long?>(null) } // execution id being corrected

    val t = trade
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // Back arrow + symbol. weight(1f) + maxLines so a long option symbol truncates instead of
            // pushing the delete action off the row.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to journal")
                }
                Text(
                    t?.symbol ?: "Trade",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Space.xs),
                )
                if (t != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
            }

            if (t == null) {
                // The anchor fill no longer belongs to a closed round-trip: the trade was deleted, or a
                // correction left the position open. Say which, rather than rendering a blank shell.
                Column(
                    modifier = Modifier.fillMaxSize().padding(Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("This trade no longer exists", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "It was deleted, or a correction left the position still open. An open position " +
                            "isn't a closed round-trip, so it has no realized P&L yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    Spacer(Modifier.size(Space.lg))
                    Button(onClick = onBack) { Text("Back to journal") }
                }
                return@Column
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (compact) Space.sm else Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                HeroCard(t)
                ReconciliationCard(t, fills)
                FillsCard(
                    fills = fills,
                    editingId = editing,
                    onEdit = { editing = it },
                    onCancelEdit = { editing = null },
                    onSave = { original, price, qty, ts, fees ->
                        scope.launch { vm.editFill(original, price, qty, ts, fees) }
                        editing = null
                    },
                )
                NoteCard(
                    saved = savedNote,
                    tradeKeyTag = "${t.symbol}-${t.entryTimestamp}-${t.exitTimestamp}",
                    onSave = { scope.launch { vm.saveNote(t, it) } },
                )
                Spacer(Modifier.size(Space.lg))
            }
        }
    }

    if (confirmDelete && t != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete trade?") },
            text = { Text("${t.symbol} ${formatMoney(t.realizedPnl)} — this deletes its ${fills.size} fills too, and can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; scope.launch { vm.delete(t); onBack() } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The figure itself, with the round-trip's shape underneath it. */
@Composable
private fun HeroCard(t: TradeEntity) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                "Realized P&L",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMoney(t.realizedPnl),
                color = pnlColor(t.realizedPnl),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = Space.xs),
            )
            Spacer(Modifier.size(Space.md))
            MetaRow("Direction", if (t.direction == Direction.LONG) "Long" else "Short")
            MetaRow("Opened", formatBangkok(t.entryTimestamp))
            MetaRow("Closed", formatBangkok(t.exitTimestamp))
            MetaRow("Held", humanDuration(t.exitTimestamp - t.entryTimestamp))
        }
    }
}

/**
 * The arithmetic, so the number is checkable rather than taken on faith.
 *
 * This sums the fills the user is looking at — it is NOT a second matcher (that would violate the
 * single-source-of-truth rule; BuildTradesFromExecutions owns matching). It is the same addition the
 * trader would do by hand, and it is checked against the stored figure. If the two disagree — which
 * they can when a fill is shared across round-trips via a flip-through-zero import, where its fees are
 * booked to the neighbouring trade — the check says so instead of quietly showing a wrong equation.
 */
@Composable
private fun ReconciliationCard(t: TradeEntity, fills: List<ExecutionEntity>) {
    if (fills.isEmpty()) return
    val entryIds = t.entryExecutionIds.toSet()
    val entries = fills.filter { it.id in entryIds }
    val exits = fills.filter { it.id !in entryIds }

    val entryValue = entries.fold(ZERO) { a, e -> a.add(e.price.multiply(e.quantity)) }
    val exitValue = exits.fold(ZERO) { a, e -> a.add(e.price.multiply(e.quantity)) }
    val fees = fills.fold(ZERO) { a, e -> a.add(e.fees) }
    // Long earns exit − entry; short earns entry − exit. Mirrors the matcher's per-lot rule.
    val gross = if (t.direction == Direction.LONG) exitValue.subtract(entryValue) else entryValue.subtract(exitValue)
    val net = gross.subtract(fees)
    val reconciles = net.compareTo(t.realizedPnl) == 0 // compareTo, not ==: BigDecimal equality counts scale

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text("How this figure is made", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(Space.md))
            val costLabel = if (t.direction == Direction.LONG) "Cost (bought)" else "Cost (bought back)"
            val proceedsLabel = if (t.direction == Direction.LONG) "Proceeds (sold)" else "Proceeds (sold short)"
            if (t.direction == Direction.LONG) {
                MoneyRow(proceedsLabel, exitValue)
                MoneyRow(costLabel, ZERO.subtract(entryValue))
            } else {
                MoneyRow(proceedsLabel, entryValue)
                MoneyRow(costLabel, ZERO.subtract(exitValue))
            }
            MoneyRow("Fees", ZERO.subtract(fees))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = Space.sm),
            )
            MoneyRow("Realized P&L", t.realizedPnl, emphasize = true)
            if (!reconciles) {
                Text(
                    "These fills also belong to another round-trip, so their fees are booked next door — " +
                        "the lines above won't add up to the total on their own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }
        }
    }
}

/** Every fill behind the trade. Tap one to correct it in place. */
@Composable
private fun FillsCard(
    fills: List<ExecutionEntity>,
    editingId: Long?,
    onEdit: (Long) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (ExecutionEntity, BigDecimal, BigDecimal, Long, BigDecimal) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                if (fills.size == 1) "1 fill" else "${fills.size} fills",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Tap a fill to correct it — the trade is re-derived from the fills, so this is where a wrong price gets fixed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.sm),
            )
            fills.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (f.id == editingId) {
                    FillEditor(f, onCancel = onCancelEdit, onSave = { p, q, ts, fee -> onSave(f, p, q, ts, fee) })
                } else {
                    FillRow(f, onClick = { onEdit(f.id) })
                }
            }
        }
    }
}

@Composable
private fun FillRow(f: ExecutionEntity, onClick: () -> Unit) {
    val tc = LocalTradeColors.current
    Row(
        // 48dp min height via vertical padding on a bodyMedium row — the same reason tapPadV() exists
        // in AnalyticsWidgets: a fixed height would clip at large font scales.
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Side is chrome, not P&L: a SELL is not a loss, so it never wears Loss Red.
        Box(
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Radii.sm),
            ).padding(horizontal = Space.sm, vertical = 2.dp),
        ) {
            Text(
                if (f.side == Side.BUY) "BUY" else "SELL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = Space.sm)) {
            Text(
                "${f.quantity} @ ${f.price}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatBangkok(f.timestamp) + if (f.source == ExecutionSource.MANUAL) "" else " · imported",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (f.fees.compareTo(ZERO) != 0) {
            Text(
                formatMoney(ZERO.subtract(f.fees)),
                color = tc.neutralPnl,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Correct one fill, inline. Deliberately not a dialog: an outside tap on a dialog silently discards
 * typed money, which is the exact failure this app already had on Quick Entry. Cancel is explicit.
 * Symbol and side aren't editable — those are identity, not correction (see UpdateExecution).
 */
@Composable
private fun FillEditor(
    f: ExecutionEntity,
    onCancel: () -> Unit,
    onSave: (BigDecimal, BigDecimal, Long, BigDecimal) -> Unit,
) {
    var price by remember(f.id) { mutableStateOf(f.price.toString()) }
    var qty by remember(f.id) { mutableStateOf(f.quantity.toString()) }
    var ts by remember(f.id) { mutableStateOf(formatBangkok(f.timestamp)) }
    var fees by remember(f.id) { mutableStateOf(f.fees.toString()) }
    var showErrors by remember(f.id) { mutableStateOf(false) }

    val priceErr = if (showErrors) numError(price) else null
    val qtyErr = if (showErrors) numError(qty) else null
    val feeErr = if (showErrors) feeError(fees) else null
    val tsErr = if (showErrors && runCatching { parseBangkok(ts.trim()) }.isFailure) "Use YYYY-MM-DD HH:MM" else null

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            "Correcting the ${if (f.side == Side.BUY) "buy" else "sell"} — the matcher re-runs on save.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field(price, { price = it }, "Price", keyboardType = KeyboardType.Decimal, error = priceErr)
        Field(qty, { qty = it }, "Quantity", keyboardType = KeyboardType.Decimal, error = qtyErr)
        DateTimeField(ts, { ts = it }, "Time (Bangkok)", Modifier.fillMaxWidth(), error = tsErr)
        Field(fees, { fees = it }, "Fees", keyboardType = KeyboardType.Decimal, error = feeErr)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Button(
                onClick = {
                    showErrors = true
                    val p = runCatching { bigDecimal(price.trim()) }.getOrNull()
                    val q = runCatching { bigDecimal(qty.trim()) }.getOrNull()
                    val fee = if (fees.isBlank()) ZERO else runCatching { bigDecimal(fees.trim()) }.getOrNull()
                    val time = runCatching { parseBangkok(ts.trim()) }.getOrNull()
                    if (p != null && q != null && fee != null && time != null &&
                        numError(price) == null && numError(qty) == null && feeError(fees) == null
                    ) {
                        onSave(p, q, time, fee)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save fill") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}

/**
 * The trade's note. Persisted in `trade_notes` under the round-trip's natural key, so it survives the
 * rebuild that fires on the symbol's next fill — notes used to live on the trades row, where the
 * matcher destroyed them.
 *
 * [tradeKeyTag] re-seeds the draft when the screen switches trades. `draft == null` means "untouched,
 * show what's stored", so a save landing back through the flow never clobbers in-progress typing.
 */
@Composable
private fun NoteCard(saved: String?, tradeKeyTag: String, onSave: (String) -> Unit) {
    var draft by remember(tradeKeyTag) { mutableStateOf<String?>(null) }
    val text = draft ?: saved ?: ""
    val dirty = draft != null && draft != (saved ?: "")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text("Note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = text,
                onValueChange = { draft = it },
                placeholder = { Text("What was the setup? What would you do differently?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            )
            if (dirty) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { draft = null }) { Text("Discard") }
                    Spacer(Modifier.width(Space.sm))
                    Button(onClick = { onSave(text); draft = null }) { Text("Save note") }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MoneyRow(label: String, v: BigDecimal, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatMoney(v),
            color = if (emphasize) pnlColor(v) else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Compact hold time — "2h 14m", "3d 4h". Reports, doesn't editorialize. */
private fun humanDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val mins = ms / 60_000
    val h = mins / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d ${h % 24}h"
        h > 0 -> "${h}h ${mins % 60}m"
        else -> "${mins}m"
    }
}

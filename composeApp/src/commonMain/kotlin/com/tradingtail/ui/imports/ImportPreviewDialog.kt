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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradingtail.common.CURRENCY
import com.tradingtail.data.imports.ParsedFill
import com.tradingtail.data.local.entity.Side
import com.tradingtail.domain.usecase.ImportPreview
import com.tradingtail.ui.theme.Space

/**
 * Preview of the fills parsed from a statement PDF, shown before anything is written. The user confirms
 * (or cancels) — a deliberate gate on a money import, since the executions become trades on commit.
 */
@Composable
fun ImportPreviewContent(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fills = preview.fills
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Import statement", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
        }

        // State the write before making it. The duplicate count is the fact most likely to change the
        // decision, and it used to appear only in the snackbar *after* the (irreversible, un-undoable)
        // commit — the gate asked for confirmation while withholding the reason to withhold it.
        Text(
            when {
                fills.isEmpty() -> "No trades found in this PDF."
                preview.duplicates == 0 -> "${fills.size} fills · ${preview.symbols} symbols · all new"
                preview.fresh == 0 -> "${fills.size} fills · already imported, nothing new to add"
                else -> "${fills.size} fills · ${preview.symbols} symbols · ${preview.fresh} new, " +
                    "${preview.duplicates} already imported"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (fills.isNotEmpty()) {
            if (!compact) HeaderRow() // no columns to head once the row stacks
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
            ) {
                fills.forEach { if (compact) FillRowCompact(it) else FillRow(it) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            // Names what will actually be written, not what was parsed: re-importing last month's
            // statement should read "Nothing new to import", not offer to import 47 fills again.
            Button(onClick = onConfirm, enabled = preview.fresh > 0) {
                Text(if (preview.fresh == 0) "Nothing new to import" else "Import ${preview.fresh}")
            }
        }
    }
}

/** The table's column header, aligned to the same widths/weights as [FillRow]. */
@Composable
private fun HeaderRow() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Symbol", style = style, color = muted, modifier = Modifier.width(64.dp))
        Text("Side", style = style, color = muted, modifier = Modifier.width(44.dp))
        Text("Qty", style = style, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("Price", style = style, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("Fees", style = style, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("Time", style = style, color = muted, modifier = Modifier.weight(1.4f))
    }
}

/**
 * Phone-width fill. The six-column table needs ~158dp of fixed columns and gaps before the numbers
 * get a pixel, which leaves each weighted column ~30dp at 360dp — a "$123.45" soft-wraps mid-number
 * there. Same fields, stacked into two lines instead: identity + price, then the rest as a caption.
 */
@Composable
private fun FillRowCompact(f: ParsedFill) {
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // weight on the symbol so a long option symbol truncates instead of squeezing the price.
            Text(
                f.symbol,
                style = mono.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SideLabel(f.side, Modifier.padding(horizontal = Space.sm))
            Text("$CURRENCY${f.price}", style = mono, textAlign = TextAlign.End, maxLines = 1)
        }
        Text(
            // "100 · 07-15 09:30 · $0.50 fees" — everything the wide table's remaining columns carry.
            "${f.quantity} · ${f.bangkokDateTime.substring(5).dropLast(3)} · $CURRENCY${f.fees} fees",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun FillRow(f: ParsedFill) {
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            f.symbol,
            style = mono.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
        )
        SideLabel(f.side, Modifier.width(44.dp))
        Text("${f.quantity}", style = mono, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("$CURRENCY${f.price}", style = mono, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("$CURRENCY${f.fees}", style = mono, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            f.bangkokDateTime.substring(5).dropLast(3), // "yyyy-MM-dd HH:mm:ss" → "MM-dd HH:mm"
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * BUY/SELL as neutral chrome. This wore the P&L pair until 2026-07-16 — BUY in Gain Green, SELL in
 * Loss Red — which broke the system's own law (DESIGN.md §2: green/red is data, "applied to monetary
 * figures only") on the one screen where it matters most. A sell is not a loss; a routine statement
 * rendered as a wall of red on the app's only irreversible bulk write, right where the user is being
 * asked to trust it. The side is a fact about direction, and the mono figures beside it already carry
 * every semantic that matters.
 */
@Composable
private fun SideLabel(side: Side, modifier: Modifier = Modifier) {
    Text(
        if (side == Side.BUY) "BUY" else "SELL",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

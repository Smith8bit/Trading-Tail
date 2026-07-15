package com.tradingtail.domain.usecase

import com.tradingtail.common.parseBangkokSeconds
import com.tradingtail.data.imports.ExecutionValidator
import com.tradingtail.data.imports.ParsedFill
import com.tradingtail.data.imports.WebullStatementParser
import com.tradingtail.data.imports.extractPdfText
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.repository.ExecutionRepository

data class ImportSummary(val executions: Int, val symbols: Int, val skipped: Int = 0)

/**
 * What a statement holds, resolved against what's already in the database — everything the confirm
 * gate needs to describe the write before it happens.
 *
 * [duplicates] is the fact that most often changes the decision ("47 fills" and "47 fills, 12 of them
 * already imported" are different imports), and it used to surface only in the snackbar *after* an
 * irreversible bulk write.
 */
data class ImportPreview(val fills: List<ParsedFill>, val duplicates: Int) {
    val fresh: Int get() = fills.size - duplicates
    val symbols: Int get() = fills.map { it.symbol }.distinct().size
}

/**
 * PDF import path: extract → parse → validate → insert → rebuild. Converges on the same
 * [ExecutionValidator] and matcher as manual entry (CLAUDE.md: one insert path, one Execution shape).
 */
class ImportWebullPdf(
    private val executions: ExecutionRepository,
    private val rebuildTrades: RebuildTradesForSymbol,
) {
    /**
     * Extract + parse + resolve against existing fills. No writes — the user previews and confirms
     * first. Suspend now (it reads the executions table) so the gate can state the duplicate count
     * up front instead of reporting it once the write is already done.
     */
    suspend fun preview(bytes: ByteArray): ImportPreview {
        val fills = WebullStatementParser.parse(extractPdfText(bytes))
        return ImportPreview(fills, duplicates = partition(fills).second)
    }

    /**
     * Persist the confirmed fills and re-derive trades for each affected symbol. Fills that already
     * exist as executions are skipped, so re-importing the same statement is a no-op instead of
     * doubling every trade. ponytail: app-level dedup on the import path (the only place dups arise);
     * a DB unique index is the upgrade if a fill ever slips in another way.
     */
    suspend fun commit(fills: List<ParsedFill>): ImportSummary {
        val (fresh, skipped) = partition(fills)
        executions.addAll(fresh)
        val symbols = fresh.map { it.symbol }.distinct()
        symbols.forEach { rebuildTrades(it) }
        return ImportSummary(fresh.size, symbols.size, skipped = skipped)
    }

    /**
     * Split parsed fills into (not-yet-imported, duplicate count). Shared by preview and commit so the
     * count the gate promises is produced by the same rule that does the skipping — two implementations
     * would drift, and the gate would start lying.
     */
    private suspend fun partition(fills: List<ParsedFill>): Pair<List<ExecutionEntity>, Int> {
        val candidates = fills.map { f ->
            ExecutionValidator.validate(
                symbol = f.symbol,
                side = f.side,
                price = f.price,
                quantity = f.quantity,
                timestamp = parseBangkokSeconds(f.bangkokDateTime),
                source = ExecutionSource.PDF,
                fees = f.fees,
                instrumentType = InstrumentType.STOCK,
            )
        }
        val existing = executions.all().map { it.dedupKey() }.toSet()
        val fresh = candidates.filterNot { it.dedupKey() in existing }
        return fresh to (fills.size - fresh.size)
    }
}

// Identity of a fill for dedup: same symbol, second, side, price and quantity ⇒ the same execution.
// Fees are excluded — they don't distinguish a fill and could differ by rounding across sources.
private fun ExecutionEntity.dedupKey(): String = "$symbol|$timestamp|$side|$price|$quantity"

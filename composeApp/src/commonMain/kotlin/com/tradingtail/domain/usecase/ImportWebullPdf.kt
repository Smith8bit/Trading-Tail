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
 * PDF import path: extract → parse → validate → insert → rebuild. Converges on the same
 * [ExecutionValidator] and matcher as manual entry (CLAUDE.md: one insert path, one Execution shape).
 */
class ImportWebullPdf(
    private val executions: ExecutionRepository,
    private val rebuildTrades: RebuildTradesForSymbol,
) {
    /** Extract + parse only — no DB writes, so the user can preview and confirm before committing. */
    fun preview(bytes: ByteArray): List<ParsedFill> =
        WebullStatementParser.parse(extractPdfText(bytes))

    /**
     * Persist the confirmed fills and re-derive trades for each affected symbol. Fills that already
     * exist as executions are skipped, so re-importing the same statement is a no-op instead of
     * doubling every trade. ponytail: app-level dedup on the import path (the only place dups arise);
     * a DB unique index is the upgrade if a fill ever slips in another way.
     */
    suspend fun commit(fills: List<ParsedFill>): ImportSummary {
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
        executions.addAll(fresh)
        val symbols = fresh.map { it.symbol }.distinct()
        symbols.forEach { rebuildTrades(it) }
        return ImportSummary(fresh.size, symbols.size, skipped = fills.size - fresh.size)
    }
}

// Identity of a fill for dedup: same symbol, second, side, price and quantity ⇒ the same execution.
// Fees are excluded — they don't distinguish a fill and could differ by rounding across sources.
private fun ExecutionEntity.dedupKey(): String = "$symbol|$timestamp|$side|$price|$quantity"

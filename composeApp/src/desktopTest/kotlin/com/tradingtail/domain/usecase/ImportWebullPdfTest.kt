package com.tradingtail.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tradingtail.common.bigDecimal
import com.tradingtail.data.imports.ParsedFill
import com.tradingtail.data.local.TradeDatabase
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Full import pipeline against a real (in-memory) Room DB: commit → validate → insert → rebuild. */
class ImportWebullPdfTest {
    private fun newDb(): TradeDatabase =
        Room.inMemoryDatabaseBuilder<TradeDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Test
    fun commitInsertsExecutionsAndDerivesTradesSkippingTheOrphanSell() = runBlocking {
        val db = newDb()
        val execRepo = ExecutionRepository(db.executionDao())
        val tradeRepo = TradeRepository(db.tradeDao())
        val rebuild = RebuildTradesForSymbol(execRepo, tradeRepo, BuildTradesFromExecutions())
        val import = ImportWebullPdf(execRepo, rebuild)

        val fills = listOf(
            ParsedFill("LESL", Side.BUY, bigDecimal("200"), bigDecimal("4.00"), "2026-05-15 20:14:53", bigDecimal("0.86")),
            ParsedFill("LESL", Side.SELL, bigDecimal("200"), bigDecimal("4.02"), "2026-05-15 20:17:19", bigDecimal("0.92")),
            // orphan sell (bought a prior month) — must NOT form a closed trade
            ParsedFill("QQQ", Side.SELL, bigDecimal("2"), bigDecimal("706.47"), "2026-05-12 19:10:29", bigDecimal("1.55")),
        )

        val summary = import.commit(fills)
        assertEquals(3, summary.executions)
        assertEquals(2, summary.symbols)

        val execs = db.executionDao().all()
        assertEquals(3, execs.size)
        assertEquals(ExecutionSource.PDF, execs.first().source) // provenance tagged

        val trades = db.tradeDao().all()
        assertEquals(1, trades.size) // only LESL closes; QQQ orphan sell emits nothing
        assertEquals("LESL", trades.single().symbol)
        // (4.02 − 4.00)·200 − 0.86 − 0.92 = 4.00 − 1.78 = 2.22
        assertEquals(bigDecimal("2.22"), trades.single().realizedPnl)

        // Re-importing the same statement is a no-op — every fill dedups, nothing doubles.
        val again = import.commit(fills)
        assertEquals(0, again.executions)
        assertEquals(3, again.skipped)
        assertEquals(3, db.executionDao().all().size)   // unchanged
        assertEquals(1, db.tradeDao().all().size)        // still one LESL trade, not two
        assertEquals(bigDecimal("2.22"), db.tradeDao().all().single().realizedPnl)

        db.close()
    }
}

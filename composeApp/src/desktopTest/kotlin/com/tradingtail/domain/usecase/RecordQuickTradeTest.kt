package com.tradingtail.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.TradeDatabase
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Guards the relational check no per-execution validator can make: each leg is individually valid and
 * the pair is still impossible. It lives in the usecase (not the form) because this is the gate every
 * manual path routes through — a UI-only check would leave the matcher accepting exit-before-entry.
 */
class RecordQuickTradeTest {
    private fun newDb(): TradeDatabase =
        Room.inMemoryDatabaseBuilder<TradeDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private class Fixture(db: TradeDatabase) {
        val execRepo = ExecutionRepository(db.executionDao())
        val tradeRepo = TradeRepository(db.tradeDao())
        val record = RecordQuickTrade(execRepo, RebuildTradesForSymbol(execRepo, tradeRepo, BuildTradesFromExecutions()))
    }

    @Test
    fun `rejects an exit before its entry, and writes nothing`() = runBlocking {
        val db = newDb()
        val f = Fixture(db)

        assertFailsWith<IllegalArgumentException> {
            f.record(
                symbol = "AAPL", direction = Direction.LONG, quantity = bigDecimal("100"),
                entryPrice = bigDecimal("150.25"), exitPrice = bigDecimal("158.75"),
                entryTimestamp = 2_000, exitTimestamp = 1_000, // exit an hour before the entry
            )
        }
        // The require() fires before any insert, so a rejected trade leaves no orphan fills behind to
        // be matched into a bogus trade on the symbol's next rebuild.
        assertTrue(f.execRepo.all().isEmpty())
        assertTrue(f.tradeRepo.all().isEmpty())
        db.close()
    }

    @Test
    fun `accepts a scratch trade opened and closed in the same instant`() = runBlocking {
        val db = newDb()
        val f = Fixture(db)

        // >= not >: entry and exit at the same millisecond is a real (if fast) round-trip, and the
        // boundary is exactly where an off-by-one would hide.
        f.record(
            symbol = "AAPL", direction = Direction.LONG, quantity = bigDecimal("100"),
            entryPrice = bigDecimal("150.00"), exitPrice = bigDecimal("150.00"),
            entryTimestamp = 1_000, exitTimestamp = 1_000,
        )
        assertEquals(2, f.execRepo.all().size)
        assertEquals(1, f.tradeRepo.all().size)
        db.close()
    }

    @Test
    fun `records a normal long round-trip and derives its P&L net of fees`() = runBlocking {
        val db = newDb()
        val f = Fixture(db)

        f.record(
            symbol = "aapl", direction = Direction.LONG, quantity = bigDecimal("100"),
            entryPrice = bigDecimal("150.25"), exitPrice = bigDecimal("158.75"),
            entryTimestamp = 1_000, exitTimestamp = 2_000,
            entryFees = bigDecimal("1.50"), exitFees = bigDecimal("1.50"),
        )
        val trades = f.tradeRepo.all()
        assertEquals(1, trades.size)
        assertEquals("AAPL", trades[0].symbol) // normalized on the way in
        // (158.75 − 150.25) × 100 − 1.50 − 1.50 = 847.00
        assertEquals(0, trades[0].realizedPnl.compareTo(bigDecimal("847.00")))
        db.close()
    }
}

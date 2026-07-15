package com.tradingtail.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * v1 -> v2 moves notes off the (rebuildable) trades row into `trade_notes`.
 *
 * Worth a real test rather than a read-through: it is the only code in the app that can destroy a
 * trade the user actually recorded, it runs exactly once per install so a bug ships silently, and
 * Room validates the post-migration schema on open — so this also proves the hand-written DDL matches
 * what Room expects, which is the part most likely to drift.
 */
class MigrationTest {
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("migration-test", ".db").also { it.delete() }
    }

    @AfterTest
    fun tearDown() {
        dbFile.delete()
    }

    /** Hand-build a v1 database: the exact v1 DDL, plus the identity hash Room stamps into it. */
    private fun seedV1(withNote: String?) {
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        conn.execSQL(
            "CREATE TABLE IF NOT EXISTS `executions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`symbol` TEXT NOT NULL, `side` TEXT NOT NULL, `price` TEXT NOT NULL, `quantity` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `fees` TEXT NOT NULL, `instrumentType` TEXT NOT NULL, `source` TEXT NOT NULL)",
        )
        conn.execSQL(
            "CREATE TABLE IF NOT EXISTS `trades` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`symbol` TEXT NOT NULL, `direction` TEXT NOT NULL, `entryExecutionIds` TEXT NOT NULL, " +
                "`exitExecutionIds` TEXT NOT NULL, `realizedPnl` TEXT NOT NULL, `entryTimestamp` INTEGER NOT NULL, " +
                "`exitTimestamp` INTEGER NOT NULL, `notes` TEXT)",
        )
        conn.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        conn.execSQL(
            "CREATE TABLE IF NOT EXISTS `trade_tag_cross_ref` (`tradeId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`tradeId`, `tagId`))",
        )
        // Room's own bookkeeping: without the v1 identity hash it refuses to open ("cannot verify the
        // data integrity") instead of migrating.
        conn.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        conn.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$IDENTITY_HASH_V1')")

        val notesValue = if (withNote == null) "NULL" else "'$withNote'"
        conn.execSQL(
            "INSERT INTO `trades` (`symbol`, `direction`, `entryExecutionIds`, `exitExecutionIds`, `realizedPnl`, " +
                "`entryTimestamp`, `exitTimestamp`, `notes`) VALUES ('AAPL', 'LONG', '1', '2', '850.00', 100, 200, $notesValue)",
        )
        conn.execSQL("PRAGMA user_version = 1")
        conn.close()
    }

    // Goes through createTradeDatabase so the test exercises the same addMigrations wiring the app uses
    // — a migration registered only in the test would prove nothing about the shipping build.
    private fun openV2(): TradeDatabase =
        createTradeDatabase(
            Room.databaseBuilder<TradeDatabase>(name = dbFile.absolutePath)
                .setQueryCoroutineContext(Dispatchers.IO),
        )

    @Test
    fun `migrating v1 keeps the trade and carries its note into trade_notes`() = runBlocking {
        seedV1(withNote = "Broke out of the opening range, held too long.")
        val db = openV2()

        // The trade survives: it is derivable from fills in principle, but only on the symbol's next
        // rebuild — dropping it here would empty the journal until then.
        val trades = db.tradeDao().all()
        assertEquals(1, trades.size)
        assertEquals("AAPL", trades[0].symbol)
        assertEquals(100L, trades[0].entryTimestamp)
        assertEquals(200L, trades[0].exitTimestamp)

        // The note moved to its own table, under the round-trip's natural key.
        val notes = db.tradeNoteDao().allFlow().first()
        assertEquals(1, notes.size)
        assertEquals("AAPL", notes[0].symbol)
        assertEquals(100L, notes[0].entryTs)
        assertEquals(200L, notes[0].exitTs)
        assertEquals("Broke out of the opening range, held too long.", notes[0].note)
        db.close()
    }

    @Test
    fun `migrating v1 with no note leaves trade_notes empty`() = runBlocking {
        seedV1(withNote = null)
        val db = openV2()

        assertEquals(1, db.tradeDao().all().size)
        // A NULL note must not become an empty-string row — an absent note is absent.
        assertEquals(0, db.tradeNoteDao().allFlow().first().size)
        db.close()
    }

    private companion object {
        // From schemas/…/1.json. If v1's schema ever changes this is wrong, but v1 is shipped and frozen.
        const val IDENTITY_HASH_V1 = "9da713fd143ef3740782df77505db804"
    }
}

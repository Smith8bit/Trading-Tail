package com.tradingtail.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.tradingtail.data.local.dao.ExecutionDao
import com.tradingtail.data.local.dao.TagDao
import com.tradingtail.data.local.dao.TradeDao
import com.tradingtail.data.local.dao.TradeNoteDao
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TagEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.local.entity.TradeNoteEntity
import com.tradingtail.data.local.entity.TradeTagCrossRef

@Database(
    entities = [
        ExecutionEntity::class,
        TradeEntity::class,
        TradeNoteEntity::class,
        TagEntity::class,
        TradeTagCrossRef::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(TradeDatabaseConstructor::class)
abstract class TradeDatabase : RoomDatabase() {
    abstract fun executionDao(): ExecutionDao
    abstract fun tradeDao(): TradeDao
    abstract fun tradeNoteDao(): TradeNoteDao
    abstract fun tagDao(): TagDao
}

/**
 * v1 -> v2: notes move off `trades` into their own `trade_notes` table, keyed by the round-trip's
 * natural identity (symbol + open/close instants) instead of a row id the matcher recycles.
 *
 * `trades` is rebuilt rather than `ALTER TABLE ... DROP COLUMN`-ed so the resulting schema is exactly
 * what Room expects to validate against. The rows are copied, not dropped: they're derivable from the
 * fills in principle, but only on the next rebuild for each symbol — dropping them here would empty
 * the journal until every symbol happened to get another fill.
 *
 * Any note already on `trades.notes` carries across. In practice there are none (no UI ever wrote
 * one), but the copy is one clause and beats assuming.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `trade_notes` (`symbol` TEXT NOT NULL, `entryTs` INTEGER NOT NULL, " +
                "`exitTs` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`symbol`, `entryTs`, `exitTs`))",
        )
        connection.execSQL(
            "INSERT OR REPLACE INTO `trade_notes` (`symbol`, `entryTs`, `exitTs`, `note`) " +
                "SELECT `symbol`, `entryTimestamp`, `exitTimestamp`, `notes` FROM `trades` " +
                "WHERE `notes` IS NOT NULL AND `notes` != ''",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `trades_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`symbol` TEXT NOT NULL, `direction` TEXT NOT NULL, `entryExecutionIds` TEXT NOT NULL, " +
                "`exitExecutionIds` TEXT NOT NULL, `realizedPnl` TEXT NOT NULL, `entryTimestamp` INTEGER NOT NULL, " +
                "`exitTimestamp` INTEGER NOT NULL)",
        )
        connection.execSQL(
            "INSERT INTO `trades_new` (`id`, `symbol`, `direction`, `entryExecutionIds`, `exitExecutionIds`, " +
                "`realizedPnl`, `entryTimestamp`, `exitTimestamp`) SELECT `id`, `symbol`, `direction`, " +
                "`entryExecutionIds`, `exitExecutionIds`, `realizedPnl`, `entryTimestamp`, `exitTimestamp` FROM `trades`",
        )
        connection.execSQL("DROP TABLE `trades`")
        connection.execSQL("ALTER TABLE `trades_new` RENAME TO `trades`")
    }
}

/**
 * v2 -> v3: adds sync bookkeeping (see data/sync/SyncManager) to the two tables that cross the wire.
 *
 * `executions` gets a [syncId] — the stable cross-device identity the churning local row id can't be —
 * plus an `updatedAt` clock and a `deleted` tombstone flag. Existing fills are backfilled with a random
 * 128-bit id (`randomblob`, SQLite's only per-row uniqueness primitive — no UUID function) and a real
 * `updatedAt` so the first sync actually pushes them (a 0 clock would look older than everything). The
 * unique index is created *after* the backfill, when no two rows still share the empty default.
 *
 * `trades` is intentionally untouched: it is derived, disposable, and never synced — each device
 * rebuilds it from the fills.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `executions` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `executions` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `executions` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "UPDATE `executions` SET `syncId` = lower(hex(randomblob(16))), " +
                "`updatedAt` = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE `syncId` = ''",
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_executions_syncId` ON `executions` (`syncId`)")

        connection.execSQL("ALTER TABLE `trade_notes` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `trade_notes` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE `trade_notes` SET `updatedAt` = CAST(strftime('%s','now') AS INTEGER) * 1000")
    }
}

// Actual is generated per target by Room's KSP processor (KMP requires this, no reflection).
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object TradeDatabaseConstructor : RoomDatabaseConstructor<TradeDatabase> {
    override fun initialize(): TradeDatabase
}

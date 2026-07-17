package com.tradingtail.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * ponytail: no `expect fun databaseBuilder()` here — Android needs a `Context` that can't cross
 * into commonMain, so each platform exposes its own `databaseBuilder(...)` (idiomatic KMP Room).
 * This shared finalizer applies the bundled SQLite driver both platforms use.
 */
fun createTradeDatabase(builder: RoomDatabase.Builder<TradeDatabase>): TradeDatabase =
    builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .setDriver(BundledSQLiteDriver())
        .build()

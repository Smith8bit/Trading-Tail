package com.tradingtail.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.tradingtail.data.local.dao.ExecutionDao
import com.tradingtail.data.local.dao.TagDao
import com.tradingtail.data.local.dao.TradeDao
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TagEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.local.entity.TradeTagCrossRef

@Database(
    entities = [
        ExecutionEntity::class,
        TradeEntity::class,
        TagEntity::class,
        TradeTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(TradeDatabaseConstructor::class)
abstract class TradeDatabase : RoomDatabase() {
    abstract fun executionDao(): ExecutionDao
    abstract fun tradeDao(): TradeDao
    abstract fun tagDao(): TagDao
}

// Actual is generated per target by Room's KSP processor (KMP requires this, no reflection).
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object TradeDatabaseConstructor : RoomDatabaseConstructor<TradeDatabase> {
    override fun initialize(): TradeDatabase
}

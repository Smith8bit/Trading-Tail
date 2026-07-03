package com.tradingtail.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<TradeDatabase> {
    val dbFile = context.getDatabasePath("tradetail.db")
    return Room.databaseBuilder<TradeDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
}

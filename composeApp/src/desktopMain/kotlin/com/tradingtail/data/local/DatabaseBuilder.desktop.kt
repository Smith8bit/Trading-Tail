package com.tradingtail.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun databaseBuilder(): RoomDatabase.Builder<TradeDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".tradetail/tradetail.db")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<TradeDatabase>(name = dbFile.absolutePath)
}

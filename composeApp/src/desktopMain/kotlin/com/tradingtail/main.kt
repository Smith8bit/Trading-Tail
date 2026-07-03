package com.tradingtail

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tradingtail.data.local.createTradeDatabase
import com.tradingtail.data.local.databaseBuilder

fun main() = application {
    val module = remember { AppModule(createTradeDatabase(databaseBuilder())) }
    Window(onCloseRequest = ::exitApplication, title = "Trading Tail") {
        App(module)
    }
}

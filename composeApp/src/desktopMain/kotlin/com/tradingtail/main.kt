package com.tradingtail

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tradingtail.common.localSettings
import com.tradingtail.data.local.createTradeDatabase
import com.tradingtail.data.local.databaseBuilder

fun main() = application {
    // Sync credentials now live in LocalSettings (entered via Settings), not a dedicated creds file —
    // an existing supabase.properties is migrated in on first load. See LocalSettings.desktop.kt.
    val module = remember { AppModule(createTradeDatabase(databaseBuilder()), localSettings()) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Trading Tail",
        // Compose's 800x600 default is under the shell's 960dp split threshold, so the app opened
        // every time into the phone-shaped fallback layout on a desktop with room to spare.
        state = rememberWindowState(width = 1280.dp, height = 832.dp),
    ) {
        App(module)
    }
}

package com.tradingtail

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tradingtail.data.local.createTradeDatabase
import com.tradingtail.data.local.databaseBuilder
import com.tradingtail.data.remote.SyncConfig
import java.io.File
import java.util.Properties

fun main() = application {
    val module = remember { AppModule(createTradeDatabase(databaseBuilder()), loadSyncConfig()) }
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

/**
 * Supabase credentials for sync, from `~/.tradetail/supabase.properties` (same dir as the DB, gitignored
 * by living outside the repo). Keys: `url`, `anonKey`. Absent file → null → sync stays off, app runs
 * fully offline. The anon key is a public client key by design (no-auth, open-RLS single-user project).
 */
private fun loadSyncConfig(): SyncConfig? {
    val file = File(System.getProperty("user.home"), ".tradetail/supabase.properties")
    if (!file.exists()) return null
    val props = Properties().apply { file.inputStream().use { load(it) } }
    val url = props.getProperty("url")?.trim().orEmpty()
    val key = props.getProperty("anonKey")?.trim().orEmpty()
    return if (url.isNotEmpty() && key.isNotEmpty()) SyncConfig(url, key) else null
}

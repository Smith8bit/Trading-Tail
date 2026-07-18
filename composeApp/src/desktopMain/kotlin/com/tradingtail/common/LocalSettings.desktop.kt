package com.tradingtail.common

import java.io.File
import java.util.Properties

fun localSettings(): LocalSettings = DesktopLocalSettings()

// internal + injectable paths so the round-trip test can point at a temp file, not the real ~/.tradetail.
internal class DesktopLocalSettings(
    private val file: File = File(System.getProperty("user.home"), ".tradetail/settings.properties"),
    private val legacyCreds: File = File(System.getProperty("user.home"), ".tradetail/supabase.properties"),
) : LocalSettings {

    override fun load(): SettingsData {
        val props = Properties()
        if (file.exists()) file.inputStream().use { props.load(it) }
        var url = props.getProperty("supabaseUrl", "")
        var key = props.getProperty("supabaseKey", "")
        // One-time migration: a desktop that already synced has creds in the old dedicated file. Fold
        // them in so it keeps syncing without re-entry; they persist into settings.properties on next save.
        if ((url.isBlank() || key.isBlank()) && legacyCreds.exists()) {
            val lp = Properties().apply { legacyCreds.inputStream().use { load(it) } }
            if (url.isBlank()) url = lp.getProperty("url", "")
            if (key.isBlank()) key = lp.getProperty("anonKey", "")
        }
        return SettingsData(
            configured = props.getProperty("configured", "false").toBoolean(),
            displayName = props.getProperty("displayName", ""),
            supabaseUrl = url,
            supabaseKey = key,
        )
    }

    override fun save(data: SettingsData) {
        file.parentFile?.mkdirs()
        val props = Properties().apply {
            setProperty("configured", data.configured.toString())
            setProperty("displayName", data.displayName)
            setProperty("supabaseUrl", data.supabaseUrl)
            setProperty("supabaseKey", data.supabaseKey)
        }
        file.outputStream().use { props.store(it, "Trading Tail local settings") }
    }
}

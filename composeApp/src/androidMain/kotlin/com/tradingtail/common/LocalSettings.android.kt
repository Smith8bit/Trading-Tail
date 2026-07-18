package com.tradingtail.common

import android.content.Context

fun localSettings(context: Context): LocalSettings = AndroidLocalSettings(context.applicationContext)

private class AndroidLocalSettings(context: Context) : LocalSettings {
    private val prefs = context.getSharedPreferences("tradetail_settings", Context.MODE_PRIVATE)

    override fun load(): SettingsData = SettingsData(
        configured = prefs.getBoolean("configured", false),
        displayName = prefs.getString("displayName", "").orEmpty(),
        supabaseUrl = prefs.getString("supabaseUrl", "").orEmpty(),
        supabaseKey = prefs.getString("supabaseKey", "").orEmpty(),
    )

    override fun save(data: SettingsData) {
        prefs.edit()
            .putBoolean("configured", data.configured)
            .putString("displayName", data.displayName)
            .putString("supabaseUrl", data.supabaseUrl)
            .putString("supabaseKey", data.supabaseKey)
            .apply()
    }
}

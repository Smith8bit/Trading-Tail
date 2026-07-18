package com.tradingtail.common

/**
 * Device-only app settings — the trader's profile and (optional) sync credentials. This never enters
 * the synced tables; that's the whole point ("no info in the cloud"). Persisted per platform
 * (SharedPreferences on Android, a properties file on desktop) and read synchronously at startup, so
 * it can gate first-run and build the sync client before the first frame — hence not Room (async).
 */
data class SettingsData(
    val configured: Boolean = false, // false until first-run onboarding is completed
    val displayName: String = "",
    val supabaseUrl: String = "",    // blank → sync stays off
    val supabaseKey: String = "",    // the PUBLISHABLE (client-safe) key of the user's OWN project
)

/**
 * Platform-backed persistence for [SettingsData]. Constructed per platform (Android needs a Context),
 * so it's a plain interface with a platform `localSettings(...)` factory rather than expect/actual —
 * the same shape as `databaseBuilder(...)`, which can't be expect/actual for the same Context reason.
 */
interface LocalSettings {
    fun load(): SettingsData
    fun save(data: SettingsData)
}

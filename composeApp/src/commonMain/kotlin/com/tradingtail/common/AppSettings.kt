package com.tradingtail.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, observable view of [LocalSettings], loaded once at startup. The UI observes [data] (the
 * account chip, the onboarding gate); writes go through [update], which persists immediately. Sync
 * credentials are read once at construction to build the client — changing them applies on next launch.
 */
class AppSettings(private val store: LocalSettings) {
    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<SettingsData> = _data.asStateFlow()

    val current: SettingsData get() = _data.value

    fun update(transform: (SettingsData) -> SettingsData) {
        val next = transform(_data.value)
        _data.value = next
        store.save(next)
    }
}

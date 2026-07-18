package com.tradingtail.common

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Settings persistence is the one thing that must survive a restart — the profile and the (opt-in) sync
 * credentials that gate whether the released app syncs at all. Tested against a temp file, and with the
 * legacy-creds migration pointed at a non-existent file so the real ~/.tradetail can't leak in.
 */
class LocalSettingsTest {
    private lateinit var file: File
    private lateinit var noLegacy: File

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("settings", ".properties").also { it.delete() }
        noLegacy = File.createTempFile("nolegacy", ".properties").also { it.delete() }
    }

    @AfterTest
    fun tearDown() {
        file.delete()
        noLegacy.delete()
    }

    @Test
    fun `a fresh store is unconfigured with empty fields`() {
        assertEquals(SettingsData(), DesktopLocalSettings(file, noLegacy).load())
    }

    @Test
    fun `name and credentials survive save then reload from a new instance`() {
        val data = SettingsData(
            configured = true,
            displayName = "K. Siwatt",
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_abc123",
        )
        DesktopLocalSettings(file, noLegacy).save(data)
        // A brand-new instance reads it back off disk — proves it persisted, not just cached.
        assertEquals(data, DesktopLocalSettings(file, noLegacy).load())
    }

    @Test
    fun `AppSettings persists updates and flips configured on first save`() {
        val settings = AppSettings(DesktopLocalSettings(file, noLegacy))
        assertTrue(!settings.current.configured)

        settings.update { it.copy(displayName = "Ada", configured = true) }

        assertEquals("Ada", settings.data.value.displayName)
        assertTrue(settings.current.configured)
        // Reloaded from disk by an independent store — the write went through, not just the flow.
        assertEquals("Ada", DesktopLocalSettings(file, noLegacy).load().displayName)
    }
}

package com.tradingtail.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Reads the OS "Remove animations" accessibility toggle — it zeroes the global animator scale. */
@Composable
actual fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

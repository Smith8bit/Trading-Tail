package com.tradingtail.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light icons on the dark ground, dark icons on the light one — following the *app's* theme, not the
 * system's. SideEffect (not LaunchedEffect): this is a synchronous property write that must land with
 * every successful composition, including the recomposition the theme toggle causes.
 */
@Composable
actual fun SystemBarsEffect(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            // "Light bars" means light *background*, so the icons go dark — hence the inversion.
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

package com.tradingtail.ui.theme

import androidx.compose.runtime.Composable

/** No-op: a Windows desktop window has no system status/nav bar whose icon tint we own. */
@Composable
actual fun SystemBarsEffect(dark: Boolean) = Unit

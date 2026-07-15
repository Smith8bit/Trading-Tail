package com.tradingtail.ui.theme

import androidx.compose.runtime.Composable

/**
 * Keep the system status/nav bar icons legible against whichever theme the app is actually showing.
 *
 * Android resolves system-bar icon appearance from the *system's* night mode. This app's theme is an
 * in-app toggle, so the two disagree the moment the user flips it — measured on a Pixel 9, both
 * mismatched combinations put the status bar at **1.44:1** (dark icons on the dark ground, white icons
 * on the light one): the clock, battery, and signal simply vanish. Matching themes measured 4.80:1 and
 * 13.70:1, so the bug is invisible until you touch the feature.
 *
 * Driven from [TradingTailTheme] rather than the Activity, because the theme is the only thing that
 * knows what's actually on screen — `enableEdgeToEdge()` in `MainActivity.onCreate` runs once, before
 * the toggle exists, and never hears about it again.
 */
@Composable
expect fun SystemBarsEffect(dark: Boolean)

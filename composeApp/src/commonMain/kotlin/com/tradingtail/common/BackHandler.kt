package com.tradingtail.common

import androidx.compose.runtime.Composable

/**
 * ponytail: expect/actual over `androidx.activity.compose.BackHandler` (already a dependency) rather
 * than bumping Compose Multiplatform to 1.8 for its common `BackHandler`. Three small files beat a
 * framework upgrade mid-cycle.
 *
 * Needed because the pushed full-screen surfaces (Quick Entry, Trade Detail) aren't Dialogs — a Dialog
 * consumes the back press for free, but it also dismisses on an outside tap, which silently destroyed
 * a form's worth of typed money. Owning back explicitly is the price of not losing data.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

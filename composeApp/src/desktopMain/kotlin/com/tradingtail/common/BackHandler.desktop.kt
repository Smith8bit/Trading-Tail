package com.tradingtail.common

import androidx.compose.runtime.Composable

/**
 * No-op: Windows desktop has no system back gesture. The pushed surfaces carry a visible back arrow
 * and a Cancel, which is the whole affordance here — there is no hardware event to intercept.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

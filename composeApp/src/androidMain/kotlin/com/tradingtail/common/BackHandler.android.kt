package com.tradingtail.common

import androidx.compose.runtime.Composable

/** The real thing: the system back gesture / button, routed through the Activity's dispatcher. */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)

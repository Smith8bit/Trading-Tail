package com.tradingtail.common

import androidx.compose.runtime.Composable

/**
 * True when the OS asks apps to minimize motion. Chart reveals then snap to their final state instead
 * of drawing on, so a motion-sensitive user reads the same figures without the animation. Honors
 * Android's system-wide "Remove animations" toggle; desktop has no cross-platform signal (see actual).
 */
@Composable
expect fun reducedMotion(): Boolean

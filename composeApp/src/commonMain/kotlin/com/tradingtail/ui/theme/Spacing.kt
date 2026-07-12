package com.tradingtail.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The single spacing + radius scale for the app — a constrained 4px grid (Tailwind-style) so every
 * gap, pad, and corner comes from one place instead of ad-hoc dp values. Mirrors DESIGN.md's tokens.
 * Kept dense on purpose ("figures lead"): reach for the smallest step that reads, not generous air.
 */
object Space {
    val xs = 4.dp   // tight inner gaps, label→value
    val sm = 8.dp   // default row/chip gap
    val md = 12.dp  // between cards, section rhythm
    val lg = 16.dp  // card interior padding
    val xl = 24.dp  // screen-level separation
    val xxl = 32.dp
}

/** Corner radii, matching DESIGN.md `rounded` (sm 4 / md 8 / lg 12 / xl 16). */
object Radii {
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
}

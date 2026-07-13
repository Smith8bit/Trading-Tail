package com.tradingtail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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

/**
 * Corner radii — the "Modern Terminal" scale (sm 8 / md 10 / lg 14 / xl 18 + pill), softer/larger than
 * the old 4/8/12/16 to suit the bento + immersive direction. [AppShapes] feeds these to M3 components
 * (Card, TextField, Sheet) so the whole surface rounds from one place; buttons stay [pill].
 */
object Radii {
    val sm = 8.dp
    val md = 10.dp
    val lg = 14.dp
    val xl = 18.dp
    val pill = 999.dp
}

/** M3 shape roles mapped onto [Radii] — extraSmall→fields, small→controls, medium→tiles, large→sheets. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.sm),
    small = RoundedCornerShape(Radii.md),
    medium = RoundedCornerShape(Radii.lg),
    large = RoundedCornerShape(Radii.xl),
    extraLarge = RoundedCornerShape(24.dp),
)

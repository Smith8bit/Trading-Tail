package com.tradingtail.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

/**
 * Tailwind's type scale (xs 12 · sm 14 · base 16 · lg 18 · xl 20 · 2xl 24 · 3xl 30 · 4xl 36).
 * One source for every font size: `Text()` reads it through [AppTypography]; Canvas-drawn chart labels
 * (which can't use Material typography) read these raw tokens directly.
 */
object FontSize {
    val xs = 12.sp
    val sm = 14.sp
    val base = 16.sp
    val lg = 18.sp
    val xl = 20.sp
    val xl2 = 24.sp
    val xl3 = 30.sp
    val xl4 = 36.sp
}

// M3 defaults keep their weight/letter-spacing/line-height (all have headroom over the new size); only
// the font size is snapped up onto the Tailwind steps, so the smallest UI text is 12sp (was 11) and the
// whole scale reads one step bigger.
private val M3 = Typography()
val AppTypography = M3.copy(
    labelSmall = M3.labelSmall.copy(fontSize = FontSize.xs),         // 11 → 12
    labelMedium = M3.labelMedium.copy(fontSize = FontSize.sm),       // 12 → 14
    labelLarge = M3.labelLarge.copy(fontSize = FontSize.sm),         // 14
    bodySmall = M3.bodySmall.copy(fontSize = FontSize.sm),           // 12 → 14
    bodyMedium = M3.bodyMedium.copy(fontSize = FontSize.base),       // 14 → 16
    bodyLarge = M3.bodyLarge.copy(fontSize = FontSize.base),         // 16
    titleSmall = M3.titleSmall.copy(fontSize = FontSize.base),       // 14 → 16
    titleMedium = M3.titleMedium.copy(fontSize = FontSize.lg),       // 16 → 18
    titleLarge = M3.titleLarge.copy(fontSize = FontSize.xl2),        // 22 → 24
    headlineSmall = M3.headlineSmall.copy(fontSize = FontSize.xl2),  // 24
    headlineMedium = M3.headlineMedium.copy(fontSize = FontSize.xl3),// 28 → 30
    headlineLarge = M3.headlineLarge.copy(fontSize = FontSize.xl4),  // 32 → 36
)

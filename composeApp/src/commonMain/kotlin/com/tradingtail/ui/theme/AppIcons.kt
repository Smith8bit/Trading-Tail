package com.tradingtail.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Hand-built glyphs for the app shell. ponytail: three small vectors instead of adding the whole
// material-icons-extended artifact (List + DateRange are in material-icons-core; these aren't).

// A 3-bar chart glyph for the Reports destination.
internal val BarChartIcon: ImageVector = ImageVector.Builder(
    name = "BarChart",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) { moveTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 20f); lineTo(4f, 20f); close() }
    path(fill = SolidColor(Color.Black)) { moveTo(10f, 6f); lineTo(14f, 6f); lineTo(14f, 20f); lineTo(10f, 20f); close() }
    path(fill = SolidColor(Color.Black)) { moveTo(16f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(16f, 20f); close() }
}.build()

// Sun + crescent for the theme toggle — no brightness glyph lives in material-icons-core.
internal val SunIcon: ImageVector = ImageVector.Builder("Sun", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        // rays
        moveTo(12f, 2f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(4f, 12f); moveTo(20f, 12f); lineTo(22f, 12f)
        moveTo(4.9f, 4.9f); lineTo(6.3f, 6.3f); moveTo(17.7f, 17.7f); lineTo(19.1f, 19.1f)
        moveTo(19.1f, 4.9f); lineTo(17.7f, 6.3f); moveTo(6.3f, 17.7f); lineTo(4.9f, 19.1f)
    }
    // solid core
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 7f); arcToRelative(5f, 5f, 0f, true, true, -0.01f, 0f); close()
    }
}.build()

// Crescent = a filled disc with an offset disc punched out (even-odd), so it reads as a moon, not a dot.
internal val MoonIcon: ImageVector = ImageVector.Builder("Moon", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, false, 9f, 9f)
        arcToRelative(7f, 7f, 0f, true, true, -9f, -9f); close()
    }
}.build()

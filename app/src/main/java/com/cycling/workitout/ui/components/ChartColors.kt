package com.cycling.workitout.ui.components

import androidx.compose.ui.graphics.Color

/** Mix this color toward white by `fraction` (0 = unchanged, 1 = white). */
internal fun Color.lighten(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * f,
        green = green + (1f - green) * f,
        blue = blue + (1f - blue) * f,
        alpha = alpha,
    )
}

/** Mix this color toward black by `fraction` (0 = unchanged, 1 = black). */
internal fun Color.darken(fraction: Float): Color {
    val f = (1f - fraction).coerceIn(0f, 1f)
    return Color(red = red * f, green = green * f, blue = blue * f, alpha = alpha)
}

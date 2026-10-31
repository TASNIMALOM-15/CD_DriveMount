package com.drivemount.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.max

private fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    return Color(ColorUtils.HSLToColor(floatArrayOf(hue, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))
}

/** Dark mode-এ গাঢ় রং পড়ার অযোগ্য হয়, তাই হালকা করা হয়। shade-এর পার্থক্য বজায় থাকে। */
private fun liftForDark(c: Color): Color {
    val a = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), a)
    return hsl(a[0], a[1], max(a[2], 0.62f + 0.30f * a[2]))
}

/**
 * একটি রং থেকে পূর্ণ Material 3 ColorScheme তৈরি করে (soft pastel স্টাইলে)।
 * - বাছাই করা রংটিই primary (Light mode-এ হুবহু)
 * - alpha < 1 হলে রংটি background-এর ওপর মিশিয়ে নেওয়া হয়
 * - ধূসর/Brown/Blue Grey পরিবারে surface-গুলো নিরপেক্ষ থাকে (রঙিন আভা পড়ে না)
 */
fun buildColorScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seedArgb, seedHsl)
    val h = seedHsl[0]
    val s = seedHsl[1]
    val sat = s.coerceAtMost(0.9f)
    val chroma = (s / 0.35f).coerceIn(0f, 1f)
    fun t(base: Float) = base * chroma

    return if (dark) {
        val background = hsl(h, t(0.20f), 0.09f)
        val primary = liftForDark(Color(ColorUtils.compositeColors(seedArgb, background.toArgb())))
        val secondary = hsl(h, t(0.35f), 0.78f)
        val tertiary = hsl(h + 60f, t(0.45f), 0.78f)
        darkColorScheme(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = hsl(h, sat * 0.55f, 0.26f),
            onPrimaryContainer = hsl(h, sat, 0.90f),
            inversePrimary = hsl(h, sat, 0.40f),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = hsl(h, t(0.25f), 0.25f),
            onSecondaryContainer = hsl(h, t(0.55f), 0.90f),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = hsl(h + 60f, t(0.30f), 0.25f),
            onTertiaryContainer = hsl(h + 60f, t(0.55f), 0.90f),
            background = background,
            onBackground = hsl(h, t(0.12f), 0.90f),
            surface = background,
            onSurface = hsl(h, t(0.12f), 0.90f),
            surfaceVariant = hsl(h, t(0.10f), 0.22f),
            onSurfaceVariant = hsl(h, t(0.10f), 0.78f),
            surfaceTint = primary,
            inverseSurface = hsl(h, t(0.12f), 0.90f),
            inverseOnSurface = hsl(h, t(0.10f), 0.18f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = hsl(h, t(0.08f), 0.58f),
            outlineVariant = hsl(h, t(0.10f), 0.30f),
            scrim = Color.Black,
            surfaceBright = hsl(h, t(0.14f), 0.24f),
            surfaceDim = hsl(h, t(0.18f), 0.06f),
            surfaceContainerLowest = hsl(h, t(0.16f), 0.06f),
            surfaceContainerLow = hsl(h, t(0.16f), 0.10f),
            surfaceContainer = hsl(h, t(0.16f), 0.125f),
            surfaceContainerHigh = hsl(h, t(0.16f), 0.16f),
            surfaceContainerHighest = hsl(h, t(0.16f), 0.20f),
        )
    } else {
        val background = hsl(h, t(0.45f), 0.97f)
        val primary = Color(ColorUtils.compositeColors(seedArgb, background.toArgb()))
        val secondary = hsl(h, t(0.30f), 0.40f)
        val tertiary = hsl(h + 60f, t(0.35f), 0.40f)
        lightColorScheme(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = hsl(h, sat * 0.9f, 0.90f),
            onPrimaryContainer = hsl(h, sat, 0.14f),
            inversePrimary = hsl(h, sat, 0.75f),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = hsl(h, t(0.55f), 0.90f),
            onSecondaryContainer = hsl(h, t(0.50f), 0.15f),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = hsl(h + 60f, t(0.60f), 0.90f),
            onTertiaryContainer = hsl(h + 60f, t(0.50f), 0.15f),
            background = background,
            onBackground = hsl(h, t(0.25f), 0.10f),
            surface = background,
            onSurface = hsl(h, t(0.25f), 0.10f),
            surfaceVariant = hsl(h, t(0.30f), 0.90f),
            onSurfaceVariant = hsl(h, t(0.15f), 0.30f),
            surfaceTint = primary,
            inverseSurface = hsl(h, t(0.15f), 0.20f),
            inverseOnSurface = hsl(h, t(0.30f), 0.95f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = hsl(h, t(0.10f), 0.46f),
            outlineVariant = hsl(h, t(0.20f), 0.80f),
            scrim = Color.Black,
            surfaceBright = background,
            surfaceDim = hsl(h, t(0.30f), 0.87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, t(0.40f), 0.955f),
            surfaceContainer = hsl(h, t(0.35f), 0.94f),
            surfaceContainerHigh = hsl(h, t(0.32f), 0.925f),
            surfaceContainerHighest = hsl(h, t(0.30f), 0.91f),
        )
    }
}

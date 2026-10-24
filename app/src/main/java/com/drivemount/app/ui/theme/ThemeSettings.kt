package com.drivemount.app.ui.theme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * DEFAULT  = Material You (Android 12+), নইলে ডিফল্ট নীল
 * MATERIAL = Material palette থেকে বাছাই করা রং
 * CUSTOM   = Custom color picker থেকে বাছাই করা রং
 */
enum class ColorSource { DEFAULT, MATERIAL, CUSTOM }

val DEFAULT_SEED: Int = 0xFF1E88E5.toInt()

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val source: ColorSource = ColorSource.DEFAULT,
    /** ARGB রং (alpha সহ) */
    val seedColor: Int = DEFAULT_SEED,
) {
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

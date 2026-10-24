package com.drivemount.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * অ্যাপের একমাত্র theme। সব screen এর ভেতরে এটি থাকে, তাই রং/আকার/spacing সব জায়গায় এক।
 * settings বদলালে পুরো অ্যাপ সঙ্গে সঙ্গে নতুন theme-এ recompose হয়।
 */
@Composable
fun DriveMountTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val dark = settings.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val useDynamic = settings.source == ColorSource.DEFAULT &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = if (settings.source == ColorSource.DEFAULT) DEFAULT_SEED else settings.seedColor
        remember(seed, dark) { buildColorScheme(seed, dark) }
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = DriveMountShapes,
            content = content,
        )
    }
}

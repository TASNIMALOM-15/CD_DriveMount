package com.drivemount.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Settings-এ "আসল ক্লাউড আইকন" চালু আছে কি না */
val LocalOriginalIcons = androidx.compose.runtime.staticCompositionLocalOf { false }

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

    val baseScheme = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = if (settings.source == ColorSource.DEFAULT) DEFAULT_SEED else settings.seedColor
        remember(seed, dark) { buildColorScheme(seed, dark) }
    }

    // AMOLED: ডার্ক মোডে খাঁটি কালো ব্যাকগ্রাউন্ড ও গাঢ় সারফেস
    val colorScheme = if (dark && settings.amoled) {
        baseScheme.copy(
            background = androidx.compose.ui.graphics.Color.Black,
            surface = androidx.compose.ui.graphics.Color.Black,
            surfaceDim = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF080808),
            surfaceContainer = androidx.compose.ui.graphics.Color(0xFF0F0F0F),
            surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF161616),
            surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        )
    } else baseScheme

    val iconStylesFlow = remember(context) {
        (context.applicationContext as? com.drivemount.app.DriveMountApp)?.iconSettings?.styles
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, com.drivemount.app.data.IconStyle>())
    }
    val iconStyles by iconStylesFlow.collectAsState()

    CompositionLocalProvider(
        com.drivemount.app.ui.components.LocalIconStyles provides iconStyles,
        LocalSpacing provides Spacing(),
        LocalOriginalIcons provides settings.originalIcons,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = DriveMountShapes,
            content = content,
        )
    }
}

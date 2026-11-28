/*
 * SmartLiquid — shared glue for the SmartLiquid navigation bar.
 *
 * The glass rendering is the vendored backdrop library (Kyant0/backdrop, Apache-2.0,
 * see ui/glass). The bar's structure is adapted from Echo-Music's floating nav bar
 * (GPL-3.0); the selection-pill motion from liquid_glass_easy (MIT).
 */
package com.drivemount.app.ui.smartliquid

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.drivemount.app.ui.glass.backdrop.Backdrop
import com.drivemount.app.ui.glass.backdrop.backdrops.LayerBackdrop
import com.drivemount.app.ui.glass.backdrop.drawBackdrop
import com.drivemount.app.ui.glass.backdrop.effects.blur
import com.drivemount.app.ui.glass.backdrop.effects.colorControls
import com.drivemount.app.ui.glass.backdrop.effects.lens
import com.drivemount.app.ui.glass.backdrop.highlight.Highlight
import com.drivemount.app.ui.glass.backdrop.shadow.Shadow
import com.drivemount.app.ui.glass.isGlassSupported
import kotlinx.coroutines.flow.MutableStateFlow

/** Fixed switches (the original has user toggles for these; SmartLiquid always runs the full effect). */
internal object SmartLiquidFlags {
    val reduceAnimation = MutableStateFlow(false)
    val reduceDynamicBlur = MutableStateFlow(false)
    val glassPerformance = MutableStateFlow(false)
}

/** The backdrop (app content) that every SmartLiquid glass surface samples. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/** Where a glass surface also records the glass it draws, so the selection lens can refract it. */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

// ── Haptics ───────────────────────────────────────────────────────────────────
internal enum class Haptic { Tick, Select }

internal class Haptics(private val view: android.view.View) {
    fun play(h: Haptic) {
        view.performHapticFeedback(
            when (h) {
                Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                Haptic.Select -> HapticFeedbackConstants.CONTEXT_CLICK
            },
        )
    }
}

@Composable
internal fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ── Glass material ────────────────────────────────────────────────────────────
private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f

/** Recording resolution of the glass backdrop (a third; the blur hides the upscale). */
private const val GLASS_RESOLUTION_SCALE = 0.33f

internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/** Icon / label colour for content on glass: pure black or white from the theme's luminance. */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/** Selected-tab indicator colour: white in both themes (raised, not sunk). */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }

/**
 * Renders this composable as liquid glass sampling [LocalAppBackdrop]: vibrancy, blur and
 * lens refraction, then a theme-adaptive tint. Callers gate on [isGlassSupported].
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    surfaceTint: Color? = null,
): Modifier {
    if (!isGlassSupported()) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = surfaceTint ?: themeTint.copy(alpha = SURFACE_OPACITY)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(color = surfaceTintColor, size = size) },
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

// ── Layout constants (Apple-measured) ─────────────────────────────────────────
internal val PILL_INSET = 4.dp
internal val NAV_BAR_GUTTER = 19.dp
internal val NAV_BAR_LIFT = 6.dp
private val GESTURE_BAR_FLOOR = 15.dp

/** The bar stands off the bottom edge by the nav-bar inset, never less than a gesture bar's. */
internal val floatingBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))

internal val TAB_VERTICAL_PADDING = 6.5.dp
internal val TAB_ICON_LABEL_GAP = 1.dp
internal val TAB_ICON_SIZE = 28.dp

/** Spring shared by the selection indicator and glyphs. */
internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

// ── Shared-element shim ───────────────────────────────────────────────────────
/**
 * Stand-in for Compose's SharedTransitionScope. The real one turns on the lookahead pass, which
 * crashes (NullPointerException in LookaheadPassDelegate) on this app's Compose version. Without
 * it the inline/expanded switch is a smooth cross-fade instead of a morph; nothing else changes.
 */
interface SmartSharedScope {
    fun rememberSharedContentState(key: Any): Any = key
    fun Modifier.sharedElement(
        state: Any,
        animatedVisibilityScope: Any? = null,
        zIndexInOverlay: Float = 0f,
    ): Modifier = this
    fun Modifier.renderInSharedTransitionScopeOverlay(): Modifier = this
    fun Modifier.skipToLookaheadSize(): Modifier = this
}

private object SmartSharedScopeImpl : SmartSharedScope

@Composable
fun SmartSharedLayout(modifier: Modifier = Modifier, content: @Composable SmartSharedScope.() -> Unit) {
    androidx.compose.foundation.layout.Box(modifier) { SmartSharedScopeImpl.content() }
}

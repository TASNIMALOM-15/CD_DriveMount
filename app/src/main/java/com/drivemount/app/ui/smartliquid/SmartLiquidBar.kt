/*
 * The SmartLiquid navigation bar (iOS 26 style): a glass tab pill plus a separate circular
 * tab. Scrolling down folds the pill into a single row, scrolling up expands it again.
 * Structure adapted from Echo-Music's floating nav bar (GPL-3.0) over [FloatingTabBar].
 */
@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.drivemount.app.ui.smartliquid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SmartLiquidTab(val label: String, val icon: ImageVector)

/** A tab's label: 10sp, letter-spaced like SF Pro Text. */
@Composable
internal fun tabLabelStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp)

/** Height the expanded bar occupies above the screen's bottom inset (pill 62dp + lift). */
val SmartLiquidBarHeight = 62.dp + NAV_BAR_LIFT

@Composable
fun SmartLiquidBar(
    tabs: List<SmartLiquidTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val unselectedColor = contentColor.copy(alpha = 0.65f)
    val haptics = rememberHaptics()
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    // The last tab is the circle beside the pill.
    val standaloneIndex = tabs.lastIndex

    // A factory, not a value: every glass surface needs its own modifier (own shape cache).
    val glassSurface: @Composable () -> Modifier = { Modifier.liquidGlass(shape = pillShape) }

    FloatingTabBar(
        selectedTabKey = selectedIndex,
        scrollConnection = scrollConnection,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .fillMaxWidth(),
        tabBarContentModifier = glassSurface,
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = glassIndicatorColor(),
        ),
        // Flat: each glass surface already draws its own shadow in the backdrop pass.
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PILL_INSET),
            tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
            inlineComponentSpacing = INLINE_SPACING,
            inlineBarPadding = PaddingValues(
                start = INLINE_GUTTER - NAV_BAR_GUTTER,
                end = INLINE_GUTTER - NAV_BAR_GUTTER,
                bottom = INLINE_LIFT,
            ),
        ),
        contentKey = remember(selectedIndex, tabs, contentColor) {
            listOf(selectedIndex, tabs, contentColor)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val tint = if (isSelected) contentColor else unselectedColor
            val onClick = {
                if (!isSelected) haptics.play(Haptic.Select)
                currentOnTabSelected(index)
            }
            if (index == standaloneIndex) {
                standaloneTab(
                    key = index,
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(TAB_ICON_SIZE),
                        )
                    },
                    onClick = onClick,
                )
            } else {
                tab(
                    key = index,
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(TAB_ICON_SIZE),
                        )
                    },
                    title = {
                        Text(
                            text = tab.label,
                            style = tabLabelStyle(),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                        )
                    },
                    onClick = onClick,
                )
            }
        }
    }
}

/** The collapsed row's margin at either side of the screen, and its items' spacing. */
private val INLINE_GUTTER = 28.dp
private val INLINE_SPACING = 14.dp

/** Half the difference between the expanded tab row (62dp) and the collapsed one (48dp). */
private val INLINE_LIFT = 7.dp

/** Expanded tab row height: pill inset + tab padding + glyph + gap + 12sp label line. */
@Composable
internal fun expandedRowHeight(): androidx.compose.ui.unit.Dp =
    PILL_INSET * 2 + TAB_VERTICAL_PADDING * 2 + TAB_ICON_SIZE + TAB_ICON_LABEL_GAP +
        with(androidx.compose.ui.platform.LocalDensity.current) { 12.sp.toDp() }

/**
 * Safety net: if measuring the bar throws (a Compose-version bug on some devices), the bar is
 * dropped, the error is saved for the crash report, and [onFailure] runs (switches the style back).
 * The app keeps running instead of closing.
 */
@Composable
fun SmartLiquidGuard(
    onFailure: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var failed by remember { mutableStateOf(false) }
    if (failed) return
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        try {
            val placeables = measurables.map { it.measure(constraints) }
            val w = placeables.maxOfOrNull { it.width } ?: 0
            val h = placeables.maxOfOrNull { it.height } ?: 0
            layout(w, h) { placeables.forEach { it.place(0, 0) } }
        } catch (e: Throwable) {
            failed = true
            onFailure(e)
            layout(0, 0) {}
        }
    }
}

package com.drivemount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.drivemount.app.DriveMountApp
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.home.NavStyle
import com.drivemount.app.ui.home.NavStylePrefs

/** Settings → Navigation Style: Original / Liquid Glass / Glass Pill */
@Composable
fun NavStyleCard() {
    val ctx = LocalContext.current
    NavStylePrefs.load(ctx)
    val settings by (ctx.applicationContext as DriveMountApp).themeRepository.settings.collectAsState()
    val current = NavStylePrefs.effective(settings.liquidGlass)
    val items = listOf(
        Triple(NavStyle.ORIGINAL, "Original", "The classic Material bottom bar."),
        Triple(NavStyle.LIQUID_GLASS, "Liquid Glass", "Floating glass capsule with a draggable lens (Android 12+)."),
        Triple(NavStyle.PILL, "Glass Pill", "Glass capsule tabs with glow; Library tabs get glass chips too."),
        Triple(NavStyle.SMART_LIQUID, "SmartLiquid", "iOS 26 style glass pill with a refracting lens selector; folds to one row on scroll (Android 12+)."),
    )
    DmCard {
        Column {
            Text("Navigation Style", style = MaterialTheme.typography.titleMedium)
            Text(
                "Applies instantly. Bottom bar of the main screens.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { (s, name, desc) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { NavStylePrefs.set(ctx, s) },
                ) {
                    RadioButton(selected = current == s, onClick = { NavStylePrefs.set(ctx, s) })
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

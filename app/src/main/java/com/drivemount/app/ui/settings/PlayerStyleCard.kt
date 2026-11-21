package com.drivemount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.player.PlayerStyle
import com.drivemount.app.ui.player.PlayerStylePrefs

/** Settings → Player Appearance: Normal / YouTube Style / Liquid Glass */
@Composable
fun PlayerStyleCard() {
    val ctx = LocalContext.current
    PlayerStylePrefs.load(ctx)
    val items = listOf(
        Triple(PlayerStyle.NORMAL, "Normal", "The current player interface."),
        Triple(PlayerStyle.YOUTUBE, "YouTube Style", "Red progress bar, center controls, YouTube-like layout."),
        Triple(PlayerStyle.GLASS, "Liquid Glass", "Transparent glass controls, aurora glow, floating layout."),
    )
    DmCard {
        Column {
            Text("Player Appearance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Applies instantly, even during playback. Same playback engine for all styles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { (s, name, desc) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { PlayerStylePrefs.set(ctx, s) },
                ) {
                    RadioButton(selected = PlayerStylePrefs.style == s, onClick = { PlayerStylePrefs.set(ctx, s) })
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

package com.drivemount.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.home.LibraryTabsPrefs

/** Library ট্যাব-বার উপরে নাকি নিচে */
@Composable
fun LibraryTabsCard() {
    val ctx = LocalContext.current
    LibraryTabsPrefs.load(ctx)
    DmCard {
        Column {
            Text("Library tabs position", style = MaterialTheme.typography.titleMedium)
            Text(
                "Videos / Photos / Audios… and their sub-tabs (All, Playlists, Folders…).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = LibraryTabsPrefs.top, onClick = { LibraryTabsPrefs.setTop(ctx, true) }, label = { Text("Top") })
                FilterChip(selected = !LibraryTabsPrefs.top, onClick = { LibraryTabsPrefs.setTop(ctx, false) }, label = { Text("Bottom") })
            }
        }
    }
}

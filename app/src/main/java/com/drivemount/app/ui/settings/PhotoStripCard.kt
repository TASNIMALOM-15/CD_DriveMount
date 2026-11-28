package com.drivemount.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.videos.PhotoStripPrefs

/** ফটো ফুল-স্ক্রিনের নিচের থাম্বনেইল-সারি কম্প্যাক্ট করা: ছবির মাপ ও ১/২ সারি */
@Composable
fun PhotoStripCard() {
    val ctx = LocalContext.current
    PhotoStripPrefs.load(ctx)
    DmCard {
        Column {
            Text("Photo viewer thumbnail strip", style = MaterialTheme.typography.titleMedium)
            Text(
                "Make the thumbnails at the bottom of the full-screen photo smaller to see more at once, or use 2 rows.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = PhotoStripPrefs.rows == 1, onClick = { PhotoStripPrefs.setRows(ctx, 1) }, label = { Text("1 row") })
                FilterChip(selected = PhotoStripPrefs.rows == 2, onClick = { PhotoStripPrefs.setRows(ctx, 2) }, label = { Text("2 rows") })
            }
            Text("Thumbnail size: ${PhotoStripPrefs.sizeDp} dp", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = PhotoStripPrefs.sizeDp.toFloat(),
                onValueChange = { PhotoStripPrefs.setSize(ctx, it.toInt()) },
                valueRange = 24f..72f,
            )
        }
    }
}

package com.drivemount.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.videos.VideoListPrefs

/** Videos তালিকা: TeraPlay-স্টাইল (১/২/৩ কলাম, সাইজ, ডিউরেশন, ৩-ডট) অন/অফ। অফ = আগের ২-কলাম ইন্টারফেস */
@Composable
fun VideoStyleCard() {
    val ctx = LocalContext.current
    VideoListPrefs.load(ctx)
    DmCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("TeraPlay-style video list", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1/2/3 columns, size, duration and a 3-dot menu. Off = the previous 2-column layout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = VideoListPrefs.tera, onCheckedChange = { VideoListPrefs.setTera(ctx, it) })
        }
    }
}

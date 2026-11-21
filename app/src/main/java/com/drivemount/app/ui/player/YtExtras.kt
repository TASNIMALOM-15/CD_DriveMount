package com.drivemount.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Repeat as LuRepeat
import com.composables.icons.lucide.Repeat1 as LuRepeat1
import com.composables.icons.lucide.Shuffle as LuShuffle
import com.composables.icons.lucide.X as LuX
import com.composables.icons.lucide.Bookmark as LuBookmark
import com.composables.icons.lucide.Ellipsis as LuEllipsis
import com.composables.icons.lucide.Gauge as LuGauge
import com.composables.icons.lucide.Headphones as LuHeadphones
import com.composables.icons.lucide.Settings as LuSettings
import com.composables.icons.lucide.Share2 as LuShare2
import com.composables.icons.lucide.ThumbsDown as LuThumbsDown
import com.composables.icons.lucide.ThumbsUp as LuThumbsUp
import com.composables.icons.lucide.Film as LuFilm
import com.composables.icons.lucide.Music as LuMusic
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.ui.browser.FileThumbnail
import com.drivemount.app.ui.browser.ThumbKind
import com.drivemount.app.ui.browser.thumbKindOf
import com.drivemount.app.ui.components.formatBytes
import java.text.DateFormat
import java.util.Date

/** YouTube-এর মতো ডানপাশের প্লেলিস্ট (ভিডিও ছোট হয়ে বাঁয়ে যায়) */
@Composable
fun YtQueuePanel(
    items: List<RemoteEntry>,
    currentIndex: Int,
    account: AccountEntity?,
    shuffle: Boolean,
    repeat: Int,
    onSelect: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
    Column(modifier.background(Color(0xFF0F0F0F)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Playlist", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("${currentIndex + 1}/${items.size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Lucide.LuX, "Close", tint = Color.White) }
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onRepeat) {
                Icon(
                    if (repeat == Player.REPEAT_MODE_ONE) Lucide.LuRepeat1 else Lucide.LuRepeat, "Repeat",
                    tint = if (repeat == Player.REPEAT_MODE_OFF) Color.White else Color(0xFF3EA6FF),
                )
            }
            IconButton(onClick = onShuffle) {
                Icon(Lucide.LuShuffle, "Shuffle", tint = if (shuffle) Color(0xFF3EA6FF) else Color.White)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { i, e -> "${e.path}#$i" }) { index, entry ->
                val current = index == currentIndex
                Row(
                    Modifier.fillMaxWidth().background(if (current) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(index) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(width = 124.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF272727)), contentAlignment = Alignment.Center) {
                        val kind = thumbKindOf(entry)
                        val ph: @Composable () -> Unit = {
                            Icon(if (kind == ThumbKind.VIDEO) Lucide.LuFilm else Lucide.LuMusic, null, tint = Color.White.copy(alpha = 0.6f))
                        }
                        if (kind != null && account != null) FileThumbnail(account = account, entry = entry, kind = kind, size = 124.dp, fallback = ph) else ph()
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        entry.size?.let { Text(formatBytes(it), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

/** ⚙ Settings: গতি, কোয়ালিটি, অ্যাসপেক্ট, লক, ঘোরানো, PiP */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtSettingsSheet(
    player: Player,
    tracks: Tracks,
    speedText: String,
    aspectText: String,
    onSpeed: () -> Unit,
    onAspect: () -> Unit,
    onLock: () -> Unit,
    onRotate: () -> Unit,
    onPip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
    val formats = video.flatMap { g -> (0 until g.length).map { i -> Triple(g, i, g.getTrackFormat(i)) } }
        .filter { it.third.height > 0 }.sortedByDescending { it.third.height }
    val overridden = player.trackSelectionParameters.overrides.values.any { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF212121), scrimColor = Color.Transparent) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SRow("Playback speed", speedText) { onDismiss(); onSpeed() }
            SRow("Aspect ratio", aspectText) { onAspect() }
            SRow("Lock screen", "") { onDismiss(); onLock() }
            SRow("Rotate", "") { onDismiss(); onRotate() }
            SRow("Picture-in-picture", "") { onDismiss(); onPip() }
            Text("Quality", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
            SRow("Auto", if (!overridden) "✓" else "") {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                onDismiss()
            }
            formats.forEach { (g, i, f) ->
                SRow("${f.height}p", if (g.isTrackSelected(i) && overridden) "✓" else "") {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                    onDismiss()
                }
            }
            if (formats.size < 2) Text("Only one quality is available for this video.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** ⋯ ভিডিওর সব তথ্য */
@Composable
fun VideoInfoDialog(entry: RemoteEntry?, st: PlayerState, account: AccountEntity?, onDismiss: () -> Unit) {
    val rows = buildList {
        add("Name" to (entry?.name ?: st.title))
        entry?.path?.let { add("Path" to it) }
        entry?.size?.let { add("Size" to formatBytes(it)) }
        entry?.modTimeMillis?.let { add("Modified" to DateFormat.getDateTimeInstance().format(Date(it))) }
        entry?.mimeType?.takeIf { it.isNotEmpty() }?.let { add("Type" to it) }
        if (st.videoWidth > 0) add("Resolution" to "${st.videoWidth} × ${st.videoHeight}")
        if (st.duration > 0) add("Duration" to formatTime(st.duration))
        add("Speed" to speedLabel(st.speed))
        if (st.count > 1) add("Playlist" to "${st.index + 1}/${st.count}")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Video information") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

/** পোর্ট্রেটে YouTube-এর "watch page": শিরোনাম, বাটন সারি, প্লেব্যাক পিল ও পরের ভিডিওর তালিকা */
@Composable
fun YtWatchPage(
    entry: RemoteEntry?,
    title: String,
    account: AccountEntity?,
    items: List<RemoteEntry>,
    currentIndex: Int,
    liked: Boolean,
    disliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onInfo: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onSettings: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = remember(items.size, currentIndex) { (currentIndex + 1 until items.size).toList() + (0 until currentIndex.coerceAtMost(items.size)).toList() }
    val blue = Color(0xFF3EA6FF)
    LazyColumn(modifier.background(Color(0xFF0F0F0F))) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val meta = listOfNotNull(
                    entry?.size?.let { formatBytes(it) },
                    entry?.modTimeMillis?.let { DateFormat.getDateInstance().format(Date(it)) },
                    if (items.size > 1) "${currentIndex + 1}/${items.size}" else null,
                ).joinToString("  ·  ")
                Row {
                    Text(meta, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                    Text("  more", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = onInfo))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) { Icon(Lucide.LuThumbsUp, "Like", tint = if (liked) blue else Color.White) }
                IconButton(onClick = onDislike) { Icon(Lucide.LuThumbsDown, "Dislike", tint = if (disliked) blue else Color.White) }
                IconButton(onClick = onShare) { Icon(Lucide.LuShare2, "Share", tint = Color.White) }
                IconButton(onClick = onSave) { Icon(Lucide.LuBookmark, "Save to playlist", tint = Color.White) }
                IconButton(onClick = onInfo) { Icon(Lucide.LuEllipsis, "More", tint = Color.White) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchPill(Lucide.LuHeadphones, "Audio", onAudio)
                WatchPill(null, "Subtitles", onSubtitles)
                WatchPill(Lucide.LuGauge, "Speed", onSpeed)
                WatchPill(Lucide.LuSettings, "Settings", onSettings)
            }
        }
        if (order.isNotEmpty()) {
            item {
                Text("Up next", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            }
            items(order, key = { it }) { index ->
                val e = items[index]
                Column(Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(bottom = 16.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF272727)), contentAlignment = Alignment.Center) {
                        val kind = thumbKindOf(e)
                        val ph: @Composable () -> Unit = {
                            Icon(if (kind == ThumbKind.VIDEO) Lucide.LuFilm else Lucide.LuMusic, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
                        }
                        if (kind != null && account != null) FileThumbnail(account = account, entry = e, kind = kind, size = 640.dp, fallback = ph) else ph()
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF272727)), contentAlignment = Alignment.Center) {
                            Icon(Lucide.LuFilm, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(e.name, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val m = listOfNotNull(e.size?.let { formatBytes(it) }, e.modTimeMillis?.let { DateFormat.getDateInstance().format(Date(it)) }).joinToString("  ·  ")
                            if (m.isNotEmpty()) Text(m, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
    }
}

@Composable
private fun WatchPill(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF272727)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

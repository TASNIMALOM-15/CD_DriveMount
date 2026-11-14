package com.drivemount.app.ui.videos

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import android.text.format.Formatter
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.meta.SidecarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.ui.player.PlaybackQueue

/** একটি অ্যালবাম: নাম, শিল্পী, গানগুলো ও কভারের জন্য একটি গান */
private class AlbumGroup(val album: String, val artist: String, val tracks: List<VideoItem>, val cover: VideoItem)

private fun itemKey(v: VideoItem) = v.sourceKey + "|" + v.entry.path

/** Music ট্যাব: গান (কভার-আর্ট ও ট্যাগসহ) বা অ্যালবাম গ্রিড; তথ্য সাইডকার থেকে, তাই গান না চালিয়েই দেখা যায় */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onPlay: (accountId: Long, path: String) -> Unit,
    vm: MusicViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val sidecar = (context.applicationContext as DriveMountApp).sidecar
    var picking by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(VideoSort.NAME) }
    var albumsView by rememberSaveable { mutableStateOf(false) }
    var albumFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val groups = remember(state.items, state.selected, state.sources, sort) { groupMedia(state, sort) }
    val accounts = remember(state.sources) { state.sources.associate { it.account.id to it.account } }
    val ordered = remember(groups) { groups.flatMap { it.second } }

    // ট্যাগ (লোকাল সাইডকার থেকে, কোনো নেটওয়ার্ক নেই); নতুন তথ্য এলে আবার পড়া হয়
    val sidecarVersion = sidecar.version.intValue
    val infos by produceState<Map<String, SidecarManager.AudioInfo>>(emptyMap(), ordered, accounts, sidecarVersion) {
        value = withContext(Dispatchers.IO) {
            val m = HashMap<String, SidecarManager.AudioInfo>()
            for (v in ordered) {
                val acc = accounts[v.accountId] ?: continue
                sidecar.audioInfo(acc.remoteName, v.entry.path, v.entry.size ?: -1L)?.let { m[itemKey(v)] = it }
            }
            m
        }
    }
    val albums = remember(ordered, infos) {
        ordered.filter { infos[itemKey(it)]?.album?.isNotBlank() == true }
            .groupBy { infos[itemKey(it)]!!.album.lowercase() }
            .map { (_, tracks) ->
                val first = infos[itemKey(tracks.first())]!!
                AlbumGroup(
                    album = first.album,
                    artist = first.artist,
                    tracks = tracks,
                    cover = tracks.firstOrNull { infos[itemKey(it)]?.hasArt == true } ?: tracks.first(),
                )
            }
            .sortedBy { it.album.lowercase() }
    }
    val openAlbum = albums.firstOrNull { it.album.lowercase() == albumFilter }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SourceMenuTitle(
                        state = state,
                        defaultTitle = stringResource(R.string.tab_music),
                        onSelect = vm::select,
                        onRemove = vm::removeSource,
                        onAdd = { picking = true },
                    )
                },
                actions = {
                    if (state.sources.isNotEmpty()) {
                        if (albums.isNotEmpty()) {
                            TextButton(onClick = { albumsView = !albumsView; albumFilter = null }) {
                                Text(stringResource(if (albumsView) R.string.music_tracks else R.string.music_albums))
                            }
                        }
                        SortAction(sort = sort, onSort = { sort = it })
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Lucide.LuRefreshCw, contentDescription = stringResource(R.string.videos_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.ready || (state.sources.isNotEmpty() && state.loading) ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.sources.isEmpty() -> PickPrompt(
                    icon = Lucide.LuMusic,
                    title = stringResource(R.string.music_pick_title),
                    hint = stringResource(R.string.music_pick_hint),
                    onPick = { picking = true },
                )

                state.visible.isEmpty() -> MediaEmpty(
                    error = state.error,
                    noneText = stringResource(R.string.music_none),
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                    fun play(item: VideoItem, source: List<VideoItem>) {
                        val acc = accounts[item.accountId] ?: return
                        // প্লেয়ারের কিউ একটি অ্যাকাউন্টের ফাইল নিয়ে; তাই একই অ্যাকাউন্টের গানগুলোই
                        val queue = source.filter { it.accountId == acc.id }.map { it.entry }
                        PlaybackQueue.set(PlaybackQueue.Queue(acc.id, queue, vm.siblings(item.sourceKey).ifEmpty { queue }))
                        onPlay(acc.id, item.entry.path)
                    }

                    @Composable
                    fun TrackRow(item: VideoItem, source: List<VideoItem>) {
                        val acc = accounts[item.accountId]
                        val info = infos[itemKey(item)]
                        ListItem(
                            headlineContent = {
                                Text(info?.title?.takeIf { it.isNotBlank() } ?: item.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val sub = listOfNotNull(
                                    info?.artist?.takeIf { it.isNotBlank() },
                                    info?.album?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                else item.entry.size?.let { Text(Formatter.formatShortFileSize(context, it)) }
                            },
                            leadingContent = { AudioArt(acc, item, 48.dp, sidecarVersion) },
                            modifier = Modifier.clickable(enabled = acc != null) { play(item, source) },
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        when {
                            albumsView && openAlbum == null -> LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                gridItems(albums, key = { it.album.lowercase() }) { a ->
                                    Column(modifier = Modifier.clickable {
                                        albumFilter = a.album.lowercase()
                                        albumsView = false
                                    }) {
                                        AudioArt(accounts[a.cover.accountId], a.cover, null, sidecarVersion, Modifier.fillMaxWidth().aspectRatio(1f))
                                        Text(a.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                        Text(
                                            listOf(a.artist, a.tracks.size.toString()).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            openAlbum != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "album-head") {
                                    TextButton(onClick = { albumFilter = null; albumsView = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text("← " + openAlbum.album, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                itemsIndexed(openAlbum.tracks, key = { i, e -> "${itemKey(e)}#$i" }) { _, item -> TrackRow(item, openAlbum.tracks) }
                            }

                            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                val showHeaders = groups.size > 1 || (groups.firstOrNull()?.first?.second?.isNotEmpty() == true)
                                for ((gk, list) in groups) {
                                    if (showHeaders) {
                                        item(key = "h|${gk.first}|${gk.second}") {
                                            Text(
                                                text = groupTitle(gk, state, list.size),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                                            )
                                        }
                                    }
                                    itemsIndexed(list, key = { i, e -> "${itemKey(e)}#$i" }) { _, item -> TrackRow(item, ordered) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        FolderPickerDialog(
            onPick = { acc, path ->
                picking = false
                vm.addSource(acc, path)
            },
            onDismiss = { picking = false },
        )
    }
}

/** গানের কভার (সাইডকার থেকে ছোট WebP; কোনো নেটওয়ার্ক নেই); না থাকলে গানের আইকন */
@Composable
private fun AudioArt(acc: AccountEntity?, item: VideoItem, dp: Dp?, version: Int, modifier: Modifier = Modifier) {
    val sidecar = (LocalContext.current.applicationContext as DriveMountApp).sidecar
    val size = item.entry.size ?: -1L
    val bmp by produceState<Bitmap?>(
        acc?.let { sidecar.cachedAudioArt(it.remoteName, item.entry.path, size) },
        acc?.remoteName, item.entry.path, size, version,
    ) {
        value = acc?.let { sidecar.audioArt(it.remoteName, item.entry.path, size) }
    }
    Box(
        modifier = modifier
            .then(if (dp != null) Modifier.size(dp) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Lucide.LuMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

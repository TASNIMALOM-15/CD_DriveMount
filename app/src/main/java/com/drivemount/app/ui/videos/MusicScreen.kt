package com.drivemount.app.ui.videos

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.collectAsState as collectFlowState
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.ListMusic as LuListMusic
import com.composables.icons.lucide.EllipsisVertical as LuEllipsisVertical
import com.drivemount.app.data.library.PlaylistRow
import kotlinx.coroutines.launch
import com.composables.icons.lucide.Heart as LuHeart
import com.drivemount.app.data.library.MediaKind
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
import androidx.compose.ui.draw.drawBehind
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
import com.drivemount.app.ui.components.RefreshBox
import com.drivemount.app.ui.components.Icon
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

private class ArtistGroup(val name: String, val tracks: List<VideoItem>, val albums: Int, val cover: VideoItem)

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
    val view = state.view
    val marks = (context.applicationContext as DriveMountApp).mediaMarks
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var artistFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistOpen by rememberSaveable { mutableStateOf<Long?>(null) }
    // dialogs: tracks to add to a playlist / new playlist / rename / delete
    var addTarget by remember { mutableStateOf<List<VideoItem>?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistRow?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistRow?>(null) }
    val playlists by remember { marks.playlists(com.drivemount.app.data.library.MediaKind.AUDIO) }.collectFlowState(emptyList())
    val plKeys by remember(playlistOpen) { playlistOpen?.let { marks.playlistItems(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectFlowState(emptyList())
    var albumFilter by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(view) { albumFilter = null; artistFilter = null; playlistOpen = null }

    // বাছাই/গ্রুপিং মেইন থ্রেডে নয় (ANR এড়াতে); আগের ফল দেখানো থাকে
    val groups by androidx.compose.runtime.produceState(
        initialValue = emptyList<MediaGroup>(), state.items, state.selected, state.sources, sort,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { groupMedia(state, sort) }
    }
    val accounts = remember(state.sources) { state.sources.associate { it.account.id to it.account } }
    val ordered = remember(groups) { groups.flatMap { it.second } }

    // ট্যাগ (লোকাল সাইডকার থেকে, কোনো নেটওয়ার্ক নেই); নতুন তথ্য এলে আবার পড়া হয়
    val sidecarVersion = sidecar.version.intValue
    val infos by produceState<Map<String, SidecarManager.AudioInfo>>(emptyMap(), ordered, accounts, sidecarVersion) {
        value = withContext(Dispatchers.IO) {
            val m = HashMap<String, SidecarManager.AudioInfo>()
            for (v in ordered) {
                val acc = accounts[v.accountId] ?: continue
                if (acc.backendType == "local") sidecar.ensureLocalAudio(acc.remoteName, v.entry.path, v.entry.size ?: -1L)
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
    val artists = remember(ordered, infos) {
        ordered.filter { infos[itemKey(it)]?.artist?.isNotBlank() == true }
            .groupBy { infos[itemKey(it)]!!.artist.trim().lowercase() }
            .map { (_, t) ->
                ArtistGroup(
                    name = infos[itemKey(t.first())]!!.artist.trim(),
                    tracks = t,
                    albums = t.mapNotNull { infos[itemKey(it)]?.album?.trim()?.lowercase()?.takeIf { a -> a.isNotEmpty() } }.distinct().size,
                    cover = t.firstOrNull { infos[itemKey(it)]?.hasArt == true } ?: t.first(),
                )
            }
            .sortedBy { it.name.lowercase() }
    }
    val openArtist = artists.firstOrNull { it.name.lowercase() == artistFilter }
    val byMark = remember(ordered) { ordered.associateBy { markKey(it) } }
    val plTracks = remember(plKeys, byMark) { plKeys.mapNotNull { byMark[it] } }

    Scaffold(
        topBar = { com.drivemount.app.ui.components.TopBarIcons {
            CenterAlignedTopAppBar(
                title = {
                    SourceMenuTitle(
                        state = state,
                        defaultTitle = stringResource(R.string.tab_music),
                        onSelect = vm::select,
                        onRemove = vm::removeSource,
                        onAdd = { picking = true },
                            onRefresh = vm::refresh,
                    )
                },
                actions = {
                    if (state.sources.isNotEmpty()) {
                        SortAction(sort = sort, onSort = { sort = it })
                    }
                },
            )
        }},
    ) { padding ->
        com.drivemount.app.ui.components.LibraryBox(refreshing = state.refreshing, modifier = Modifier.fillMaxSize().padding(padding)) {
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
                    fun TrackRow(item: VideoItem, source: List<VideoItem>, removable: Boolean = false) {
                        val acc = accounts[item.accountId]
                        val info = infos[itemKey(item)]
                        // Compact row with a very thin inset divider (same look as the cloud file browser)
                        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    val inset = 12.dp.toPx()
                                    val y = size.height - 0.25.dp.toPx()
                                    drawLine(
                                        color = dividerColor,
                                        start = androidx.compose.ui.geometry.Offset(inset, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width - inset, y),
                                        strokeWidth = 0.5.dp.toPx(),
                                    )
                                }
                                .clickable(enabled = acc != null) { play(item, source) }
                                .padding(start = 16.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AudioArt(acc, item, 48.dp, sidecarVersion)
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(
                                    info?.title?.takeIf { it.isNotBlank() } ?: item.entry.name,
                                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val sub = listOfNotNull(
                                    info?.artist?.takeIf { it.isNotBlank() },
                                    info?.album?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ").ifEmpty { item.entry.size?.let { Formatter.formatShortFileSize(context, it) }.orEmpty() }
                                if (sub.isNotEmpty()) Text(
                                    sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val fav = markKey(item) in state.favorites
                            androidx.compose.material3.IconButton(
                                onClick = { vm.toggleFavorite(listOf(item), MediaKind.AUDIO) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Lucide.LuHeart, contentDescription = null, modifier = Modifier.size(18.dp),
                                    tint = if (fav) androidx.compose.ui.graphics.Color(0xFFFF4D6D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                            var rowMenu by remember { mutableStateOf(false) }
                            Box {
                                androidx.compose.material3.IconButton(onClick = { rowMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Lucide.LuEllipsisVertical, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                androidx.compose.material3.DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { rowMenu = false; addTarget = listOf(item) })
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(if (view == LibView.PRIVATE) "Remove from Private" else "Move to Private") },
                                        onClick = { rowMenu = false; vm.setPrivate(listOf(item), MediaKind.AUDIO, view != LibView.PRIVATE) },
                                    )
                                    val pid = playlistOpen
                                    if (removable && pid != null) androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Remove from playlist") },
                                        onClick = { rowMenu = false; scope.launch { marks.removeFromPlaylist(pid, markKey(item)) } },
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        when {
                            view == LibView.PLAYLISTS && playlistOpen != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "pl-head") {
                                    val pl = playlists.firstOrNull { it.id == playlistOpen }
                                    Column {
                                        TextButton(onClick = { playlistOpen = null }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text("← " + (pl?.name ?: "Playlists"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (plTracks.isNotEmpty()) androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.Button(onClick = { play(plTracks.first(), plTracks) }) { Text("Play all") }
                                            androidx.compose.material3.OutlinedButton(onClick = { plTracks.shuffled().let { play(it.first(), it) } }) { Text("Shuffle") }
                                        }
                                    }
                                }
                                if (plTracks.isEmpty()) item(key = "pl-empty") {
                                    Text("No tracks yet. Use ⋮ on a track to add it here.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                itemsIndexed(plTracks, key = { i, e -> "${itemKey(e)}#$i" }) { _, item -> TrackRow(item, plTracks, removable = true) }
                            }

                            view == LibView.PLAYLISTS -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "pl-new") {
                                    ListItem(
                                        headlineContent = { Text("New playlist") },
                                        leadingContent = { Icon(Lucide.LuPlus, contentDescription = null) },
                                        modifier = Modifier.clickable { newPlaylist = true },
                                    )
                                }
                                items(playlists, key = { it.id }) { pl ->
                                    var menu by remember { mutableStateOf(false) }
                                    ListItem(
                                        headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { Text("${pl.count} tracks") },
                                        leadingContent = { Icon(Lucide.LuListMusic, contentDescription = null) },
                                        trailingContent = {
                                            Box {
                                                androidx.compose.material3.IconButton(onClick = { menu = true }) { Icon(Lucide.LuEllipsisVertical, contentDescription = null) }
                                                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                    androidx.compose.material3.DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameTarget = pl })
                                                    androidx.compose.material3.DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleteTarget = pl })
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable { playlistOpen = pl.id },
                                    )
                                }
                            }

                            view == LibView.ALBUMS && openAlbum == null -> LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                gridItems(albums, key = { it.album.lowercase() }) { a ->
                                    Column(modifier = Modifier.clickable { albumFilter = a.album.lowercase() }) {
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

                            view == LibView.ALBUMS && openAlbum != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "album-head") {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { albumFilter = null }, modifier = Modifier.padding(horizontal = 8.dp).weight(1f)) {
                                            Text("← " + openAlbum.album, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        TextButton(onClick = { addTarget = openAlbum.tracks }) { Text("Add all to playlist") }
                                    }
                                }
                                itemsIndexed(openAlbum.tracks, key = { i, e -> "${itemKey(e)}#$i" }) { _, item -> TrackRow(item, openAlbum.tracks) }
                            }

                            view == LibView.ARTISTS && openArtist != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "artist-head") {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { artistFilter = null }, modifier = Modifier.padding(horizontal = 8.dp).weight(1f)) {
                                            Text("← " + openArtist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        TextButton(onClick = { addTarget = openArtist.tracks }) { Text("Add all to playlist") }
                                    }
                                }
                                itemsIndexed(openArtist.tracks, key = { i, e -> "${itemKey(e)}#$i" }) { _, item -> TrackRow(item, openArtist.tracks) }
                            }

                            view == LibView.ARTISTS -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(artists, key = { it.name.lowercase() }) { a ->
                                    ListItem(
                                        headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { Text("${a.tracks.size} tracks" + if (a.albums > 0) " · ${a.albums} albums" else "") },
                                        leadingContent = { AudioArt(accounts[a.cover.accountId], a.cover, 48.dp, sidecarVersion) },
                                        modifier = Modifier.clickable { artistFilter = a.name.lowercase() },
                                    )
                                }
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

    addTarget?.let { target ->
        var creating by remember(target) { mutableStateOf(false) }
        var name by remember(target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    if (creating) OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Playlist name") })
                    else {
                        TextButton(onClick = { creating = true }) { Text("+ New playlist") }
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(playlists, key = { it.id }) { pl ->
                                ListItem(
                                    headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${pl.count} tracks") },
                                    modifier = Modifier.clickable {
                                        scope.launch { marks.addToPlaylist(pl.id, target.map { markKey(it) }) }
                                        addTarget = null
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (creating) TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        val id = marks.createPlaylist(com.drivemount.app.data.library.MediaKind.AUDIO, name)
                        marks.addToPlaylist(id, target.map { markKey(it) })
                    }
                    addTarget = null
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { addTarget = null }) { Text("Cancel") } },
        )
    }
    if (newPlaylist || renameTarget != null) {
        val rt = renameTarget
        var name by remember(rt, newPlaylist) { mutableStateOf(rt?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { newPlaylist = false; renameTarget = null },
            title = { Text(if (rt != null) "Rename playlist" else "New playlist") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        if (rt != null) marks.renamePlaylist(rt.id, name)
                        else marks.createPlaylist(com.drivemount.app.data.library.MediaKind.AUDIO, name)
                    }
                    newPlaylist = false; renameTarget = null
                }) { Text(if (rt != null) "Rename" else "Create") }
            },
            dismissButton = { TextButton(onClick = { newPlaylist = false; renameTarget = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${pl.name}\" will be deleted. The tracks themselves are not touched.") },
            confirmButton = { TextButton(onClick = { scope.launch { marks.deletePlaylist(pl.id) }; deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (picking) {
        CloudPickerDialog(
            accounts = null,
            multi = true,
            subOption = true,
            onPick = { acc, entries, sub ->
                picking = false
                vm.addPicked(acc, entries, sub)
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
        if (acc?.backendType == "local") sidecar.ensureLocalAudio(acc.remoteName, item.entry.path, size)
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

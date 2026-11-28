package com.drivemount.app.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.EllipsisVertical as LuEllipsisVertical
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.library.MediaKind
import com.drivemount.app.data.library.PlaylistRow
import com.drivemount.app.ui.browser.FileThumbnail
import com.drivemount.app.ui.browser.ThumbKind
import com.drivemount.app.ui.videos.PlaylistDialogsHost
import com.drivemount.app.ui.videos.PlaylistUi
import com.drivemount.app.ui.videos.VideoItem
import com.drivemount.app.ui.videos.markKey
import androidx.compose.foundation.horizontalScroll
import com.composables.icons.lucide.Heart as LuHeart
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.Folder as LuFolder
import com.drivemount.app.ui.videos.LibView
import com.drivemount.app.ui.videos.MediaLibraryViewModel
import com.drivemount.app.ui.videos.VideosState
import com.drivemount.app.ui.videos.VideosViewModel
import com.drivemount.app.ui.videos.PhotosViewModel
import com.drivemount.app.ui.videos.MusicViewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AppWindow as LuAppWindow
import com.composables.icons.lucide.FileText as LuFileText
import com.composables.icons.lucide.Film as LuFilm
import com.composables.icons.lucide.Image as LuImage
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.Star as LuStar
import com.composables.icons.lucide.Layers as LuLayers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Puzzle as LuPuzzle
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Settings as LuSettings
import com.composables.icons.lucide.UserPlus as LuUserPlus
import com.drivemount.app.R
import com.drivemount.app.ui.components.Icon
import com.drivemount.app.ui.videos.ApksScreen
import com.drivemount.app.ui.videos.DocsScreen
import com.drivemount.app.ui.videos.FavoritesScreen
import com.drivemount.app.ui.videos.MusicScreen
import com.drivemount.app.ui.videos.PhotosScreen
import com.drivemount.app.ui.videos.VideosScreen

/** Library-র ভেতরের ভাগ (chip)। সংখ্যাগুলো MainShell-এ rememberSaveable হয়ে থাকে */
object LibKind {
    const val VIDEOS = 0
    const val PHOTOS = 1
    const val MUSIC = 2
    const val DOCS = 3
    const val APKS = 4
    const val FAVORITES = 5
}

private class LibChip(val kind: Int, val label: Int, val icon: ImageVector)

private val libChips = listOf(
    LibChip(LibKind.VIDEOS, R.string.qa_videos, Lucide.LuFilm),
    LibChip(LibKind.PHOTOS, R.string.qa_photos, Lucide.LuImage),
    LibChip(LibKind.MUSIC, R.string.qa_audios, Lucide.LuMusic),
    LibChip(LibKind.DOCS, R.string.qa_docs, Lucide.LuFileText),
    LibChip(LibKind.APKS, R.string.qa_apks, Lucide.LuAppWindow),
    LibChip(LibKind.FAVORITES, R.string.qa_favorites, Lucide.LuStar),
)

/**
 * Library ট্যাব: ওপরে স্ক্রলযোগ্য chip-সারি (Videos | Photos | Audios | Docs | APKs | Favorites), নিচে সেই পর্দা।
 * নিচের বারে ৬টা আলাদা ট্যাব না রেখে এক ট্যাপে ভাগ বদলানো যায়। chip-সারি স্ট্যাটাস-বারের নিচে বসে;
 * ভেতরের পর্দার নিজস্ব টপ-বার তাই স্ট্যাটাস-বারের জায়গা আর নেয় না (consumeWindowInsets)।
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    kind: Int,
    onKind: (Int) -> Unit,
    onPlay: (accountId: Long, path: String) -> Unit,
) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = kind.coerceIn(0, libChips.size - 1)) { libChips.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // chip ট্যাপ / বাইরে থেকে kind বদলালে পেজার সেদিকে স্লাইড করে; সোয়াইপে পেজ বদলালে kind আপডেট হয়
    androidx.compose.runtime.LaunchedEffect(kind) {
        if (kind in libChips.indices && kind != pager.targetPage) pager.animateScrollToPage(kind)
    }
    androidx.compose.runtime.LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { if (it != kind) onKind(it) }
    }

    // chip-সারির উচ্চতা (px): টপ-বার এই জায়গা ছেড়ে নিচে বসে; কালো ব্যাকগ্রাউন্ড/collapse নেই
    val reserve = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val hdr = androidx.compose.runtime.remember { com.drivemount.app.ui.components.LibHeader(reserve) }
    val statusTopPx = WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current)
    LibraryTabsPrefs.load(LocalContext.current)
    val tabsTop = LibraryTabsPrefs.top
    val barInset = LocalBottomBarInset.current
    val hdrH = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // সাব-ট্যাব সারি নিচে থাকলে মিনি প্লেয়ার তার ওপরে বসবে (ঢেকে দেবে না)
    val playbackCtl = (LocalContext.current.applicationContext as DriveMountApp).playback
    androidx.compose.runtime.DisposableEffect(tabsTop, hdrH.intValue) {
        playbackCtl.extraBottomPx.value = if (tabsTop) 0 else hdrH.intValue
        onDispose { playbackCtl.extraBottomPx.value = 0 }
    }
    androidx.compose.runtime.SideEffect {
        hdr.statusTop.intValue = statusTopPx
        hdr.bottom.value = !tabsTop
        // নিচে থাকলে ওপরে শুধু স্ট্যাটাস-বারের জায়গা ছাড়া হয় (ভেতরের টপ-বারের জন্য)
        if (!tabsTop) reserve.intValue = statusTopPx
    }
    // নিচে স্ক্রল করলে হেডার (ভাগ-বার + সাব-ট্যাব + টপ-বার) লুকায়, ওপরে টানলে ফেরে; ছাড়লে পুরো লুকানো/পুরো দেখানোতে থিতু হয়
    val hideScope = androidx.compose.runtime.rememberCoroutineScope()
    val hideConn = androidx.compose.runtime.remember(hdr) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            fun dist() = (if (hdr.bottom.value) hdrH.intValue else hdr.reserve.intValue - hdr.statusTop.intValue).coerceAtLeast(1).toFloat()
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y != 0f) {
                    hdr.hide.floatValue = (hdr.hide.floatValue - available.y / dist()).coerceIn(0f, 1f)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val from = hdr.hide.floatValue
                val to = if (from > 0.5f) 1f else 0f
                if (from != to) androidx.compose.animation.core.animate(from, to) { v, _ -> hdr.hide.floatValue = v }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    // ভাগ বদলালে (Videos→Photos ইত্যাদি) হেডার আবার দেখানো
    androidx.compose.runtime.LaunchedEffect(kind) { hdr.hide.floatValue = 0f }

    // Sub-views (All / Folders / Favorites) of the current Videos / Photos / Audios tab
    val vVm: VideosViewModel = viewModel()
    val pVm: PhotosViewModel = viewModel()
    val mVm: MusicViewModel = viewModel()
    val vSt by vVm.state.collectAsState()
    val pSt by pVm.state.collectAsState()
    val mSt by mVm.state.collectAsState()
    val curVm: MediaLibraryViewModel? = when (pager.targetPage) { LibKind.VIDEOS -> vVm; LibKind.PHOTOS -> pVm; LibKind.MUSIC -> mVm; else -> null }
    val curSt: VideosState = when (pager.targetPage) { LibKind.VIDEOS -> vSt; LibKind.PHOTOS -> pSt; else -> mSt }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ctx = LocalContext.current
    val marks = (ctx.applicationContext as DriveMountApp).mediaMarks
    // Private locks again when the app goes to the background or this tab is left
    androidx.compose.runtime.DisposableEffect(ctx) {
        val owner = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        fun relock() { listOf(vVm, pVm, mVm).forEach { if (it.state.value.view == LibView.PRIVATE) it.setView(LibView.ALL) } }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) relock() }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs); relock() }
    }
    val vLists by androidx.compose.runtime.remember { marks.playlists(MediaKind.VIDEO) }.collectAsState(emptyList())
    val pLists by androidx.compose.runtime.remember { marks.playlists(MediaKind.PHOTO) }.collectAsState(emptyList())
    val curLists = when (pager.targetPage) { LibKind.VIDEOS -> vLists; LibKind.PHOTOS -> pLists; else -> emptyList() }
    // name shown on the active chip while a folder / playlist / album is open
    val openLabel: String? = curSt.playlistId?.let { id -> curLists.firstOrNull { it.id == id }?.name }
        ?: curSt.folder?.let { f -> f.second.substringAfterLast('/').ifEmpty { curSt.sources.firstOrNull { it.key == f.first }?.label.orEmpty() } }

    val contentInset = if (tabsTop) barInset else barInset + with(density) { hdrH.intValue.toDp() }
    androidx.compose.runtime.CompositionLocalProvider(LocalBottomBarInset provides contentInset) {
    Box(Modifier.fillMaxSize().nestedScroll(hideConn)) {
        androidx.compose.runtime.CompositionLocalProvider(com.drivemount.app.ui.components.LocalLibHeader provides hdr) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.statusBars),
                key = { it },
            ) { k ->
                when (k) {
                    LibKind.PHOTOS -> PhotosScreen()
                    LibKind.MUSIC -> MusicScreen(onPlay = onPlay)
                    LibKind.DOCS -> DocsScreen(onPlay = onPlay)
                    LibKind.APKS -> ApksScreen(onPlay = onPlay)
                    LibKind.FAVORITES -> FavoritesScreen(onPlay = onPlay)
                    else -> VideosScreen(onPlay = onPlay)
                }
            }
        }
        val paneModifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = with(density) {
                val full = reserve.intValue
                val f = if (tabsTop) hdr.hide.floatValue else 0f
                (full - f * (full - statusTopPx).coerceAtLeast(0)).toDp()
            })
        val tab = pager.targetPage
        if (curVm != null && curSt.sources.isNotEmpty()) {
            if (curSt.view == LibView.FOLDERS && curSt.folder == null) {
                LibFolderPane(st = curSt, onOpen = { k, f -> curVm.openFolder(k, f) }, modifier = paneModifier)
            } else if (curSt.playlistId == null && curSt.folder == null &&
                ((tab == LibKind.VIDEOS && curSt.view == LibView.PLAYLISTS) || (tab == LibKind.PHOTOS && curSt.view == LibView.ALBUMS))
            ) {
                val photos = tab == LibKind.PHOTOS
                LibListPane(
                    kind = if (photos) MediaKind.PHOTO else MediaKind.VIDEO,
                    st = curSt, rows = curLists, autoFolders = photos,
                    onOpenList = { id -> curVm.openPlaylist(id, if (photos) LibView.ALBUMS else LibView.PLAYLISTS) },
                    onOpenFolder = { k, f -> curVm.openFolder(k, f, LibView.ALBUMS) },
                    modifier = paneModifier,
                )
            }
        }
        PlaylistDialogsHost(onDeleted = { id -> if (curSt.playlistId == id) curVm?.closeOpen() })
        // chip-সারি কনটেন্টের ওপরে ভাসে; ব্যাকগ্রাউন্ড ট্রান্সপারেন্ট
        Column(
            modifier = (
                if (tabsTop) {
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { reserve.intValue = it.height; hdrH.intValue = it.height - statusTopPx }
                        .statusBarsPadding()
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = barInset)
                        .then(if (NavStylePrefs.isPill) Modifier else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)))
                        .onSizeChanged { hdrH.intValue = it.height }
                }
                ),
        ) {
        Column(
            Modifier.graphicsLayer {
                val f = hdr.hide.floatValue
                translationY = if (tabsTop) {
                    -f * (reserve.intValue - hdr.statusTop.intValue).coerceAtLeast(0)
                } else {
                    f * (hdrH.intValue + barInset.toPx())
                }
                alpha = 1f - f
            },
        ) {
        if (NavStylePrefs.isPill) {
            val kindLabels = libChips.map { stringResource(it.label) }
            GlassTopTabs(
                labels = kindLabels,
                selected = libChips.indexOfFirst { it.kind == pager.targetPage }.coerceAtLeast(0),
                onSelect = { i -> val k = libChips[i].kind; onKind(k); scope.launch { pager.animateScrollToPage(k) } },
                modifier = Modifier.fillMaxWidth(),
            )
        } else LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            items(libChips, key = { it.kind }) { c ->
                val sel = pager.targetPage == c.kind
                Row(
                    modifier = Modifier
                        .animateContentSize()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onKind(c.kind); scope.launch { pager.animateScrollToPage(c.kind) } }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(c.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    if (sel) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(c.label), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (curVm != null) LibViewRow(curSt, curVm, kind = pager.targetPage, openLabel = openLabel, onPrivate = {
            // Private needs the phone's screen lock / fingerprint every time it is opened
            val act = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstOrNull { it is androidx.fragment.app.FragmentActivity } as? androidx.fragment.app.FragmentActivity
            if (act == null || !com.drivemount.app.security.Biometrics.isAvailable(ctx)) {
                android.widget.Toast.makeText(ctx, "Set a screen lock (PIN, pattern or fingerprint) to use Private", android.widget.Toast.LENGTH_LONG).show()
            } else {
                com.drivemount.app.security.Biometrics.authenticate(act, "Private", "Unlock to view private items", onSuccess = { curVm.setView(LibView.PRIVATE) })
            }
        })
        }
        }
    }
    }
}

@Composable
private fun LibViewRow(st: VideosState, vm: MediaLibraryViewModel, kind: Int, openLabel: String?, onPrivate: () -> Unit) {
    if (NavStylePrefs.isPill) {
        val items: List<Pair<String, LibView>> = when (kind) {
            LibKind.MUSIC -> listOf("Tracks" to LibView.ALL, "Albums" to LibView.ALBUMS, "Artists" to LibView.ARTISTS, "Playlists" to LibView.PLAYLISTS,
                "Favorites" to LibView.FAVORITES, "Folders" to LibView.FOLDERS, "Private" to LibView.PRIVATE)
            LibKind.PHOTOS -> listOf("All" to LibView.ALL, "Albums" to LibView.ALBUMS, "Folders" to LibView.FOLDERS,
                "Favorites" to LibView.FAVORITES, "Private" to LibView.PRIVATE)
            else -> listOf("All" to LibView.ALL, "Playlists" to LibView.PLAYLISTS, "Folders" to LibView.FOLDERS,
                "Favorites" to LibView.FAVORITES, "Private" to LibView.PRIVATE)
        }
        GlassTopTabs(
            labels = items.map { (l, v) -> if (st.view == v && openLabel != null) "$l · $openLabel" else l },
            selected = items.indexOfFirst { it.second == st.view }.coerceAtLeast(0),
            onSelect = { i ->
                val view = items[i].second
                val selected = st.view == view
                if (selected && openLabel != null) vm.closeOpen()
                else if (view == LibView.PRIVATE && !selected) onPrivate()
                else vm.setView(view)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun chip(label: String, view: LibView, icon: ImageVector?) {
            val selected = st.view == view
            val opened = selected && openLabel != null
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            val glow = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) Modifier.background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, glow.copy(alpha = 0.22f))),
                        ) else Modifier,
                    )
                    .clickable { if (opened) vm.closeOpen() else if (view == LibView.PRIVATE && !selected) onPrivate() else vm.setView(view) }
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (opened) "$label · $openLabel" else label,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.let {
                        if (selected) it.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            shadow = androidx.compose.ui.graphics.Shadow(color = glow.copy(alpha = 0.9f), offset = androidx.compose.ui.geometry.Offset.Zero, blurRadius = 22f),
                        ) else it
                    },
                    color = tint,
                )
                Spacer(Modifier.height(3.dp))
                Box(Modifier.height(2.dp).fillMaxWidth().background(if (selected) tint else androidx.compose.ui.graphics.Color.Transparent))
            }
        }
        when (kind) {
            LibKind.MUSIC -> {
                chip("Tracks", LibView.ALL, null)
                chip("Albums", LibView.ALBUMS, null)
                chip("Artists", LibView.ARTISTS, null)
                chip("Playlists", LibView.PLAYLISTS, null)
                chip("Favorites", LibView.FAVORITES, Lucide.LuHeart)
                chip("Folders", LibView.FOLDERS, Lucide.LuFolder)
                chip("Private", LibView.PRIVATE, Lucide.LuLock)
            }
            LibKind.PHOTOS -> {
                chip("All", LibView.ALL, null)
                chip("Albums", LibView.ALBUMS, null)
                chip("Folders", LibView.FOLDERS, Lucide.LuFolder)
                chip("Favorites", LibView.FAVORITES, Lucide.LuHeart)
                chip("Private", LibView.PRIVATE, Lucide.LuLock)
            }
            else -> {
                chip("All", LibView.ALL, null)
                chip("Playlists", LibView.PLAYLISTS, null)
                chip("Folders", LibView.FOLDERS, Lucide.LuFolder)
                chip("Favorites", LibView.FAVORITES, Lucide.LuHeart)
                chip("Private", LibView.PRIVATE, Lucide.LuLock)
            }
        }
    }
}

private class FolderRow(val sk: String, val folder: String, val name: String, val sub: String, val keys: List<String>)

/** Every folder (of the chosen source folders) with its items; shared by the Folders view and the auto-albums */
private fun folderRows(st: VideosState): List<FolderRow> {
    val labels = st.sources.associate { it.key to it.label }
    return st.shownItems.entries.flatMap { (sk, list) -> list.groupBy { it.folder }.map { (f, l) -> Triple(sk, f, l) } }
        .sortedWith(compareBy({ t -> st.sources.indexOfFirst { s -> s.key == t.first } }, { it.second.lowercase() }))
        .map { (sk, f, l) ->
            val name = f.substringAfterLast('/').ifEmpty { labels[sk].orEmpty() }
            val sub = (if (st.sources.size > 1) labels[sk].orEmpty() else "") + (if (f.contains('/')) "  " + f.substringBeforeLast('/') else "")
            FolderRow(sk, f, name, sub.trim(), l.map { markKey(it) })
        }
}

/** FOLDERS view: every folder with its item count */
@Composable
private fun LibFolderPane(st: VideosState, onOpen: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val rows = androidx.compose.runtime.remember(st.shownItems, st.sources) { folderRows(st) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + 8.dp)) {
        if (rows.isEmpty()) item { Text("No folders yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows, key = { it.sk + "|" + it.folder }) { r ->
            ListItem(
                headlineContent = { Text(r.name, maxLines = 1) },
                supportingContent = { Text((r.sub.takeIf { it.isNotEmpty() }?.plus(" · ") ?: "") + "${r.keys.size} items", maxLines = 1) },
                leadingContent = { Icon(Lucide.LuFolder, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(r.sk, r.folder) },
            )
        }
    }
}

/** Cover of a playlist/album: thumbnail of its first item */
@Composable
private fun KeyCover(key: String?, kind: Int, st: VideosState, byMark: Map<String, VideoItem>) {
    val item = key?.let { byMark[it] }
    val acc = item?.let { i -> st.sources.firstOrNull { it.account.id == i.accountId }?.account }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (item != null && acc != null) {
            FileThumbnail(
                account = acc, entry = item.entry,
                kind = if (kind == MediaKind.PHOTO) ThumbKind.IMAGE else ThumbKind.VIDEO,
                size = 56.dp, aspect = 1f, thumbPx = 160,
            ) { Icon(Lucide.LuFolder, contentDescription = null) }
        } else Icon(Lucide.LuFolder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Video playlists / Photo albums: the ones the user made, plus (photos) automatic albums from folders.
 * Folder albums have "Add all to album…" so a whole folder can be copied into a custom album.
 */
@Composable
private fun LibListPane(
    kind: Int,
    st: VideosState,
    rows: List<PlaylistRow>,
    autoFolders: Boolean,
    onOpenList: (Long) -> Unit,
    onOpenFolder: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = PlaylistUi.noun(kind)
    val byMark = androidx.compose.runtime.remember(st.shownItems) { st.shownItems.values.flatten().associateBy { markKey(it) } }
    val folders = androidx.compose.runtime.remember(st.shownItems, st.sources, autoFolders) { if (autoFolders) folderRows(st) else emptyList() }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + 8.dp)) {
        item(key = "new") {
            ListItem(
                headlineContent = { Text("New $noun") },
                leadingContent = { Icon(Lucide.LuPlus, contentDescription = null) },
                modifier = Modifier.clickable { PlaylistUi.newKind = kind },
            )
        }
        items(rows, key = { "p" + it.id }) { pl ->
            var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(pl.name, maxLines = 1) },
                supportingContent = { Text("${pl.count} items") },
                leadingContent = { KeyCover(pl.coverKey, kind, st, byMark) },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Lucide.LuEllipsisVertical, contentDescription = null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; PlaylistUi.rename = pl })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; PlaylistUi.delete = pl })
                        }
                    }
                },
                modifier = Modifier.clickable { onOpenList(pl.id) },
            )
        }
        if (folders.isNotEmpty()) {
            item(key = "auto-head") {
                Text("From folders", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            items(folders, key = { "f" + it.sk + "|" + it.folder }) { r ->
                var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(r.name, maxLines = 1) },
                    supportingContent = { Text((r.sub.takeIf { it.isNotEmpty() }?.plus(" · ") ?: "") + "${r.keys.size} items", maxLines = 1) },
                    leadingContent = { KeyCover(r.keys.firstOrNull(), kind, st, byMark) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Lucide.LuEllipsisVertical, contentDescription = null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Add all to album…") }, onClick = { menu = false; PlaylistUi.addKind = kind; PlaylistUi.addKeys = r.keys })
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpenFolder(r.sk, r.folder) },
                )
            }
        }
    }
}

/**
 * More ট্যাব: কম-ব্যবহৃত জায়গাগুলো এক তালিকায় (আগে Addon/Others ট্যাব ও টপ-বারের ছড়ানো আইকনে ছিল)।
 * Addon-এ চাপলে [AddonScreen] খোলে (ব্যাক চাপলে More-এ ফেরে — MainShell সামলায়)।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenAddon: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSync: () -> Unit,
    onNewSync: () -> Unit,
    onOpenBackup: () -> Unit,
    onAddAccount: () -> Unit,
) {
    class MoreRow(val icon: ImageVector, val label: Int, val onClick: () -> Unit)
    val rows = listOf(
        MoreRow(Lucide.LuUserPlus, R.string.add_account, onAddAccount),
        MoreRow(Lucide.LuSearch, R.string.browser_search, onOpenSearch),
        MoreRow(Lucide.LuRefreshCcw, R.string.sync_title, onOpenSync),
        MoreRow(Lucide.LuRefreshCcw, R.string.tab_new_sync, onNewSync),
        MoreRow(Lucide.LuLayers, R.string.backup_title, onOpenBackup),
        MoreRow(Lucide.LuPuzzle, R.string.tab_addon, onOpenAddon),
        MoreRow(Lucide.LuSettings, R.string.settings, onOpenSettings),
    )
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_more)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = LocalBottomBarInset.current + 8.dp,
            ),
        ) {
            items(rows) { r ->
                ListItem(
                    headlineContent = { Text(stringResource(r.label), style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = { Icon(r.icon, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = r.onClick),
                )
            }
        }
    }
}

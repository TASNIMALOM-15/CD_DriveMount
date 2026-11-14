package com.drivemount.app.ui.videos

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check as LuCheck
import com.composables.icons.lucide.CirclePlay as LuCirclePlay
import com.composables.icons.lucide.ListFilter as LuListFilter
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Video as LuVideo
import com.composables.icons.lucide.X as LuX
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.ui.browser.FileThumbnail
import com.drivemount.app.ui.browser.ThumbKind
import com.drivemount.app.ui.browser.VideoMeta
import com.drivemount.app.ui.player.PlaybackQueue
import com.drivemount.app.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Videos ট্যাব: বেছে নেওয়া ফোল্ডারগুলোর সব ভিডিও (সব একসাথে বা একটি করে), থাম্বনেল-কার্ডে */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    onPlay: (accountId: Long, path: String) -> Unit,
    vm: VideosViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val sp = MaterialTheme.spacing

    var picking by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(VideoSort.DATE) }
    var sortMenu by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val accounts = remember(state.sources) { state.sources.associate { it.account.id to it.account } }
    val labels = remember(state.sources) { state.sources.associate { it.key to it.label } }
    val selectedSource = state.sources.firstOrNull { it.key == state.selected }

    val groups = remember(state.items, state.selected, state.sources, query, sort) {
        val q = query.trim()
        val all = state.visible
        val filtered = if (q.isEmpty()) all else all.filter { it.entry.name.contains(q, ignoreCase = true) }
        val cmp: Comparator<VideoItem> = when (sort) {
            VideoSort.NAME -> compareBy<VideoItem> { naturalKey(it.entry.name) }
            VideoSort.DATE -> compareByDescending<VideoItem> { it.entry.modTimeMillis ?: 0L }
            VideoSort.SIZE -> compareByDescending<VideoItem> { it.entry.size ?: 0L }
        }
        val order = state.sources.map { it.key }
        val grouped = filtered.groupBy { it.sourceKey to it.folder }
        val keys = grouped.keys.sortedWith(
            compareBy<Pair<String, String>>({ order.indexOf(it.first) }, { it.second.lowercase() }),
        )
        keys.map { k -> k to grouped.getValue(k).sortedWith(cmp) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.videos_search)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (state.sources.isEmpty()) {
                        Text(stringResource(R.string.tab_videos))
                    } else {
                        SourceMenuTitle(
                            state = state,
                            defaultTitle = stringResource(R.string.tab_videos),
                            onSelect = vm::select,
                            onRemove = vm::removeSource,
                            onAdd = { picking = true },
                        )
                    }
                },
                actions = {
                    if (state.sources.isNotEmpty()) {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(
                                if (searching) Lucide.LuX else Lucide.LuSearch,
                                contentDescription = stringResource(R.string.videos_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Lucide.LuListFilter, contentDescription = stringResource(R.string.videos_sort))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                for (k in VideoSort.values()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (k) {
                                                    VideoSort.NAME -> stringResource(R.string.videos_sort_name)
                                                    VideoSort.DATE -> stringResource(R.string.videos_sort_date)
                                                    VideoSort.SIZE -> stringResource(R.string.videos_sort_size)
                                                },
                                            )
                                        },
                                        trailingIcon = { if (sort == k) Icon(Lucide.LuCheck, contentDescription = null) },
                                        onClick = {
                                            sort = k
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
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
                !state.ready -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.sources.isEmpty() -> PickPrompt(
                    icon = Lucide.LuVideo,
                    title = stringResource(R.string.videos_pick_title),
                    hint = stringResource(R.string.videos_pick_hint),
                    onPick = { picking = true },
                )

                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.visible.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(sp.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(sp.md),
                ) {
                    Text(
                        text = state.error ?: stringResource(R.string.videos_none),
                        textAlign = TextAlign.Center,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { vm.refresh() }) { Text(stringResource(R.string.videos_refresh)) }
                }

                else -> {
                    val ordered = remember(groups) { groups.flatMap { it.second } }
                    // স্ক্রল থামলে (≈১ সেকেন্ড) যে কার্ডগুলো দেখা যাচ্ছে, তাদের ভিডিওর শুরু/শেষ আগেভাগে ক্যাশে আনা:
                    // ট্যাপ করলে প্রায় সঙ্গে সঙ্গে চলে। মোবাইল ডেটা/ব্যাটারি-সেভারে চলে না।
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    androidx.compose.runtime.LaunchedEffect(ordered, accounts) {
                        val app = ctx.applicationContext as com.drivemount.app.DriveMountApp
                        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.key } }
                            .collectLatest { keys ->
                                delay(900)
                                val byKey = ordered.associateBy { it.sourceKey + "|" + it.entry.path }
                                var n = 0
                                for (k in keys) {
                                    val v = byKey[(k as? String)?.substringBeforeLast('#') ?: continue] ?: continue
                                    val acc = accounts[v.accountId] ?: continue
                                    if (acc.backendType == "local" || acc.backendType == "terabox") continue
                                    com.drivemount.app.engine.StreamWarmer.warmFile(
                                        ctx, app.rcloneApi,
                                        com.drivemount.app.engine.fsRootFor(acc.remoteName, acc.backendType),
                                        v.entry.path, v.entry.size ?: -1L, false,
                                    )
                                    if (++n >= 4) break
                                }
                            }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = sp.md, end = sp.md, top = sp.sm, bottom = sp.lg),
                            horizontalArrangement = Arrangement.spacedBy(sp.md),
                            verticalArrangement = Arrangement.spacedBy(sp.md),
                        ) {
                            val showHeaders = groups.size > 1 || (groups.firstOrNull()?.first?.second?.isNotEmpty() == true)
                            for ((gk, list) in groups) {
                                if (showHeaders) {
                                    item(key = "h|${gk.first}|${gk.second}", span = { GridItemSpan(maxLineSpan) }) {
                                        val base = if (state.selected == null) labels[gk.first].orEmpty() else ""
                                        val title = when {
                                            base.isNotEmpty() && gk.second.isNotEmpty() -> base + " / " + gk.second
                                            base.isNotEmpty() -> base
                                            gk.second.isNotEmpty() -> gk.second
                                            else -> stringResource(R.string.videos_root)
                                        }
                                        Text(
                                            text = title + "  ·  " + list.size,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = sp.sm),
                                        )
                                    }
                                }
                                itemsIndexed(list, key = { i, e -> "${e.sourceKey + "|" + e.entry.path}#$i" }) { _, item ->
                                    val acc = accounts[item.accountId]
                                    if (acc != null) {
                                        VideoCard(
                                            account = acc,
                                            item = item,
                                            resumeTick = resumeTick,
                                            onClick = {
                                                // প্লেয়ারের কিউ একটি অ্যাকাউন্টের ফাইল নিয়ে; তাই একই অ্যাকাউন্টের ভিডিওগুলোই
                                                val queue = ordered.filter { it.accountId == acc.id }.map { it.entry }
                                                PlaybackQueue.set(
                                                    PlaybackQueue.Queue(acc.id, queue, vm.siblings(item.sourceKey).ifEmpty { queue }),
                                                )
                                                onPlay(acc.id, item.entry.path)
                                            },
                                        )
                                    }
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

@Composable
private fun VideoCard(
    account: AccountEntity,
    item: VideoItem,
    resumeTick: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val entry = item.entry
    val key = VideoMeta.key(account, entry)

    // দৈর্ঘ্য জানা যায় থাম্বনেল বানানোর সময়; তাই জানা না থাকলে অল্প অল্প করে দেখে নেওয়া হয়
    var dur by remember(key) { mutableLongStateOf(VideoMeta.duration(context, key)) }
    LaunchedEffect(key) {
        var tries = 0
        while (dur == 0L && tries < 30) {
            delay(1500)
            dur = VideoMeta.duration(context, key)
            tries++
        }
    }
    val pos = remember(key, resumeTick) { VideoMeta.position(context, account.id, entry.path) }
    val shape = RoundedCornerShape(10.dp)

    Column(modifier = Modifier.clip(shape).clickable(onClick = onClick)) {
        Box {
            FileThumbnail(
                account = account,
                entry = entry,
                kind = ThumbKind.VIDEO,
                size = 0.dp,
                aspect = 16f / 9f,
                showBadge = false,
                modifier = Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Lucide.LuCirclePlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            entry.size?.let { sz ->
                Text(
                    text = Formatter.formatShortFileSize(context, sz),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (dur > 0) {
                Text(
                    text = VideoMeta.formatDuration(dur),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp, end = 6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (dur > 0 && pos > 5_000L) {
                LinearProgressIndicator(
                    progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
        )
    }
}

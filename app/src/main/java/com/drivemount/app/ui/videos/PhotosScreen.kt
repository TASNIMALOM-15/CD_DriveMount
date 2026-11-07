package com.drivemount.app.ui.videos

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Image as LuImage
import com.composables.icons.lucide.Images as LuImages
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.ui.browser.FileThumbnail
import com.drivemount.app.ui.browser.ThumbKind

/** Photos ট্যাব: বেছে নেওয়া ফোল্ডারগুলোর সব ছবি, থাম্বনেল-গ্রিডে; ছবিতে চাপলে পূর্ণ-পর্দায় দেখা যায় */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(vm: PhotosViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(VideoSort.DATE) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    // পিঞ্চ-টু-জুম: এক সারিতে ৩ থেকে ৭টি ছবি (বেছে নেওয়া সংখ্যা মনে রাখা হয়)
    val prefs = LocalContext.current.getSharedPreferences("photos_ui", android.content.Context.MODE_PRIVATE)
    var columns by remember { mutableStateOf(prefs.getInt("columns", 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS)) }

    // ঘর যত ছোট, থাম্বনেলও তত ছোট সাইজে ডিকোড হয় (৭ কলামে ৩৬০px লাগে না): স্ক্রল মসৃণ রাখে
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current.density
    val cellPx = screenWidthDp / columns * density
    val thumbPx = when {
        cellPx <= 150f -> 160
        cellPx <= 230f -> 240
        cellPx <= 350f -> 360
        else -> 480
    }

    val groups = remember(state.items, state.selected, state.sources, sort) { groupMedia(state, sort) }
    val accounts = remember(state.sources) { state.sources.associate { it.account.id to it.account } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SourceMenuTitle(
                        state = state,
                        defaultTitle = stringResource(R.string.tab_photos),
                        onSelect = vm::select,
                        onRemove = vm::removeSource,
                        onAdd = { picking = true },
                    )
                },
                actions = {
                    if (state.sources.isNotEmpty()) {
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
                    icon = Lucide.LuImages,
                    title = stringResource(R.string.photos_pick_title),
                    hint = stringResource(R.string.photos_pick_hint),
                    onPick = { picking = true },
                )

                state.visible.isEmpty() -> MediaEmpty(
                    error = state.error,
                    noneText = stringResource(R.string.photos_none),
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                    val ordered = remember(groups) { groups.flatMap { it.second } }
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // দুই আঙুলে টানলে কলাম বদলায়; এক আঙুলের স্ক্রল ও ট্যাপে হাত দেওয়া হয় না
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        var scale = 1f
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            // শুধু পর্দার মাঝের অংশে দুই আঙুল পড়লে জুম হয়; কিনারায় ভুল স্পর্শে কিছু হয় না
                                            val c = event.calculateCentroid(useCurrent = true)
                                            val inCenter = c.x in size.width * 0.15f..size.width * 0.85f &&
                                                c.y in size.height * 0.2f..size.height * 0.8f
                                            if (event.changes.size >= 2 && inCenter) {
                                                scale *= event.calculateZoom()
                                                val next = when {
                                                    scale > 1.25f -> columns - 1 // ছড়ালে ছবি বড় = কম কলাম
                                                    scale < 0.8f -> columns + 1 // চিমটি দিলে ছোট = বেশি কলাম
                                                    else -> columns
                                                }.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
                                                if (scale > 1.25f || scale < 0.8f) {
                                                    scale = 1f
                                                    if (next != columns) {
                                                        columns = next
                                                        prefs.edit().putInt("columns", next).apply()
                                                    }
                                                }
                                                event.changes.forEach { it.consume() }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(1.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            val showHeaders = groups.size > 1 || (groups.firstOrNull()?.first?.second?.isNotEmpty() == true)
                            var offset = 0
                            for ((gk, list) in groups) {
                                if (showHeaders) {
                                    item(key = "h|${gk.first}|${gk.second}", span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = groupTitle(gk, state, list.size),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                                        )
                                    }
                                }
                                val base = offset
                                items(list.size, key = { list[it].sourceKey + "|" + list[it].entry.path + "#" + it }) { i ->
                                    val item = list[i]
                                    val acc = accounts[item.accountId]
                                    if (acc != null) {
                                        FileThumbnail(
                                            account = acc,
                                            entry = item.entry,
                                            kind = ThumbKind.IMAGE,
                                            size = 0.dp,
                                            aspect = 1f,
                                            thumbPx = thumbPx,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewerIndex = base + i },
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Lucide.LuImage,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                offset += list.size
                            }
                        }
                    }
                    viewerIndex?.let { idx ->
                        PhotoViewerDialog(
                            items = ordered,
                            startIndex = idx,
                            accounts = accounts,
                            onDismiss = { viewerIndex = null },
                        )
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

private const val MIN_COLUMNS = 3
private const val MAX_COLUMNS = 7

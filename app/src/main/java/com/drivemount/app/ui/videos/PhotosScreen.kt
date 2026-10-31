package com.drivemount.app.ui.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
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
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.videos_refresh))
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
                    icon = Icons.Rounded.PhotoLibrary,
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
                            columns = GridCells.Adaptive(110.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                items(list.size, key = { list[it].sourceKey + "|" + list[it].entry.path }) { i ->
                                    val item = list[i]
                                    val acc = accounts[item.accountId]
                                    if (acc != null) {
                                        FileThumbnail(
                                            account = acc,
                                            entry = item.entry,
                                            kind = ThumbKind.IMAGE,
                                            size = 0.dp,
                                            aspect = 1f,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewerIndex = base + i },
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Image,
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

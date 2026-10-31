package com.drivemount.app.ui.videos

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.ui.player.PlaybackQueue

/** Music ট্যাব: বেছে নেওয়া ফোল্ডারগুলোর সব গান, ফোল্ডার অনুযায়ী ভাগ করে */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onPlay: (accountId: Long, path: String) -> Unit,
    vm: MusicViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(VideoSort.NAME) }

    val groups = remember(state.items, state.selected, state.sources, sort) { groupMedia(state, sort) }

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
                    icon = Icons.Rounded.MusicNote,
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
                    val ordered = remember(groups) { groups.flatMap { it.second } }
                    val accounts = remember(state.sources) { state.sources.associate { it.account.id to it.account } }
                    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                        if (state.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                items(list, key = { it.sourceKey + "|" + it.entry.path }) { item ->
                                    val acc = accounts[item.accountId]
                                    ListItem(
                                        headlineContent = { Text(item.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = {
                                            item.entry.size?.let { Text(Formatter.formatShortFileSize(context, it)) }
                                        },
                                        leadingContent = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                                        modifier = Modifier.clickable(enabled = acc != null) {
                                            if (acc != null) {
                                                // প্লেয়ারের কিউ একটি অ্যাকাউন্টের ফাইল নিয়ে; তাই একই অ্যাকাউন্টের গানগুলোই
                                                val queue = ordered.filter { it.accountId == acc.id }.map { it.entry }
                                                PlaybackQueue.set(
                                                    PlaybackQueue.Queue(acc.id, queue, vm.siblings(item.sourceKey).ifEmpty { queue }),
                                                )
                                                onPlay(acc.id, item.entry.path)
                                            }
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

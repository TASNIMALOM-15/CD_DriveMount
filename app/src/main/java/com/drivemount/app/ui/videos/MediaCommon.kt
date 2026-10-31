package com.drivemount.app.ui.videos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivemount.app.R
import com.drivemount.app.ui.theme.spacing

internal fun naturalKey(s: String): String =
    s.lowercase().replace(Regex("\\d+")) { it.value.padStart(8, '0') }

internal typealias MediaGroup = Pair<Pair<String, String>, List<VideoItem>>

/** বর্তমান বাছাই অনুযায়ী উৎস ও ফোল্ডার ধরে ভাগ করা তালিকা */
internal fun groupMedia(state: VideosState, sort: VideoSort): List<MediaGroup> {
    val cmp: Comparator<VideoItem> = when (sort) {
        VideoSort.NAME -> compareBy<VideoItem> { naturalKey(it.entry.name) }
        VideoSort.DATE -> compareByDescending<VideoItem> { it.entry.modTimeMillis ?: 0L }
        VideoSort.SIZE -> compareByDescending<VideoItem> { it.entry.size ?: 0L }
    }
    val order = state.sources.map { it.key }
    val grouped = state.visible.groupBy { it.sourceKey to it.folder }
    val keys = grouped.keys.sortedWith(
        compareBy<Pair<String, String>>({ order.indexOf(it.first) }, { it.second.lowercase() }),
    )
    return keys.map { k -> k to grouped.getValue(k).sortedWith(cmp) }
}

@Composable
internal fun groupTitle(gk: Pair<String, String>, state: VideosState, count: Int): String {
    val base = if (state.selected == null) state.sources.firstOrNull { it.key == gk.first }?.label.orEmpty() else ""
    val title = when {
        base.isNotEmpty() && gk.second.isNotEmpty() -> base + " / " + gk.second
        base.isNotEmpty() -> base
        gk.second.isNotEmpty() -> gk.second
        else -> stringResource(R.string.videos_root)
    }
    return "$title  ·  $count"
}

/** ওপরের ছোট বাটন: All বা যেকোনো একটি ফোল্ডারে সুইচ, ফোল্ডার বাদ দেওয়া, নতুন ফোল্ডার যোগ */
@Composable
internal fun SourceMenuTitle(
    state: VideosState,
    defaultTitle: String,
    onSelect: (String?) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    if (state.sources.isEmpty()) {
        Text(defaultTitle)
        return
    }
    var open by remember { mutableStateOf(false) }
    val selected = state.sources.firstOrNull { it.key == state.selected }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { open = true }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.label ?: stringResource(R.string.videos_all),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.videos_all) + "  (" + state.items.values.sumOf { it.size } + ")") },
                trailingIcon = { if (state.selected == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            for (src in state.sources) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = src.label + "  (" + state.items[src.key].orEmpty().size + ")",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { if (state.selected == src.key) Icon(Icons.Rounded.Check, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            open = false
                            onRemove(src.key)
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.videos_remove_folder))
                        }
                    },
                    onClick = {
                        onSelect(src.key)
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.videos_add_folder)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    open = false
                    onAdd()
                },
            )
        }
    }
}

@Composable
internal fun SortAction(sort: VideoSort, onSort: (VideoSort) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.videos_sort))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
                    trailingIcon = { if (sort == k) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        onSort(k)
                        menu = false
                    },
                )
            }
        }
    }
}

/** এখনও কোনো ফোল্ডার বাছা হয়নি */
@Composable
internal fun PickPrompt(icon: ImageVector, title: String, hint: String, onPick: () -> Unit) {
    val sp = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(sp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = sp.md),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = sp.sm, bottom = sp.lg),
        )
        Button(onClick = onPick) { Text(stringResource(R.string.videos_select_folder_btn)) }
    }
}

/** ফোল্ডার আছে, কিন্তু কিছু পাওয়া যায়নি বা ত্রুটি */
@Composable
internal fun MediaEmpty(error: String?, noneText: String, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val sp = MaterialTheme.spacing
    Column(
        modifier = modifier.padding(sp.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        Text(
            text = error ?: noneText,
            textAlign = TextAlign.Center,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRefresh) { Text(stringResource(R.string.videos_refresh)) }
    }
}

package com.drivemount.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.ui.browser.FileThumbnail
import com.drivemount.app.ui.browser.thumbKindOf
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.theme.spacing
import java.util.Locale

/** প্লেলিস্ট: তালিকা থেকে যেকোনোটিতে যাওয়া, shuffle ও repeat */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
    items: List<RemoteEntry>,
    currentIndex: Int,
    account: AccountEntity?,
    shuffle: Boolean,
    repeat: Int,
    onSelect: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetColor(), scrimColor = Color.Transparent) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.player_playlist), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.player_playlist_count, currentIndex + 1, items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.player_repeat),
                        tint = if (repeat == Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(state = listState) {
                itemsIndexed(items, key = { _, e -> e.path }) { index, entry ->
                    val current = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(horizontal = sp.md, vertical = sp.xs + 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.md),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            val kind = thumbKindOf(entry)
                            val placeholder: @Composable () -> Unit = {
                                Icon(
                                    imageVector = if (kind == null) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (kind != null && account != null) {
                                FileThumbnail(account = account, entry = entry, kind = kind, size = 64.dp, fallback = placeholder)
                            } else {
                                placeholder()
                            }
                            if (current) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onSelect(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            entry.size?.let {
                                Text(
                                    text = formatBytes(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** চালানোর গতি */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val sp = MaterialTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor(), scrimColor = Color.Transparent) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.player_speed),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm),
            )
            SPEEDS.forEach { s ->
                OptionRow(label = speedLabel(s), selected = kotlin.math.abs(s - current) < 0.01f) { onSelect(s) }
            }
            Spacer(Modifier.height(sp.sm))
        }
    }
}

val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

fun speedLabel(s: Float): String =
    (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "×"

/** অডিও ট্র্যাক ও সাবটাইটেল বাছাই */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksSheet(player: Player, tracks: Tracks, onDismiss: () -> Unit) {
    val sp = MaterialTheme.spacing
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
    val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val anyTextSelected = text.any { it.isSelected }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor(), scrimColor = Color.Transparent) {
        LazyColumn(modifier = Modifier.navigationBarsPadding()) {
            item {
                Text(
                    text = stringResource(R.string.player_audio),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm),
                )
            }
            if (audio.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.player_no_tracks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
                    )
                }
            }
            var n = 0
            audio.forEach { group ->
                for (i in 0 until group.length) {
                    n++
                    val label = trackLabel(group.getTrackFormat(i), n, audio = true)
                    val selected = group.isTrackSelected(i)
                    item(key = "a${n}") {
                        OptionRow(label, selected) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                            onDismiss()
                        }
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = sp.sm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = stringResource(R.string.player_subtitles),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm),
                )
                OptionRow(stringResource(R.string.player_subtitles_off), textDisabled || !anyTextSelected) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    onDismiss()
                }
            }
            var t = 0
            text.forEach { group ->
                for (i in 0 until group.length) {
                    t++
                    val label = trackLabel(group.getTrackFormat(i), t, audio = false)
                    val selected = !textDisabled && group.isTrackSelected(i)
                    item(key = "t${t}") {
                        OptionRow(label, selected) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                            onDismiss()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(sp.md)) }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = sp.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "English · 5.1" বা "Track 2" */
private fun trackLabel(f: Format, number: Int, audio: Boolean): String {
    val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
    val name = f.label ?: lang ?: "Track $number"
    val extra = buildList {
        if (audio) {
            when (f.channelCount) {
                Format.NO_VALUE -> Unit
                1 -> add("Mono")
                2 -> add("Stereo")
                6 -> add("5.1")
                8 -> add("7.1")
                else -> add("${f.channelCount}ch")
            }
        }
        if (f.label != null && lang != null && !f.label.equals(lang, ignoreCase = true)) add(lang)
    }
    return if (extra.isEmpty()) name else name + " · " + extra.joinToString(" · ")
}

/** প্রায় স্বচ্ছ শিট: নিচের ভিডিও দেখা যায় */
@Composable
private fun sheetColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)

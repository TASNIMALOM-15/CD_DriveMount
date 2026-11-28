package com.drivemount.app.ui.videos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.library.MediaKind
import com.drivemount.app.data.library.PlaylistRow
import kotlinx.coroutines.launch

/**
 * Shared state for the "add to playlist/album", new, rename and delete dialogs of Videos (playlists)
 * and Photos (albums). Any screen sets a field; [PlaylistDialogsHost] (hosted once in LibraryScreen) shows the dialog.
 */
object PlaylistUi {
    /** item keys ("accountId|path") waiting to be added, and the kind they belong to */
    var addKeys by mutableStateOf<List<String>?>(null)
    var addKind by mutableStateOf(MediaKind.VIDEO)
    var newKind by mutableStateOf<Int?>(null)
    var rename by mutableStateOf<PlaylistRow?>(null)
    var delete by mutableStateOf<PlaylistRow?>(null)

    fun noun(kind: Int) = if (kind == MediaKind.PHOTO) "album" else "playlist"
}

@Composable
fun PlaylistDialogsHost(onDeleted: (Long) -> Unit = {}) {
    val marks = (LocalContext.current.applicationContext as DriveMountApp).mediaMarks
    val scope = rememberCoroutineScope()
    val videoLists by remember { marks.playlists(MediaKind.VIDEO) }.collectAsState(emptyList())
    val photoLists by remember { marks.playlists(MediaKind.PHOTO) }.collectAsState(emptyList())

    PlaylistUi.addKeys?.let { keys ->
        val kind = PlaylistUi.addKind
        val noun = PlaylistUi.noun(kind)
        val lists = if (kind == MediaKind.PHOTO) photoLists else videoLists
        var creating by remember(keys) { mutableStateOf(false) }
        var name by remember(keys) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { PlaylistUi.addKeys = null },
            title = { Text("Add to $noun") },
            text = {
                Column {
                    if (creating) OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("${noun.replaceFirstChar { it.uppercase() }} name") })
                    else {
                        TextButton(onClick = { creating = true }) { Text("+ New $noun") }
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(lists, key = { it.id }) { pl ->
                                ListItem(
                                    headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${pl.count} items") },
                                    modifier = Modifier.clickable {
                                        scope.launch { marks.addToPlaylist(pl.id, keys) }
                                        PlaylistUi.addKeys = null
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (creating) TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { marks.addToPlaylist(marks.createPlaylist(kind, name), keys) }
                    PlaylistUi.addKeys = null
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { PlaylistUi.addKeys = null }) { Text("Cancel") } },
        )
    }

    val nk = PlaylistUi.newKind
    val rt = PlaylistUi.rename
    if (nk != null || rt != null) {
        val kind = nk ?: MediaKind.VIDEO
        val noun = PlaylistUi.noun(kind)
        var name by remember(nk, rt) { mutableStateOf(rt?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { PlaylistUi.newKind = null; PlaylistUi.rename = null },
            title = { Text(if (rt != null) "Rename" else "New $noun") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { if (rt != null) marks.renamePlaylist(rt.id, name) else marks.createPlaylist(kind, name) }
                    PlaylistUi.newKind = null; PlaylistUi.rename = null
                }) { Text(if (rt != null) "Rename" else "Create") }
            },
            dismissButton = { TextButton(onClick = { PlaylistUi.newKind = null; PlaylistUi.rename = null }) { Text("Cancel") } },
        )
    }

    PlaylistUi.delete?.let { pl ->
        AlertDialog(
            onDismissRequest = { PlaylistUi.delete = null },
            title = { Text("Delete?") },
            text = { Text("\"${pl.name}\" will be deleted. The files themselves are not touched.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { marks.deletePlaylist(pl.id) }; onDeleted(pl.id); PlaylistUi.delete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { PlaylistUi.delete = null }) { Text("Cancel") } },
        )
    }
}

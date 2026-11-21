package com.drivemount.app.ui.zip

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.File as LuFile
import com.composables.icons.lucide.FileArchive as LuFileArchive
import com.composables.icons.lucide.Folder as LuFolder
import com.composables.icons.lucide.Lucide
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.meta.ZipEntryInfo
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.ui.components.Icon
import java.text.DateFormat
import java.util.Date

private class Child(val name: String, val isDir: Boolean, var size: Long, var count: Int, var mtime: Long)

/** জিপ এক্সট্রাক্ট না করেই ভেতরের ফোল্ডার-ট্রী, ফাইল ও সাইজ দেখা (লোকাল, ক্লাউড, ভল্ট) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipViewerScreen(accountId: Long, path: String, size: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DriveMountApp
    var cur by remember { mutableStateOf("") }
    val state by produceState<Pair<Boolean, List<ZipEntryInfo>?>>(true to null, accountId, path) {
        val acc = app.database.accountDao().getById(accountId)
        val list = if (acc == null) null else runCatching {
            app.zipIndex.load(acc.remoteName, fsRootFor(acc.remoteName, acc.backendType), path, size)
        }.getOrNull()
        value = false to list
    }
    val entries = state.second
    BackHandler(enabled = cur.isNotEmpty()) { cur = cur.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } }

    val children = remember(entries, cur) {
        val map = LinkedHashMap<String, Child>()
        entries?.forEach { e ->
            if (!e.path.startsWith(cur) || e.path.length == cur.length) return@forEach
            val rest = e.path.substring(cur.length)
            val first = rest.substringBefore('/')
            if (first.isEmpty()) return@forEach
            val dirChild = rest.contains('/')
            val c = map.getOrPut(first) { Child(first, dirChild, 0L, 0, 0L) }
            if (!e.isDir) { c.size += e.size; c.count++ }
            if (e.mtime > c.mtime) c.mtime = e.mtime
        }
        map.values.sortedWith(compareBy<Child> { !it.isDir }.thenBy { it.name.lowercase() })
    }
    val files = entries?.count { !it.isDir } ?: 0
    val total = entries?.sumOf { if (it.isDir) 0L else it.size } ?: 0L
    val csTotal = entries?.sumOf { if (it.isDir) 0L else it.csize } ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (cur.isEmpty()) "/" else "/$cur",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (cur.isNotEmpty()) cur = cur.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } else onBack()
                    }) { Icon(Lucide.LuArrowLeft, contentDescription = null) }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                state.first -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                entries == null -> Text(
                    "Couldn't read the contents of this zip (the file may be corrupted or unsupported).",
                    Modifier.align(Alignment.Center).padding(24.dp), style = MaterialTheme.typography.bodyMedium,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "$files ${if (files == 1) "file" else "files"} · ${Formatter.formatShortFileSize(context, total)} total · zip size ${Formatter.formatShortFileSize(context, if (size > 0) size else csTotal)}",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(children, key = { it.name + it.isDir }) { c ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .then(if (c.isDir) Modifier.clickable { cur = "$cur${c.name}/" } else Modifier)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    if (c.isDir) Lucide.LuFolder else if (c.name.substringAfterLast('.', "").lowercase() in setOf("zip", "rar", "7z", "tar", "gz")) Lucide.LuFileArchive else Lucide.LuFile,
                                    contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                    val sz = Formatter.formatShortFileSize(context, c.size)
                                    val date = if (c.mtime > 0) " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.mtime)) else ""
                                    Text(
                                        (if (c.isDir) "${c.count} ${if (c.count == 1) "file" else "files"} · " else "") + sz + date,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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

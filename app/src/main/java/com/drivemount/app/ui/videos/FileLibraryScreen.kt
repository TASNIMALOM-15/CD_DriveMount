package com.drivemount.app.ui.videos

import android.app.Application
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.File as LuFile
import com.composables.icons.lucide.FileText as LuFileText
import com.composables.icons.lucide.AppWindow as LuAppWindow
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Star as LuStar
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.transfer.NewTransfer
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import com.drivemount.app.ui.browser.FileThumbnail
import com.drivemount.app.ui.browser.openDownloadedFile
import com.drivemount.app.ui.browser.thumbKindOf
import com.drivemount.app.ui.player.PlaybackQueue
import kotlinx.coroutines.launch
import java.io.File

private val DOC_EXT = setOf(
    "pdf", "doc", "docx", "dot", "dotx", "odt", "rtf", "txt", "md", "xls", "xlsx", "csv", "ods",
    "ppt", "pptx", "odp", "epub", "mobi", "html", "htm", "json", "xml", "log",
)
private val APK_EXT = setOf("apk", "apks", "xapk", "apkm")

/** Docs ট্যাব: বেছে নেওয়া ফোল্ডারগুলোর সব ডকুমেন্ট */
class DocsViewModel(app: Application) :
    MediaLibraryViewModel(app, "docs_source", "docs_list.json", { it.name.substringAfterLast('.', "").lowercase() in DOC_EXT })

/** APKs ট্যাব: বেছে নেওয়া ফোল্ডারগুলোর সব APK (আইকন সাইডকার থেকে, ফাইল নামানো ছাড়াই) */
class ApksViewModel(app: Application) :
    MediaLibraryViewModel(app, "apks_source", "apks_list.json", { it.name.substringAfterLast('.', "").lowercase() in APK_EXT })

/** Favorites ট্যাব: প্রিয় ফোল্ডারগুলোর সব ধরনের ফাইল */
class FavoritesViewModel(app: Application) :
    MediaLibraryViewModel(app, "favorites_source", "favorites_list.json", { !it.isDir })

@Composable
fun DocsScreen(onPlay: (Long, String) -> Unit, vm: DocsViewModel = viewModel()) =
    FileLibraryScreen(vm, R.string.qa_docs, Lucide.LuFileText, R.string.docs_pick_title, R.string.docs_pick_hint, R.string.docs_none, onPlay)

@Composable
fun ApksScreen(onPlay: (Long, String) -> Unit, vm: ApksViewModel = viewModel()) =
    FileLibraryScreen(vm, R.string.qa_apks, Lucide.LuAppWindow, R.string.apks_pick_title, R.string.apks_pick_hint, R.string.apks_none, onPlay)

@Composable
fun FavoritesScreen(onPlay: (Long, String) -> Unit, vm: FavoritesViewModel = viewModel()) =
    FileLibraryScreen(vm, R.string.qa_favorites, Lucide.LuStar, R.string.fav_pick_title, R.string.fav_pick_hint, R.string.fav_none, onPlay)

/**
 * ফোল্ডার-নির্ভর ফাইল-তালিকা (Docs / APKs / Favorites)। ফোল্ডার বাছা, ডুপ্লিকেট রোধ, ব্যাকগ্রাউন্ড নোটিফিকেশন ও ক্যাশ
 * Photos/Videos/Music-এর মতোই (একই ViewModel-ভিত্তি)। ফাইলে চাপলে: অফলাইন কপি থাকলে সঙ্গে সঙ্গে খোলে, নইলে
 * নামিয়ে খোলে; ভিডিও/গান হলে প্লেয়ারে চলে।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileLibraryScreen(
    vm: MediaLibraryViewModel,
    titleRes: Int,
    icon: ImageVector,
    pickTitleRes: Int,
    pickHintRes: Int,
    noneRes: Int,
    onPlay: (Long, String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as DriveMountApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(VideoSort.NAME) }

    val groups = remember(state.items, state.selected, state.sources, sort) { groupMedia(state, sort) }
    val accounts = remember(state.sources) { state.sources.associate { it.account.id to it.account } }

    fun open(item: VideoItem) {
        val acc = accounts[item.accountId] ?: return
        val entry = item.entry
        val mime = guessMime(entry.name, entry.mimeType)
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            val queue = groups.flatMap { it.second }.filter { it.accountId == acc.id && guessMime(it.entry.name, it.entry.mimeType).let { m -> m.startsWith("video/") || m.startsWith("audio/") } }.map { it.entry }
            PlaybackQueue.set(PlaybackQueue.Queue(acc.id, queue, vm.siblings(item.sourceKey).ifEmpty { queue }))
            onPlay(acc.id, entry.path)
            return
        }
        // অফলাইনে রাখা কপি থাকলে সঙ্গে সঙ্গে
        val local = app.offlineStore.localFile(acc.id, entry.path)
        if (local != null) {
            if (!openDownloadedFile(context, local.absolutePath, mime)) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.browser_no_app)) }
            }
            return
        }
        // নইলে নামিয়ে খোলা (নামা শেষে নোটিফিকেশনে ট্যাপ করলে খোলে)
        val dir = File(context.cacheDir, "open/${System.currentTimeMillis()}").absolutePath
        scope.launch {
            app.transferRepository.enqueue(
                listOf(
                    NewTransfer(
                        name = entry.name,
                        isDir = false,
                        move = false,
                        srcFs = fsRootFor(acc.remoteName, acc.backendType),
                        srcPath = entry.path,
                        dstFs = dir,
                        dstPath = entry.name,
                        openWhenDone = true,
                    ),
                ),
            )
            snackbar.showSnackbar(context.getString(R.string.browser_queued, 1))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SourceMenuTitle(
                        state = state,
                        defaultTitle = stringResource(titleRes),
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.ready || (state.sources.isNotEmpty() && state.loading && state.visible.isEmpty()) ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.sources.isEmpty() -> PickPrompt(
                    icon = icon,
                    title = stringResource(pickTitleRes),
                    hint = stringResource(pickHintRes),
                    onPick = { picking = true },
                )

                state.visible.isEmpty() -> MediaEmpty(
                    error = state.error,
                    noneText = stringResource(noneRes),
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.refreshing || state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                            itemsIndexed(list, key = { i, v -> v.sourceKey + "|" + v.entry.path + "#" + i }) { _, item ->
                                val acc = accounts[item.accountId]
                                val kind = thumbKindOf(item.entry)
                                ListItem(
                                    headlineContent = { Text(item.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        item.entry.size?.let { Text(Formatter.formatShortFileSize(context, it)) }
                                    },
                                    leadingContent = {
                                        val fallback: @Composable () -> Unit = {
                                            Icon(
                                                imageVector = when (item.entry.name.substringAfterLast('.', "").lowercase()) {
                                                    in APK_EXT -> Lucide.LuAppWindow
                                                    in DOC_EXT -> Lucide.LuFileText
                                                    else -> Lucide.LuFile
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (acc != null && kind != null) {
                                            FileThumbnail(acc, item.entry, kind, 44.dp, fallback = fallback)
                                        } else {
                                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) { fallback() }
                                        }
                                    },
                                    modifier = Modifier.clickable(enabled = acc != null) { open(item) },
                                )
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

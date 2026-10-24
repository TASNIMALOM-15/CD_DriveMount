package com.drivemount.app.ui.browser

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.ClipboardContent
import com.drivemount.app.data.transfer.NewTransfer
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.LocalPaths
import com.drivemount.app.engine.browseRootFor
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.joinPath
import com.drivemount.app.engine.parentPath
import com.drivemount.app.engine.startPathFor
import com.drivemount.app.engine.uniqueName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** স্ক্রিনকে জানানোর ঘটনা */
sealed interface BrowserEvent {
    data class Message(val text: String) : BrowserEvent
    data class Queued(val count: Int) : BrowserEvent
    data class OpenFile(val path: String, val mime: String) : BrowserEvent
}

/** ডাউনলোডের ফোল্ডার; ব্যবহারকারীভেদে (ব্যক্তিগত/কাজের প্রোফাইল) বদলায় */
private val DOWNLOAD_DIR: String
    get() = LocalPaths.downloadDir()

enum class SortKey { NAME, DATE, SIZE }

data class SortOrder(val key: SortKey = SortKey.NAME, val ascending: Boolean = true)

data class Progress(val done: Int, val total: Int)

data class BrowserState(
    val account: AccountEntity? = null,
    val path: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val entries: List<RemoteEntry> = emptyList(),
    val sort: SortOrder = SortOrder(),
    /** null = খোঁজার বার বন্ধ */
    val query: String? = null,
    /** নির্বাচিত জিনিসের path */
    val selected: Set<String> = emptySet(),
    val progress: Progress? = null,
)

/** ফোল্ডার আগে, তারপর নির্বাচিত ক্রমে; খোঁজার লেখা থাকলে ছাঁকনি */
fun visibleEntries(entries: List<RemoteEntry>, sort: SortOrder, query: String?): List<RemoteEntry> {
    val filtered = if (query.isNullOrBlank()) entries else entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val byKey: Comparator<RemoteEntry> = when (sort.key) {
        SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortKey.DATE -> compareBy { it.modTimeMillis ?: 0L }
        SortKey.SIZE -> compareBy { it.size ?: 0L }
    }
    val ordered = if (sort.ascending) byKey else byKey.reversed()
    return filtered.sortedWith(compareByDescending<RemoteEntry> { it.isDir }.then(ordered))
}

class BrowserViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp
    private val files = appRef.fileRepository
    private val clipboardStore = appRef.clipboard
    private val transfers = appRef.transferRepository
    private val accountId: Long = handle.get<Long>("accountId") ?: -1L

    val clipboard: StateFlow<ClipboardContent?> = clipboardStore.content

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private suspend fun message(text: String) = _events.send(BrowserEvent.Message(text))

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val account = appRef.accountRepository.getById(accountId)
            if (account == null) {
                _state.update { it.copy(loading = false, error = str(R.string.browser_account_missing)) }
                return@launch
            }
            val start = startPathFor(account.backendType)
            _state.update { it.copy(account = account, path = start) }
            load(start)
        }
    }

    init {
        // ডাউনলোড শেষ হলে ফাইল খোলার অনুরোধ, আর কোনো ট্রান্সফার শেষ হলে তালিকা হালনাগাদ
        viewModelScope.launch {
            transfers.openRequests.collect { _events.send(BrowserEvent.OpenFile(it.path, it.mime)) }
        }
        viewModelScope.launch {
            transfers.finished.collect { silentRefresh() }
        }
    }

    /** তালিকা চুপচাপ নতুন করে আনে; নির্বাচন ও খোঁজার লেখা অক্ষুণ্ণ থাকে */
    private fun silentRefresh() {
        val account = _state.value.account ?: return
        val path = _state.value.path
        viewModelScope.launch {
            val result = files.list(account, path)
            if (_state.value.path != path) return@launch
            result.onSuccess { list ->
                _state.update {
                    val alive = list.map { e -> e.path }.toSet()
                    it.copy(entries = list, selected = it.selected.intersect(alive))
                }
            }
        }
    }

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    val root: String
        get() = _state.value.account?.let { browseRootFor(it.backendType) } ?: ""

    // ---------------- দেখা ও চলাচল ----------------

    private fun load(path: String) {
        val account = _state.value.account ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(path = path, loading = true, error = null, selected = emptySet(), query = null)
        }
        loadJob = viewModelScope.launch {
            val result = files.list(account, path)
            ensureActive() // নতুন ফোল্ডারে চলে গেলে পুরোনো ফল ফেলে দেওয়া হয়
            result.fold(
                onSuccess = { list -> _state.update { it.copy(entries = list, loading = false) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(entries = emptyList(), loading = false, error = e.message ?: e.toString())
                    }
                },
            )
        }
    }

    fun refresh() = load(_state.value.path)

    fun open(entry: RemoteEntry) {
        if (entry.isDir) load(entry.path)
    }

    fun navigateTo(path: String) = load(path)

    /** এক ধাপ ওপরে যায়; ওপরে যাওয়ার জায়গা না থাকলে false */
    fun up(): Boolean {
        val current = _state.value.path
        if (current == root) return false
        load(parentPath(current).let { if (it.length < root.length) root else it })
        return true
    }

    // ---------------- নির্বাচন, খোঁজা, সাজানো ----------------

    fun toggleSelect(entry: RemoteEntry) {
        _state.update {
            val next = if (entry.path in it.selected) it.selected - entry.path else it.selected + entry.path
            it.copy(selected = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun setQuery(query: String?) = _state.update { it.copy(query = query) }

    fun setSort(key: SortKey) = _state.update {
        val s = it.sort
        it.copy(sort = if (s.key == key) s.copy(ascending = !s.ascending) else SortOrder(key, true))
    }

    // ---------------- কাজ ----------------

    private fun selectedEntries(): List<RemoteEntry> {
        val s = _state.value
        return s.entries.filter { it.path in s.selected }
    }

    /** কাজ চলার সময় অগ্রগতি দেখায়; শেষে তালিকা নতুন করে আনে */
    private fun runBusy(total: Int, block: suspend (step: () -> Unit) -> Unit) {
        viewModelScope.launch {
            var done = 0
            _state.update { it.copy(progress = Progress(0, total)) }
            try {
                block {
                    done++
                    _state.update { s -> s.copy(progress = Progress(done, total)) }
                }
            } catch (t: Throwable) {
                message(t.message ?: t.toString())
            }
            _state.update { it.copy(progress = null) }
            load(_state.value.path)
        }
    }

    /** প্রতিটি জিনিসে কাজ চালায়; কোনোটি ব্যর্থ হলেও বাকিগুলো চলে, শেষে ব্যর্থগুলোর খবর দেয় */
    private suspend fun each(
        items: List<RemoteEntry>,
        step: () -> Unit,
        op: suspend (RemoteEntry) -> Result<Unit>,
    ) {
        val errors = mutableListOf<String>()
        for (item in items) {
            op(item).onFailure { errors += "${item.name}: ${it.message ?: it}" }
            step()
        }
        if (errors.isNotEmpty()) throw RuntimeException(errors.joinToString("\n"))
    }

    private fun validName(name: String): Boolean = name.isNotBlank() && '/' !in name

    fun createFolder(name: String) {
        val account = _state.value.account ?: return
        val clean = name.trim()
        if (!validName(clean)) {
            viewModelScope.launch { message(str(R.string.browser_bad_name)) }
            return
        }
        val path = joinPath(_state.value.path, clean)
        runBusy(1) { step ->
            files.mkdir(account, path).getOrThrow()
            step()
        }
    }

    fun rename(entry: RemoteEntry, newName: String) {
        val account = _state.value.account ?: return
        val clean = newName.trim()
        if (!validName(clean)) {
            viewModelScope.launch { message(str(R.string.browser_bad_name)) }
            return
        }
        if (clean == entry.name) return
        if (_state.value.entries.any { it.name == clean }) {
            viewModelScope.launch { message(str(R.string.browser_name_exists)) }
            return
        }
        val newPath = joinPath(parentPath(entry.path), clean)
        runBusy(1) { step ->
            files.rename(account, entry, newPath).getOrThrow()
            step()
        }
    }

    fun deleteSelected() {
        val account = _state.value.account ?: return
        val items = selectedEntries()
        if (items.isEmpty()) return
        runBusy(items.size) { step ->
            each(items, step) { files.delete(account, it) }
        }
    }

    /** নির্বাচিতগুলো কপি (move=false) বা কাটার (move=true) জন্য জমা রাখে */
    fun copySelected(move: Boolean) {
        val account = _state.value.account ?: return
        val items = selectedEntries()
        if (items.isEmpty()) return
        clipboardStore.set(ClipboardContent(account, items, move))
        clearSelection()
    }

    fun cancelClipboard() = clipboardStore.clear()

    /** কপি/কাটা জিনিসগুলো এই ফোল্ডারে পেস্টের জন্য ট্রান্সফার লাইনে দেয় */
    fun paste() {
        val dest = _state.value.account ?: return
        val clip = clipboardStore.content.value ?: return
        val destPath = _state.value.path
        val existing = _state.value.entries.map { it.name }.toMutableSet()
        val sameAccount = clip.source.id == dest.id
        val srcFs = fsRootFor(clip.source.remoteName, clip.source.backendType)
        val dstFs = fsRootFor(dest.remoteName, dest.backendType)

        val tasks = mutableListOf<NewTransfer>()
        val errors = mutableListOf<String>()
        for (item in clip.items) {
            val intoItself = item.isDir && sameAccount &&
                (destPath == item.path || destPath.startsWith(item.path + "/"))
            val sameFolderMove = clip.move && sameAccount && parentPath(item.path) == destPath

            when {
                intoItself -> errors += "${item.name}: ${str(R.string.browser_into_itself)}"
                sameFolderMove -> Unit // একই জায়গায় সরানো মানে কিছুই না
                else -> {
                    val target = uniqueName(item.name, existing)
                    existing += target
                    tasks += NewTransfer(
                        name = item.name,
                        isDir = item.isDir,
                        move = clip.move,
                        srcFs = srcFs,
                        srcPath = item.path,
                        dstFs = dstFs,
                        dstPath = joinPath(destPath, target),
                    )
                }
            }
        }

        viewModelScope.launch {
            if (tasks.isNotEmpty()) {
                transfers.enqueue(tasks)
                _events.send(BrowserEvent.Queued(tasks.size))
            }
            if (clip.move && errors.isEmpty()) clipboardStore.clear()
            if (errors.isNotEmpty()) message(errors.joinToString("\n"))
        }
    }

    // ---------------- ডাউনলোড, খোলা, আপলোড ----------------

    fun downloadSelected() {
        val items = selectedEntries()
        clearSelection()
        download(items)
    }

    /** ফোনের Download/DriveMount ফোল্ডারে নামায় */
    fun download(items: List<RemoteEntry>) {
        val account = _state.value.account ?: return
        if (items.isEmpty()) return
        val srcFs = fsRootFor(account.remoteName, account.backendType)
        viewModelScope.launch {
            val existing = transfers.existingNames(DOWNLOAD_DIR, "").toMutableSet()
            val tasks = items.map { item ->
                val target = uniqueName(item.name, existing)
                existing += target
                NewTransfer(
                    name = item.name,
                    isDir = item.isDir,
                    move = false,
                    srcFs = srcFs,
                    srcPath = item.path,
                    dstFs = DOWNLOAD_DIR,
                    dstPath = target,
                )
            }
            transfers.enqueue(tasks)
            _events.send(BrowserEvent.Queued(tasks.size))
        }
    }

    /** অস্থায়ীভাবে নামিয়ে অন্য অ্যাপে খোলে (শেষ হলে OpenFile ঘটনা আসে) */
    fun openFile(entry: RemoteEntry) {
        val account = _state.value.account ?: return
        val dir = File(getApplication<Application>().cacheDir, "open/${System.currentTimeMillis()}").absolutePath
        viewModelScope.launch {
            transfers.enqueue(
                listOf(
                    NewTransfer(
                        name = entry.name,
                        isDir = false,
                        move = false,
                        srcFs = fsRootFor(account.remoteName, account.backendType),
                        srcPath = entry.path,
                        dstFs = dir,
                        dstPath = entry.name,
                        openWhenDone = true,
                    ),
                ),
            )
            _events.send(BrowserEvent.Queued(1))
        }
    }

    /** বাছাই করা ফাইলগুলো এই ফোল্ডারে আপলোডের জন্য লাইনে দেয় */
    fun upload(uris: List<Uri>) {
        val account = _state.value.account ?: return
        if (uris.isEmpty()) return
        val path = _state.value.path
        val resolver = getApplication<Application>().contentResolver
        val dstFs = fsRootFor(account.remoteName, account.backendType)
        viewModelScope.launch {
            val existing = _state.value.entries.map { it.name }.toMutableSet()
            val tasks = uris.map { uri ->
                val name = displayName(resolver, uri)
                val target = uniqueName(name, existing)
                existing += target
                NewTransfer(
                    name = name,
                    isDir = false,
                    move = false,
                    srcFs = "",
                    srcPath = "",
                    dstFs = dstFs,
                    dstPath = joinPath(path, target),
                    sourceUri = uri.toString(),
                )
            }
            transfers.enqueue(tasks)
            _events.send(BrowserEvent.Queued(tasks.size))
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val fromProvider = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "file"
    }
}

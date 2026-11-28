package com.drivemount.app.ui.browser

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.drivemount.app.engine.guessMime
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import com.drivemount.app.data.transfer.TransferEntity
import com.drivemount.app.data.transfer.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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

/** একটি ফোল্ডার দেখানোর পর পেছনে যতগুলো সাব-ফোল্ডার আগাম আনা হবে */
private const val PREFETCH_FOLDERS = 6

enum class SortKey { NAME, DATE, SIZE, TYPE }

data class SortOrder(val key: SortKey = SortKey.NAME, val ascending: Boolean = true)

data class Progress(val done: Int, val total: Int)

/**
 * এই ফোল্ডারে ঢুকছে (আপলোড/কপি চলছে) এমন ফাইল: ক্লাউডে পুরো না পৌঁছানো পর্যন্ত তালিকায় ধূসর অবস্থায় দেখানো হয়,
 * আর [fraction] অনুযায়ী বাম থেকে ডানে রঙিন হয়। শুধু দেখানোর জন্য; নির্বাচন/খোলা/মোছা যায় না।
 */
data class UploadGhost(
    val entry: RemoteEntry,
    val status: String,
    /** ০.০–১.০ */
    val fraction: Float,
    val done: Long,
    val total: Long,
    val speed: Long,
    /** থাম্বনেলের জন্য ফোনের উৎস: content URI বা ফাইলের পথ (দুটির একটি, না-ও থাকতে পারে) */
    val thumbUri: String?,
    val thumbPath: String?,
    /** আপলোড-পূর্ব ধাপ ([com.drivemount.app.data.transfer.TransferStage]); জানা না থাকলে null */
    val stage: Int? = null,
)

/** Galaxy: ফাইলটি total টি প্ল্যাটফর্মের মধ্যে have টিতে আছে */
data class ReplicaStatus(val have: Int, val total: Int)

private const val REPLICA_TTL_MS = 24L * 60 * 60 * 1000 // ১ দিন

data class BrowserState(
    val account: AccountEntity? = null,
    val path: String = "",
    val loading: Boolean = true,
    /** ওপরে টেনে রিফ্রেশ চলছে (তালিকা পর্দায় থাকে) */
    val refreshing: Boolean = false,
    val error: String? = null,
    val entries: List<RemoteEntry> = emptyList(),
    val sort: SortOrder = SortOrder(),
    /** null = খোঁজার বার বন্ধ */
    val query: String? = null,
    /** নির্বাচিত জিনিসের path */
    val selected: Set<String> = emptySet(),
    val progress: Progress? = null,
)

private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv", "wmv", "mpg", "mpeg", "ts", "m2ts", "vob")
private val AUDIO_EXT = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "amr", "mka", "aiff")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff", "svg", "avif", "ico", "raw", "dng")
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso")

/** ধরন অনুযায়ী ক্রম-সংখ্যা (TYPE সাজানোর জন্য) */
fun typeRank(name: String): Int {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in VIDEO_EXT -> 0
        ext in AUDIO_EXT -> 1
        ext in IMAGE_EXT -> 2
        ext in com.drivemount.app.ui.videos.DOC_EXT -> 3
        ext in com.drivemount.app.ui.videos.APK_EXT -> 4
        ext in ARCHIVE_EXT -> 5
        else -> 6
    }
}

/** ফোল্ডার আগে, তারপর নির্বাচিত ক্রমে; খোঁজার লেখা থাকলে ছাঁকনি */
fun visibleEntries(entries: List<RemoteEntry>, sort: SortOrder, query: String?): List<RemoteEntry> {
    // Galaxy ট্র্যাশ-ফোল্ডার লুকানো: সেটি আলাদা ট্র্যাশ-পাতায় দেখা যায়
    val shown = entries.filter { it.name != com.drivemount.app.data.galaxy.Galaxy.TRASH }
    val filtered = if (query.isNullOrBlank()) shown else shown.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val byKey: Comparator<RemoteEntry> = when (sort.key) {
        SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortKey.DATE -> compareBy { it.modTimeMillis ?: 0L }
        SortKey.SIZE -> compareBy { it.size ?: 0L }
        // ধরন: ভিডিও, গান, ছবি, ডক, APK, আর্কাইভ, বাকি; তারপর এক্সটেনশন (mp4, mkv, jpg...), তারপর নাম
        SortKey.TYPE -> compareBy<RemoteEntry> { typeRank(it.name) }
            .thenBy { it.name.substringAfterLast('.', "").lowercase() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
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

    /** এনক্রিপ্টেড ভল্টগুলো ("Yes, encrypt" বেছে নিলে ফাইল এগুলোতেই যায়) */
    val vaults: StateFlow<List<AccountEntity>> = appRef.accountRepository.accounts
        .map { list -> list.filter { it.backendType == "crypt" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** শুধু এই ক্লাউডের ভেতরের ভল্টগুলো (My vault) — ক্লাউডে ঢুকে এখান থেকেই ভল্টে যাওয়া যায় */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val cloudVaults: StateFlow<List<AccountEntity>> = appRef.accountRepository.accounts
        .mapLatest { list ->
            val crypts = list.filter { it.backendType == "crypt" }
            if (crypts.isEmpty()) return@mapLatest emptyList()
            val config = runCatching { appRef.rcloneApi.dumpConfig() }.getOrDefault(emptyMap())
            val byRemote = list.associateBy { it.remoteName }
            fun baseOf(vault: AccountEntity): AccountEntity? {
                var current = vault
                repeat(4) {
                    val parentName = config[current.remoteName]?.optString("remote")?.substringBefore(':')
                    val parent = parentName?.let { byRemote[it] } ?: return null
                    if (parent.backendType != "crypt") return parent
                    current = parent
                }
                return null
            }
            crypts.filter { baseOf(it)?.id == accountId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    /** সার্চ/নোটিফিকেশন থেকে এলে যে ফাইল বা ফোল্ডারটি কয়েক সেকেন্ড হাইলাইট হবে (পূর্ণ path); না থাকলে খালি */
    private val _highlight = MutableStateFlow(handle.get<String>("hl").orEmpty())
    val highlight: StateFlow<String> = _highlight

    fun clearHighlight() { _highlight.value = "" }

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private suspend fun message(text: String) = _events.send(BrowserEvent.Message(text))

    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var replicaJob: Job? = null

    /** Galaxy (বা তার ভল্ট) হলে মোছা মানে ৩০ দিনের ট্র্যাশে পাঠানো */
    private val _trashMode = MutableStateFlow(false)
    val trashMode: StateFlow<Boolean> = _trashMode

    /** Galaxy ফোল্ডারে ফাইল-প্রতি কপি-অবস্থা (path -> অবস্থা); Galaxy না হলে খালি */
    private val _replicas = MutableStateFlow<Map<String, ReplicaStatus>>(emptyMap())
    val replicas: StateFlow<Map<String, ReplicaStatus>> = _replicas

    private var datesJob: kotlinx.coroutines.Job? = null

    /** ফোল্ডারের ফাইলগুলোর দুটি তারিখ (তোলা/আপলোড): আগে ফোনের ক্যাশ থেকে, না থাকলে ক্লাউডের ছোট প্যাক থেকে */
    private fun loadDates(account: AccountEntity, path: String, list: List<RemoteEntry>) {
        datesJob?.cancel()
        if (account.backendType == "local" || list.none { !it.isDir }) return
        datesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fd = appRef.fileDates
            var changed = fd.warm(account.remoteName, list)
            if (changed) appRef.sidecar.version.intValue++
            changed = fd.ensureFolder(account.remoteName, fsRootFor(account.remoteName, account.backendType), path, list)
            if (changed) appRef.sidecar.version.intValue++
        }
    }

    private var photoPrefetchJob: kotlinx.coroutines.Job? = null

    /**
     * ফোল্ডার খুললে এর ছবিগুলোর মাঝারি প্রিভিউ (ছোট ছবির ক্ষেত্রে আসলটাই) ফোনে আগেভাগে জমায়, যাতে অফলাইনেও
     * স্পষ্ট ছবি দেখা যায়। শুধু Wi-Fi-তে (Photos সেটিং চালু থাকলে), পেছনে, নতুন ফোল্ডারে গেলে বাতিল।
     */
    private fun prefetchPhotos(account: AccountEntity, list: List<RemoteEntry>) {
        photoPrefetchJob?.cancel()
        val pv = appRef.photoPreviews
        if (account.backendType == "local" || !pv.offlineEnabled) return
        val cm = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (cm.isActiveNetworkMetered) return
        val images = list.filter { !it.isDir && (it.size ?: 0L) > 0 && com.drivemount.app.data.meta.ThumbMetaReader.isImage(it.name) }.take(120)
        if (images.isEmpty()) return
        photoPrefetchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(1500)
            val fs = fsRootFor(account.remoteName, account.backendType)
            var failures = 0
            for (e in images) {
                ensureActive()
                val size = e.size ?: continue
                if (pv.has(account.remoteName, e.path, size)) continue
                val ok = if (pv.needsPreview(size)) pv.fetchRemote(account.remoteName, fs, e.path, size)
                else pv.fetchSmallOriginal(account.remoteName, fs, e.path, size)
                if (ok) failures = 0 else if (++failures >= 8) break // সাইডকার নেই: অকারণ রিকোয়েস্ট থামানো
            }
        }
    }

    private val replicaPrefs by lazy {
        getApplication<Application>().getSharedPreferences("replica_cache", android.content.Context.MODE_PRIVATE)
    }

    /** ফোল্ডারের কপি-অবস্থা ডিস্কে জমা: (সময়, নাম -> have/total/size) */
    private class ReplicaCacheEntry(val time: Long, val map: Map<String, Triple<Int, Int, Long?>>)

    private fun readReplicaCache(key: String): ReplicaCacheEntry? = runCatching {
        val o = org.json.JSONObject(replicaPrefs.getString(key, null) ?: return null)
        val f = o.getJSONObject("f")
        val m = HashMap<String, Triple<Int, Int, Long?>>()
        for (name in f.keys()) {
            val a = f.getJSONArray(name)
            m[name] = Triple(a.getInt(0), a.getInt(1), if (a.isNull(2)) null else a.getLong(2))
        }
        ReplicaCacheEntry(o.getLong("t"), m)
    }.getOrNull()

    private fun writeReplicaCache(key: String, status: Map<String, ReplicaStatus>, sizes: Map<String, Long?>) {
        runCatching {
            val f = org.json.JSONObject()
            for ((name, st) in status) {
                f.put(name, org.json.JSONArray().put(st.have).put(st.total).put(sizes[name] ?: org.json.JSONObject.NULL))
            }
            val o = org.json.JSONObject().put("t", System.currentTimeMillis()).put("f", f)
            val ed = replicaPrefs.edit()
            // জমা বেশি হলে সবচেয়ে পুরোনোগুলো বাদ
            val all = replicaPrefs.all
            if (all.size > 300) {
                all.keys.sortedBy { k -> readReplicaCache(k)?.time ?: 0L }.take(100).forEach { ed.remove(it) }
            }
            ed.putString(key, o.toString()).apply()
        }
    }

    /**
     * কপি-অবস্থা (⚠/✓ n/m): আগে ডিস্কের জমা-ফল সঙ্গে সঙ্গে দেখায়। নেটওয়ার্কে সব প্ল্যাটফর্ম চেক হয় শুধু যখন:
     * ফোল্ডারে নতুন ফাইল আছে যার অবস্থা জানা নেই, বা শেষ চেকের পর ১ দিন পেরিয়েছে,
     * বা force = true (আপলোড/মোছা/সরানো/রিফ্রেশের পর)। পেছনে, ধীরে; নতুন ফোল্ডারে গেলে বাতিল;
     * কোনো পুলে সংযোগ না পেলে কিছুই দাবি করা হয় না।
     */
    private fun checkReplicas(account: AccountEntity, path: String, list: List<RemoteEntry>, force: Boolean = false) {
        replicaJob?.cancel()
        val files = list.filter { !it.isDir }
        if (files.isEmpty()) { _replicas.value = emptyMap(); return }
        val key = "${account.id}|$path"
        val cache = readReplicaCache(key)
        val cached = HashMap<String, ReplicaStatus>()
        if (cache != null) {
            for (f in files) {
                val c = cache.map[f.name] ?: continue
                if (f.size == null || c.third == null || c.third == f.size) cached[f.path] = ReplicaStatus(c.first, c.second)
            }
        }
        _replicas.value = cached
        val fresh = cache != null && System.currentTimeMillis() - cache.time < REPLICA_TTL_MS
        if (!force && fresh && cached.size == files.size) return
        replicaJob = viewModelScope.launch {
            delay(300)
            val roots = runCatching { appRef.galaxy.replicaRoots(account) }.getOrNull() ?: return@launch
            val sets = ArrayList<Map<String, Long?>>()
            for (fs in roots) {
                ensureActive()
                val r = runCatching { appRef.rcloneApi.list(fs, path) }
                val l = r.getOrNull()
                if (l != null) {
                    sets += l.filter { !it.isDir }.associate { it.name to it.size }
                } else if (r.exceptionOrNull()?.message?.contains("not found", ignoreCase = true) == true) {
                    sets += emptyMap() // ফোল্ডারটিই ওই প্ল্যাটফর্মে নেই: সেখানে কোনো ফাইল নেই
                } else {
                    return@launch // সংযোগ-সমস্যা: ভুল দাবি না করে কিছু দেখানো নয়
                }
            }
            if (_state.value.path != path) return@launch
            val byName = files.associate { f ->
                f.name to ReplicaStatus(
                    sets.count { m ->
                        m.containsKey(f.name) && (f.size == null || m[f.name] == null || m[f.name] == f.size)
                    },
                    sets.size,
                )
            }
            _replicas.value = files.associate { f -> f.path to byName.getValue(f.name) }
            writeReplicaCache(key, byName, files.associate { it.name to it.size })
        }
    }

    /** নতুন সাব-ফোল্ডারের তালিকা জমলে বাড়ে; ফোল্ডারের "N items" লেখা তখন হালনাগাদ হয় */
    private val _countTick = MutableStateFlow(0)
    val countTick: StateFlow<Int> = _countTick

    /**
     * ফোল্ডারের ভেতরের আইটেম সংখ্যা: শুধু আগে-আনা তালিকা থেকে (মেমরি, তারপর ডিস্ক); নেটওয়ার্ক কল নেই, তাই স্ক্রল হালকা থাকে।
     * জানা না থাকলে null (লেখা দেখায় না)।
     */
    suspend fun childCount(path: String): Int? {
        val account = _state.value.account ?: return null
        files.cached(account, path)?.let { return it.size }
        if (account.backendType == "local") return null
        return files.persisted(account, path)?.size
    }

    /**
     * ফোল্ডার দেখানোর পর তার প্রথম কয়েকটি সাব-ফোল্ডারের তালিকা পেছনে একে একে আনা (ধীরে, একটার পর একটা),
     * যাতে ঢুকলে সঙ্গে সঙ্গে খোলে। ব্যবহারকারী অন্য ফোল্ডারে গেলে বা নতুন কাজ শুরু হলে সঙ্গে সঙ্গে থামে।
     */
    private fun prefetchChildren(account: AccountEntity, path: String, list: List<RemoteEntry>) {
        prefetchJob?.cancel()
        if (account.backendType == "local") return
        val dirs = visibleEntries(list, _state.value.sort, null).filter { it.isDir }.take(PREFETCH_FOLDERS)
        if (dirs.isEmpty()) return
        prefetchJob = viewModelScope.launch {
            delay(600)
            for (d in dirs) {
                ensureActive()
                if (_state.value.path != path) return@launch
                if (files.cached(account, d.path) != null) continue
                files.list(account, d.path)
                _countTick.value++
                delay(200)
            }
        }
    }

    init {
        viewModelScope.launch {
            val account = appRef.accountRepository.getById(accountId)
            if (account == null) {
                _state.update { it.copy(loading = false, error = str(R.string.browser_account_missing)) }
                return@launch
            }
            // সার্চের ফল থেকে এলে নির্দিষ্ট ফোল্ডারে খোলে; নইলে অ্যাকাউন্টের শুরুর ফোল্ডারে
            val start = handle.get<String>("path").orEmpty().ifEmpty { startPathFor(account.backendType) }
            _state.update { it.copy(account = account, path = start) }
            load(start)
            _trashMode.value = runCatching { appRef.galaxy.isGalaxyBacked(account) }.getOrDefault(false)
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
                checkReplicas(account, path, list, force = true) // আপলোড/কপি শেষ: কপি-অবস্থা নতুন করে
            }
        }
    }

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    val root: String
        get() = _state.value.account?.let { browseRootFor(it.backendType) } ?: ""

    // ---------------- দেখা ও চলাচল ----------------

    /**
     * useCache = true: আগে দেখা ফোল্ডার হলে তালিকা সঙ্গে সঙ্গে দেখায়, পেছনে নতুন তালিকা এনে বদলে দেয়।
     * কাজ (মোছা/সরানো...) বা রিফ্রেশের পর false দেওয়া হয়, যাতে পুরোনো তালিকা না দেখায়।
     */
    private fun load(path: String, useCache: Boolean = true) {
        val account = _state.value.account ?: return
        loadJob?.cancel()
        prefetchJob?.cancel() // ব্যবহারকারীর নিজের অনুরোধ আগে: পেছনের কাজ নেটওয়ার্ক আটকাবে না
        val hit = if (useCache) files.cached(account, path) else null
        _state.update {
            it.copy(
                path = path,
                loading = hit == null,
                error = null,
                selected = emptySet(),
                query = null,
                entries = hit ?: it.entries,
            )
        }
        loadJob = viewModelScope.launch {
            // মেমরিতে না থাকলেও ডিস্কের শেষ তালিকা (সাধারণ ক্লাউডে ইনডেক্স, ভল্টে এনক্রিপ্টেড ক্যাশ) সঙ্গে সঙ্গে দেখানো; নতুন তালিকা নিচে এসে বদলায়
            var shownStale = false
            if (hit == null && useCache) {
                files.persisted(account, path)?.let { old ->
                    shownStale = true
                    _state.update { if (it.path == path && it.loading) it.copy(entries = old, loading = false) else it }
                }
            }
            val result = files.list(account, path)
            ensureActive() // নতুন ফোল্ডারে চলে গেলে পুরোনো ফল ফেলে দেওয়া হয়
            result.fold(
                onSuccess = { list ->
                    // দেখানো তালিকার সঙ্গে হুবহু এক হলে তালিকা বদলানো হয় না: বড় grid অকারণে আবার আঁকা হয় না
                    _state.update { if (it.entries == list) it.copy(loading = false) else it.copy(entries = list, loading = false) }
                    prefetchChildren(account, path, list)
                    checkReplicas(account, path, list, force = !useCache)
                    prefetchPhotos(account, list)
                    loadDates(account, path, list)
                },
                onFailure = { e ->
                    _state.update {
                        if (hit != null || shownStale) {
                            it.copy(loading = false) // দেখানো তালিকা থাক; নেটওয়ার্ক ফিরলে রিফ্রেশ করা যাবে
                        } else {
                            it.copy(entries = emptyList(), loading = false, error = e.message ?: e.toString())
                        }
                    }
                },
            )
        }
    }

    /** তালিকার ওপরে নিচে টানলে: বর্তমান তালিকা পর্দায় রেখেই পেছনে নতুন তালিকা এনে বদলায় */
    fun pullRefresh() {
        val account = _state.value.account ?: return
        if (_state.value.refreshing) return
        val path = _state.value.path
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val result = files.list(account, path)
            if (_state.value.path != path) {
                _state.update { it.copy(refreshing = false) }
                return@launch
            }
            result.fold(
                onSuccess = { list ->
                    _state.update {
                        val alive = list.map { e -> e.path }.toSet()
                        it.copy(
                            entries = if (it.entries == list) it.entries else list,
                            selected = it.selected.intersect(alive),
                            error = null,
                            refreshing = false,
                        )
                    }
                    prefetchChildren(account, path, list)
                    checkReplicas(account, path, list, force = true)
                    prefetchPhotos(account, list)
                    loadDates(account, path, list)
                },
                onFailure = { e ->
                    _state.update {
                        if (it.entries.isEmpty()) it.copy(refreshing = false, error = e.message ?: e.toString())
                        else it.copy(refreshing = false)
                    }
                    if (_state.value.entries.isNotEmpty()) message(e.message ?: e.toString())
                },
            )
        }
    }

    fun open(entry: RemoteEntry) {
        if (entry.isDir) load(entry.path)
    }

    fun navigateTo(path: String) = load(path)

    /** এক ধাপ ওপরে যায়; ওপরে যাওয়ার জায়গা না থাকলে false */
    fun up(): Boolean {
        val current = _state.value.path
        if (current == root) return false
        // ফোনের স্টোরেজ: শুরুর ফোল্ডার (যেমন /storage/emulated/0) থেকে ব্যাক চাপলে সরাসরি বেরিয়ে যায়,
        // storage > emulated স্তরগুলোতে নিয়ে যায় না
        if (_state.value.account?.backendType == "local" && current == startPathFor("local")) return false
        load(parentPath(current).let { if (it.length < root.length) root else it })
        return true
    }

    // ---------------- নির্বাচন, খোঁজা, সাজানো ----------------

    /** শেষ যে জিনিস বাছা/ছাড়া হয়েছে (দীর্ঘ চাপে মাঝের সবগুলো বাছতে) */
    private var anchorPath: String? = null

    fun toggleSelect(entry: RemoteEntry) {
        anchorPath = entry.path
        _state.update {
            val next = if (entry.path in it.selected) it.selected - entry.path else it.selected + entry.path
            it.copy(selected = next)
        }
    }

    /**
     * আগে থেকে কিছু বাছা থাকলে, আগের বাছা জিনিস থেকে [entry] পর্যন্ত মাঝের সব (ফোল্ডার/ফাইল) বাছা হয়;
     * নইলে শুধু এটিই। [visible] হলো পর্দায় দেখানো ক্রম।
     */
    fun selectRangeTo(entry: RemoteEntry, visible: List<RemoteEntry>) {
        val anchor = anchorPath
        val s = _state.value.selected
        val a = visible.indexOfFirst { it.path == anchor }
        val b = visible.indexOfFirst { it.path == entry.path }
        if (s.isEmpty() || a < 0 || b < 0) { toggleSelect(entry); return }
        val range = visible.subList(minOf(a, b), maxOf(a, b) + 1).map { it.path }
        anchorPath = entry.path
        _state.update { it.copy(selected = it.selected + range) }
    }

    fun selectAll(paths: Collection<String>) = _state.update { it.copy(selected = paths.toSet()) }

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
            // তালিকা পর্দায় রেখেই নতুন তালিকা এনে বদলায় (লোডিং-স্পিনারে ফেলে ঝলকানি/গ্লিচ হয় না)
            silentRefresh()
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
        // সঙ্গে সঙ্গে তালিকা থেকে সরানো (আশাবাদী); ব্যর্থ হলে পরের রিফ্রেশে ফিরে আসে
        val gone = items.map { it.path }.toSet()
        _state.update { it.copy(entries = it.entries.filter { e -> e.path !in gone }, selected = emptySet()) }
        runBusy(items.size) { step ->
            if (_trashMode.value) {
                val ts = System.currentTimeMillis()
                var i = 0
                each(items, step) { appRef.galaxy.moveToTrash(account, it, "${ts}_${i++}") }
            } else {
                each(items, step) {
                    val r = files.delete(account, it)
                    if (r.isSuccess) appRef.folderSizes.removed(account.remoteName, it.path, it.size, it.isDir)
                    r
                }
            }
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
    /** vault দেওয়া হলে ফাইলগুলো (এনক্রিপ্ট হয়ে) সেই ভল্টের মূল ফোল্ডারে যায় */
    fun paste(vault: AccountEntity? = null) {
        val dest = vault ?: _state.value.account ?: return
        val clip = clipboardStore.content.value ?: return
        val destPath = if (vault != null) "" else _state.value.path
        val sameAccount = clip.source.id == dest.id
        val srcFs = fsRootFor(clip.source.remoteName, clip.source.backendType)
        val dstFs = fsRootFor(dest.remoteName, dest.backendType)

        viewModelScope.launch {
        val existing = if (vault != null) {
            files.list(vault, "").getOrDefault(emptyList()).map { it.name }.toMutableSet()
        } else {
            _state.value.entries.map { it.name }.toMutableSet()
        }
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

    /** খোলার জন্য নামছে এমন ফাইলের শতাংশ (পথ -> ০–৯৯); ফাইলের সারি/ঘরের ওপর হালকা করে দেখানো হয় */
    val openProgress: StateFlow<Map<String, Int>> =
        combine(
            transfers.transfers,
            _state.map { it.account }.distinctUntilChanged(),
        ) { list, account ->
            if (account == null) {
                emptyMap()
            } else {
                val fs = fsRootFor(account.remoteName, account.backendType)
                list.filter {
                    it.openWhenDone && it.srcFs == fs &&
                        (it.status == TransferStatus.QUEUED || it.status == TransferStatus.PREPARING || it.status == TransferStatus.RUNNING)
                }.associate { t ->
                    t.srcPath to if (t.bytesTotal > 0) (t.bytesDone * 100 / t.bytesTotal).toInt().coerceIn(0, 99) else 0
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * এই ফোল্ডারে ঢুকতে থাকা ফাইলগুলো (পথ -> তথ্য)। লাইনে/প্রস্তুতিতে/চলছে এমন কাজ, আর সদ্য শেষ হওয়া কাজ (৩০ সেকেন্ড):
     * শেষ হওয়ার পর তালিকা নতুন করে আসা পর্যন্ত ফাইলটি যেন মাঝখানে হারিয়ে না যায়। তালিকায় আসল ফাইল এসে গেলে
     * পর্দায় এটি আপনা-আপনি বাদ পড়ে (পথ মিললে)।
     */
    val uploadGhosts: StateFlow<Map<String, UploadGhost>> =
        combine(
            transfers.transfers,
            _state.map { (it.account?.remoteName to it.account?.backendType) to it.path }.distinctUntilChanged(),
            transfers.stages,
            transfers.dirProgress,
        ) { list, loc, stages, dirProg ->
            val (acc, path) = loc
            val remote = acc.first
            val type = acc.second
            if (remote == null || type == null) {
                emptyMap()
            } else {
                val fs = fsRootFor(remote, type)
                val now = System.currentTimeMillis()
                val out = LinkedHashMap<String, UploadGhost>()
                // পুরোনো থেকে নতুন: একই পথে একাধিক কাজ থাকলে নতুনটি জেতে
                for (t in list.asReversed()) {
                    if (t.isDir) {
                        // ফোল্ডার আপলোড/Sync: ভেতরের সব ফাইল ও ফোল্ডার ধূসর থেকে রঙিন
                        dirGhosts(t, fs, path, stages[t.id], dirProg[t.id], out)
                        continue
                    }
                    if (t.openWhenDone || t.dstFs != fs || parentPath(t.dstPath) != path) continue
                    val active = t.status == TransferStatus.QUEUED || t.status == TransferStatus.PREPARING ||
                        t.status == TransferStatus.RUNNING
                    val justDone = t.status == TransferStatus.DONE && now - (t.finishedAt ?: 0L) < 30_000L
                    if (!active && !justDone) continue
                    val name = t.dstPath.substringAfterLast('/')
                    val fraction = when {
                        justDone -> 1f
                        t.bytesTotal > 0 -> (t.bytesDone.toFloat() / t.bytesTotal.toFloat()).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val localSrc = when {
                        t.sourceUri != null -> null
                        t.srcFs.startsWith("/") -> File(t.srcFs, t.srcPath).absolutePath
                        t.srcFs.endsWith(":/") -> "/" + t.srcPath
                        else -> null
                    }
                    out[t.dstPath] = UploadGhost(
                        entry = RemoteEntry(
                            name = name,
                            path = t.dstPath,
                            isDir = false,
                            size = t.bytesTotal.takeIf { it > 0 },
                            modTimeMillis = t.createdAt,
                            mimeType = guessMime(name),
                        ),
                        status = t.status,
                        fraction = fraction,
                        done = t.bytesDone,
                        total = t.bytesTotal,
                        speed = t.speed,
                        thumbUri = t.sourceUri,
                        thumbPath = localSrc,
                        stage = stages[t.id],
                    )
                }
                out
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val dirListCache = HashMap<String, Pair<Long, List<Triple<String, Boolean, Long>>>>()

    /** লোকাল ফোল্ডারের সরাসরি সন্তানগুলো (নাম, ফোল্ডার কি না, আকার); ৫ সেকেন্ড ক্যাশ */
    private fun localChildren(dir: File): List<Triple<String, Boolean, Long>> {
        val now = System.currentTimeMillis()
        dirListCache[dir.absolutePath]?.let { (at, l) -> if (now - at < 5_000L) return l }
        val l = dir.listFiles()?.filter { !it.name.startsWith(".dm") }?.map { Triple(it.name, it.isDirectory, if (it.isFile) it.length() else 0L) }
            ?: emptyList()
        if (dirListCache.size > 50) dirListCache.clear()
        dirListCache[dir.absolutePath] = now to l
        return l
    }

    /**
     * ফোল্ডার-কাজ (আপলোড/Sync) থাকলে, এখন যে ফোল্ডার দেখা হচ্ছে তার ভেতরের (বা গন্তব্য-ফোল্ডারটাই) সব ফাইল ও ফোল্ডার
     * ঘোস্ট হিসেবে যোগ করে। ফাইল চলতি হলে বাইট অনুযায়ী রঙিন, শেষ হলে পুরো রঙিন, বাকিগুলো ধূসর অপেক্ষায়।
     */
    private fun dirGhosts(
        t: TransferEntity,
        fs: String,
        path: String,
        stage: Int?,
        prog: com.drivemount.app.data.transfer.TransferRepository.DirProgress?,
        out: MutableMap<String, UploadGhost>,
    ) {
        val live = t.status == TransferStatus.QUEUED || t.status == TransferStatus.PREPARING ||
            t.status == TransferStatus.RUNNING || t.status == TransferStatus.PAUSED
        if (!live || t.openWhenDone || t.dstFs != fs) return
        val srcRoot = when {
            t.srcFs.startsWith("/") -> File(t.srcFs, t.srcPath)
            t.srcFs.endsWith(":/") -> File("/" + t.srcPath)
            else -> return
        }
        if (!srcRoot.isDirectory) return
        val dstRoot = t.dstPath.trim('/')
        val cur = path.trim('/')
        fun ghost(name: String, p: String, isDir: Boolean, size: Long, status: String, fraction: Float, done: Long, total: Long, thumb: String?) {
            out[p] = UploadGhost(
                entry = RemoteEntry(
                    name = name, path = p, isDir = isDir, size = size.takeIf { it > 0 },
                    modTimeMillis = t.createdAt, mimeType = if (isDir) "inode/directory" else guessMime(name),
                ),
                status = status, fraction = fraction, done = done, total = total, speed = 0L,
                thumbUri = null, thumbPath = thumb, stage = stage,
            )
        }
        val rel: String = when {
            dstRoot.isEmpty() -> cur
            cur == dstRoot -> ""
            cur.startsWith("$dstRoot/") -> cur.substring(dstRoot.length + 1)
            dstRoot.startsWith(if (cur.isEmpty()) "" else "$cur/") -> {
                // গন্তব্য-ফোল্ডারটাই এই ফোল্ডারের ভেতরে: সেটি ধূসর ফোল্ডার হিসেবে
                val seg = dstRoot.substring(if (cur.isEmpty()) 0 else cur.length + 1).substringBefore('/')
                val p = if (cur.isEmpty()) seg else "$cur/$seg"
                ghost(seg, p, true, 0L, t.status, 0f, 0L, 0L, null)
                return
            }
            else -> return
        }
        val dir = if (rel.isEmpty()) srcRoot else File(srcRoot, rel)
        if (!dir.isDirectory) return
        for ((name, isDir, size) in localChildren(dir)) {
            val p = if (cur.isEmpty()) name else "$cur/$name"
            val key = if (rel.isEmpty()) name else "$rel/$name"
            val act = prog?.active?.get(key)
            when {
                !isDir && prog?.done?.contains(key) == true ->
                    ghost(name, p, false, size, TransferStatus.DONE, 1f, size, size, File(dir, name).absolutePath)
                act != null && act.second > 0 ->
                    ghost(name, p, false, size, TransferStatus.RUNNING, (act.first.toFloat() / act.second).coerceIn(0f, 1f), act.first, act.second, File(dir, name).absolutePath)
                else ->
                    ghost(name, p, isDir, size, if (t.status == TransferStatus.RUNNING) TransferStatus.QUEUED else t.status, 0f, 0L, 0L, if (isDir) null else File(dir, name).absolutePath)
            }
        }
    }

    /**
     * ফাইলে ট্যাপ: অফলাইন কপি থাকলে সঙ্গে সঙ্গে খোলে। নইলে নামায় (সারির ওপরেই শতাংশ দেখায়, ট্রান্সফার-স্ক্রিনে
     * যেতে হয় না) এবং নামা শেষ হলে নিজে থেকে খোলে।
     */
    fun openFile(entry: RemoteEntry) {
        val account = _state.value.account ?: return
        if (entry.path in openProgress.value) return // ইতিমধ্যে নামছে
        // অফলাইনে রাখা কপি থাকলে নামানো ছাড়াই সরাসরি খোলা
        appRef.offlineStore.localFile(account.id, entry.path)?.let {
            viewModelScope.launch { _events.send(BrowserEvent.OpenFile(it.absolutePath, guessMime(entry.name, entry.mimeType))) }
            return
        }
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
        }
    }

    /** বাছাই করা ফাইল/ফোল্ডারের থাম্বনেল, সিক-শিট, কভার, এক্সট্রা রেজোল্যুশন ব্যাকগ্রাউন্ডে বানায় (যা বানানো আছে তা বাদ যায়) */
    fun prepareSelected() {
        val account = _state.value.account ?: return
        val items = selectedEntries()
        if (items.isEmpty()) return
        val app = getApplication<Application>()
        val name = "selprep-${System.nanoTime()}.txt"
        java.io.File(app.cacheDir, name).writeText(items.joinToString("\n") { (if (it.isDir) "D\t" else "F\t") + it.path })
        val req = androidx.work.OneTimeWorkRequestBuilder<com.drivemount.app.work.SelectionPrepWorker>()
            .setInputData(androidx.work.workDataOf(
                com.drivemount.app.work.SelectionPrepWorker.KEY_ACCOUNT to account.id,
                com.drivemount.app.work.SelectionPrepWorker.KEY_LIST to name,
            ))
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(
                if (account.backendType == "local") androidx.work.NetworkType.NOT_REQUIRED else androidx.work.NetworkType.CONNECTED).build())
            .build()
        androidx.work.WorkManager.getInstance(app).enqueue(req)
        viewModelScope.launch { message(app.getString(R.string.selprep_started, items.size)) }
        clearSelection()
    }

    /** নির্বাচিতগুলোর সবই অফলাইনে রাখা থাকলে সরায়, নইলে সবগুলো রাখে (ব্যাকগ্রাউন্ডে নামে) */
    fun toggleOffline() {
        val account = _state.value.account ?: return
        val items = selectedEntries()
        if (items.isEmpty()) return
        val store = appRef.offlineStore
        val snap = store.ui.value
        val allPinned = items.all { "${account.id}|${it.path}" in snap.pinned }
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (allPinned) {
                store.unpin(account.id, items)
                message(app.getString(R.string.offline_removed, items.size))
            } else {
                store.pin(account.id, items.filter { "${account.id}|${it.path}" !in snap.pinned })
                message(app.getString(R.string.offline_saving, items.size))
            }
            clearSelection()
        }
    }

    /** বাছাই করা ফাইলগুলো এই ফোল্ডারে আপলোডের জন্য লাইনে দেয় */
    fun upload(uris: List<Uri>, vault: AccountEntity? = null) {
        val account = vault ?: _state.value.account ?: return
        if (uris.isEmpty()) return
        val path = if (vault != null) "" else _state.value.path
        val resolver = getApplication<Application>().contentResolver
        val dstFs = fsRootFor(account.remoteName, account.backendType)
        viewModelScope.launch {
            val entries0 = if (vault != null) files.list(vault, "").getOrDefault(emptyList()) else _state.value.entries
            val existing = entries0.map { it.name }.toMutableSet()
            val sizes = entries0.filter { !it.isDir }.associate { it.name to it.size }
            var skipped = 0
            val tasks = uris.mapNotNull { uri ->
                val name = displayName(resolver, uri)
                // একই নামের ফাইল (একই আকারের) আগে থেকেই ফোল্ডারে থাকলে আবার আপলোড না করে বাদ
                val localSize = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                }
                if (name in sizes && (sizes[name] == null || localSize == null || sizes[name] == localSize)) { skipped++; return@mapNotNull null }
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
            if (tasks.isNotEmpty()) {
                transfers.enqueue(tasks)
                _events.send(BrowserEvent.Queued(tasks.size))
            }
            if (skipped > 0) _events.send(BrowserEvent.Message("আগে থেকেই আছে, $skipped টি ফাইল বাদ দেওয়া হয়েছে"))
        }
    }

    /** ফোনের পথ থেকে সরাসরি আপলোড (অস্থায়ী কপি ছাড়া); নিজস্ব ফাইল-বাছাই ডায়ালগ থেকে আসে */
    fun uploadLocal(localFiles: List<File>, vault: AccountEntity? = null) {
        val account = vault ?: _state.value.account ?: return
        if (localFiles.isEmpty()) return
        val path = if (vault != null) "" else _state.value.path
        val dstFs = fsRootFor(account.remoteName, account.backendType)
        viewModelScope.launch {
            val entries0 = if (vault != null) files.list(vault, "").getOrDefault(emptyList()) else _state.value.entries
            val existing = entries0.map { it.name }.toMutableSet()
            val sizes = entries0.filter { !it.isDir }.associate { it.name to it.size }
            var skipped = 0
            val tasks = localFiles.mapNotNull { file ->
                if (file.name in sizes && (sizes[file.name] == null || sizes[file.name] == file.length())) { skipped++; return@mapNotNull null }
                val target = uniqueName(file.name, existing)
                existing += target
                NewTransfer(
                    name = file.name,
                    isDir = false,
                    move = false,
                    srcFs = file.parentFile?.absolutePath ?: "/",
                    srcPath = file.name,
                    dstFs = dstFs,
                    dstPath = joinPath(path, target),
                )
            }
            if (tasks.isNotEmpty()) {
                transfers.enqueue(tasks)
                _events.send(BrowserEvent.Queued(tasks.size))
            }
            if (skipped > 0) _events.send(BrowserEvent.Message("আগে থেকেই আছে, $skipped টি ফাইল বাদ দেওয়া হয়েছে"))
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

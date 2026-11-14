package com.drivemount.app.ui.videos

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.meta.META_DIR
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import com.drivemount.app.engine.parentPath
import com.drivemount.app.ui.browser.ThumbKind
import com.drivemount.app.ui.browser.prefetchThumbnail
import com.drivemount.app.ui.browser.thumbKindOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** একটি বেছে নেওয়া ক্লাউড-ফোল্ডার */
data class VideoSource(val account: AccountEntity, val path: String) {
    val key: String get() = "${account.id}|$path"
    val label: String
        get() = if (path.isEmpty()) account.displayName else path.substringAfterLast('/')
}

/** folder = উৎস-ফোল্ডার থেকে ভিডিওর ফোল্ডারের আপেক্ষিক পথ ("" = সরাসরি উৎস-ফোল্ডারে) */
data class VideoItem(val entry: RemoteEntry, val folder: String, val sourceKey: String, val accountId: Long)

enum class VideoSort { NAME, DATE, SIZE }

data class VideosState(
    val ready: Boolean = false,
    val sources: List<VideoSource> = emptyList(),
    val items: Map<String, List<VideoItem>> = emptyMap(),
    /** null = All (সব ফোল্ডার) */
    val selected: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    /** বর্তমান বাছাই অনুযায়ী দেখানোর ভিডিও */
    val visible: List<VideoItem>
        get() = (if (selected == null) items.values.flatten() else items[selected].orEmpty())
            // একই ফাইল দুটি উৎসে (যেমন ওপরের ও ভেতরের ফোল্ডার) থাকলে একবারই দেখানো
            .distinctBy { it.accountId to it.entry.path }
}

/**
 * Videos ট্যাব: বেছে নেওয়া এক বা একাধিক ক্লাউড-ফোল্ডারের (সাব-ফোল্ডার সহ) সব ভিডিও।
 * তালিকা ফোনে সংরক্ষিত থাকে, তাই ট্যাব খুললেই সঙ্গে সঙ্গে দেখা যায়; পেছনে নতুন তালিকা এসে বদলে দেয়।
 */
open class MediaLibraryViewModel(
    app: Application,
    prefsName: String,
    cacheName: String,
    /** কোন ফাইল এই ট্যাবে দেখানো হবে */
    private val accept: (RemoteEntry) -> Boolean,
    /** true হলে (Photos) তালিকার ছবিগুলোর মাঝারি প্রিভিউ ফোনে জমানো হয় */
    private val cachePreviews: Boolean = false,
) : AndroidViewModel(app) {
    private val appRef = app as DriveMountApp
    private val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val cacheFile = File(app.filesDir, cacheName)

    private val _state = MutableStateFlow(VideosState())
    val state: StateFlow<VideosState> = _state

    private val allFiles = HashMap<String, List<RemoteEntry>>()
    private var job: Job? = null
    private var prefetchJob: Job? = null

    /** উৎস-ফোল্ডারে ক্লাউডে থাকা প্রিভিউ-ফাইলের পথ (তালিকা থেকেই জানা যায়, আলাদা রিকোয়েস্ট লাগে না) */
    private val previewPaths = HashMap<String, Set<String>>()

    /** সাবটাইটেল খোঁজার জন্য: ওই উৎস-ফোল্ডারের সব ফাইল (সর্বশেষ তালিকা আসার পর) */
    fun siblings(sourceKey: String): List<RemoteEntry> = synchronized(allFiles) { allFiles[sourceKey].orEmpty() }

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { readSources() }
            val sources = ArrayList<VideoSource>()
            for ((id, path) in saved) {
                val acc = appRef.accountRepository.getById(id) ?: continue // মুছে ফেলা অ্যাকাউন্ট বাদ
                sources += VideoSource(acc, path)
            }
            val cached = withContext(Dispatchers.IO) { readCache() }
            val items = sources.associate { it.key to cached[it.key].orEmpty() }
            _state.value = VideosState(
                ready = true,
                sources = sources,
                items = items,
                loading = sources.isNotEmpty() && items.values.all { it.isEmpty() },
            )
            if (sources.isNotEmpty()) refresh()
        }
    }

    fun select(key: String?) {
        _state.update { it.copy(selected = key) }
    }

    /** এই সেশনে নতুন যোগ হওয়া উৎস: তালিকা আসার পর ব্যাকগ্রাউন্ড নোটিফিকেশন-কাজ চলে */
    private val fresh = HashSet<String>()

    private fun toast(resId: Int, vararg args: Any) {
        android.widget.Toast.makeText(appRef, appRef.getString(resId, *args), android.widget.Toast.LENGTH_LONG).show()
    }

    /** ফোনের পুরো স্টোরেজ (বা তার চেয়ে ওপরের) path: এটি রিকার্সিভ স্ক্যান করলে অ্যাপ ক্র্যাশ করে */
    private fun tooBroad(account: AccountEntity, path: String): Boolean {
        if (account.backendType != "local") return false
        val root = com.drivemount.app.engine.LocalPaths.internalRelative()
        val p = path.trim('/')
        return p.isEmpty() || p == root || root.startsWith("$p/")
    }

    fun addSource(account: AccountEntity, rawPath: String) {
        val path = rawPath.trim('/')
        if (tooBroad(account, path)) { toast(R.string.folder_too_broad); return }
        val src = VideoSource(account, path)
        val st = _state.value
        // একই ফোল্ডার আবার: কিছু যোগ হয় না, আগেরটিই দেখানো হয়
        if (st.sources.any { it.key == src.key }) {
            toast(R.string.folder_already_added)
            select(src.key)
            return
        }
        // ওপরের কোনো ফোল্ডার আগেই যোগ করা (এর ফাইল ইতিমধ্যে আছে)
        val parent = st.sources.firstOrNull {
            it.account.id == account.id && (it.path.isEmpty() || path.startsWith(it.path + "/"))
        }
        if (parent != null) {
            toast(R.string.folder_already_included, parent.label)
            select(parent.key)
            return
        }
        // নতুনটি ওপরের ফোল্ডার হলে ভেতরের পুরোনো উৎসগুলো এতে মিশে যায় (ডুপ্লিকেট এড়াতে)
        val children = st.sources.filter {
            it.account.id == account.id && (path.isEmpty() || it.path.startsWith("$path/"))
        }
        if (children.isNotEmpty()) toast(R.string.folder_merged)
        val childKeys = children.map { it.key }.toSet()
        childKeys.forEach { k -> synchronized(allFiles) { allFiles.remove(k) } }
        _state.update {
            it.copy(
                sources = it.sources.filter { s -> s.key !in childKeys } + src,
                items = it.items.filterKeys { k -> k !in childKeys } + (src.key to emptyList()),
                selected = src.key,
                loading = true,
                error = null,
            )
        }
        saveSources()
        if (account.backendType != "local") {
            fresh.add(src.key)
            // নোটিফিকেশন সঙ্গে সঙ্গে ("Scanning…"), অ্যাপ আড়ালে গেলেও কাজ চলে
            com.drivemount.app.work.LibraryPrepWorker.begin(appRef, src.key, src.label, cacheFile.name, cachePreviews)
        }
        refresh()
    }

    fun removeSource(key: String) {
        _state.update {
            it.copy(
                sources = it.sources.filter { s -> s.key != key },
                items = it.items - key,
                selected = if (it.selected == key) null else it.selected,
            )
        }
        synchronized(allFiles) { allFiles.remove(key) }
        saveSources()
        viewModelScope.launch(Dispatchers.IO) { writeCache(_state.value.items) }
    }

    /** সব উৎস-ফোল্ডারের তালিকা নতুন করে আনে */
    fun refresh() {
        val sources = _state.value.sources
        if (sources.isEmpty()) return
        job?.cancel()
        _state.update { it.copy(refreshing = true, error = null) }
        job = viewModelScope.launch {
            var firstError: String? = null
            for (src in sources) {
                try {
                    val acc = src.account
                    val files = appRef.rcloneApi.listFilesRecursive(fsRootFor(acc.remoteName, acc.backendType), src.path)
                    // সাইডকারের লুকানো ফাইল (.dmmeta/...) ছবি/ভিডিওর তালিকায় আসবে না
                    val visibleFiles = files.filter { !isMeta(it.path) }
                    val prevSet = if (cachePreviews) files.filter { isMeta(it.path) }.map { it.path }.toHashSet() else emptySet()
                    synchronized(previewPaths) { previewPaths[src.key] = prevSet }
                    appRef.photoPreviews.remoteSets[src.key] = prevSet
                    val videos = visibleFiles
                        .filter { accept(it) }
                        .let { if (it.size > MAX_ITEMS) it.take(MAX_ITEMS) else it }
                        .map { VideoItem(it, relFolder(src.path, it.path), src.key, acc.id) }
                    synchronized(allFiles) { allFiles[src.key] = visibleFiles }
                    // ক্লাউডে থাকা সাইডকার (থাম্বনেল/ট্যাগ/কভার) পেছনে আমদানি: এই ফোনে নতুন হলেও আইটেম সঙ্গে সঙ্গে সাজানো দেখায়
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { appRef.sidecar.importFromListing(acc.remoteName, fsRootFor(acc.remoteName, acc.backendType), files) }
                    }
                    _state.update { st ->
                        // এর মধ্যে উৎসটি মুছে ফেলা হলে ফেরানো হয় না
                        if (st.sources.none { it.key == src.key }) st else st.copy(items = st.items + (src.key to videos))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (firstError == null) firstError = e.message ?: e.toString()
                }
            }
            withContext(Dispatchers.IO) { writeCache(_state.value.items) }
            // নতুন যোগ করা উৎসের ভারী কাজ (থাম্বনেল/প্রিভিউ) ব্যাকগ্রাউন্ড ওয়ার্কারে; তাকে জানানো
            val handedOver = fresh.toSet()
            for (k in handedOver) com.drivemount.app.work.LibraryPrepWorker.listReady(k, firstError == null)
            fresh.removeAll(handedOver)
            startPrefetch(skip = handedOver)
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    // কিছু দেখানো থাকলে ব্যর্থতা চুপচাপ; না থাকলে কারণ দেখানো হয়
                    error = if (it.visible.isEmpty()) firstError else null,
                )
            }
        }
    }

    /** তালিকা আসার পর ব্যাকগ্রাউন্ডে সব থাম্বনেল বানিয়ে রাখা, যাতে স্ক্রলের সময় সঙ্গে সঙ্গে দেখা যায় */
    private fun startPrefetch(skip: Set<String> = emptySet()) {
        prefetchJob?.cancel()
        val accounts = _state.value.sources.associate { it.account.id to it.account }
        val all = _state.value.items.values.flatten().filter { it.sourceKey !in skip }
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            for (chunk in all.chunked(3)) {
                kotlinx.coroutines.coroutineScope {
                    chunk.map { v ->
                        launch {
                            val acc = accounts[v.accountId] ?: return@launch
                            runCatching { prefetchThumbnail(appRef, acc, v.entry) }
                            if (cachePreviews && offlineAllowed()) {
                                val set = synchronized(previewPaths) { previewPaths[v.sourceKey] }.orEmpty()
                                runCatching {
                                    appRef.photoPreviews.ensureCached(
                                        acc.remoteName, fsRootFor(acc.remoteName, acc.backendType),
                                        v.entry.path, v.entry.size ?: -1L, set,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        // ViewModel চলে গেলে অপেক্ষায় থাকা ব্যাকগ্রাউন্ড কাজকে আটকে না রাখা
        fresh.forEach { com.drivemount.app.work.LibraryPrepWorker.listReady(it, false) }
        super.onCleared()
    }

    private fun isMeta(path: String) = path.startsWith("$META_DIR/") || path.contains("/$META_DIR/")

    /** ফোনে জমানো চালু আছে এবং ডেটা মিটারড (মোবাইল ডেটা) নয় */
    private fun offlineAllowed(): Boolean {
        if (!appRef.photoPreviews.offlineEnabled) return false
        val cm = appRef.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return !cm.isActiveNetworkMetered
    }

    private fun relFolder(source: String, path: String): String {
        val rel = if (source.isEmpty()) path else path.removePrefix("$source/")
        return parentPath(rel)
    }

    // ---- সংরক্ষণ ----

    private fun saveSources() {
        val arr = JSONArray()
        for (s in _state.value.sources) arr.put(JSONObject().put("accountId", s.account.id).put("path", s.path))
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    private fun readSources(): List<Pair<Long, String>> {
        val raw = prefs.getString("sources", null)
        if (raw != null) {
            return runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    o.getLong("accountId") to o.optString("path")
                }
            }.getOrDefault(emptyList())
        }
        // আগের সংস্করণের একক ফোল্ডার
        val id = prefs.getLong("accountId", -1L)
        return if (id >= 0) listOf(id to prefs.getString("path", "").orEmpty()) else emptyList()
    }

    private fun readCache(): Map<String, List<VideoItem>> = runCatching {
        if (!cacheFile.exists()) return emptyMap()
        val root = JSONObject(cacheFile.readText())
        val out = HashMap<String, List<VideoItem>>()
        val srcs = root.getJSONArray("sources")
        for (k in 0 until srcs.length()) {
            val so = srcs.getJSONObject(k)
            val key = so.getString("key")
            val accountId = so.getLong("accountId")
            val arr = so.getJSONArray("items")
            out[key] = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                VideoItem(
                    entry = RemoteEntry(
                        name = o.getString("name"),
                        path = o.getString("path"),
                        isDir = false,
                        size = o.optLong("size", -1L).takeIf { it >= 0 },
                        modTimeMillis = if (o.has("mod")) o.getLong("mod") else null,
                        mimeType = o.optString("mime"),
                    ),
                    folder = o.optString("folder"),
                    sourceKey = key,
                    accountId = accountId,
                )
            }
        }
        out
    }.getOrDefault(emptyMap())

    private fun writeCache(items: Map<String, List<VideoItem>>) {
        runCatching {
            val srcs = JSONArray()
            for ((key, list) in items) {
                val arr = JSONArray()
                for (v in list) {
                    arr.put(
                        JSONObject()
                            .put("name", v.entry.name)
                            .put("path", v.entry.path)
                            .put("size", v.entry.size ?: -1L)
                            .put("mod", v.entry.modTimeMillis)
                            .put("mime", v.entry.mimeType)
                            .put("folder", v.folder),
                    )
                }
                srcs.put(
                    JSONObject()
                        .put("key", key)
                        .put("accountId", list.firstOrNull()?.accountId ?: key.substringBefore('|').toLongOrNull() ?: -1L)
                        .put("items", arr),
                )
            }
            cacheFile.writeText(JSONObject().put("sources", srcs).toString())
        }
    }
}

private const val MAX_ITEMS = 20_000

/** Videos ট্যাব */
class VideosViewModel(app: Application) :
    MediaLibraryViewModel(app, "videos_source", "videos_list.json", { thumbKindOf(it) == ThumbKind.VIDEO })

/** Music ট্যাব */
class MusicViewModel(app: Application) :
    MediaLibraryViewModel(app, "music_source", "music_list.json", { guessMime(it.name, it.mimeType).startsWith("audio/") })

/** Photos ট্যাব */
class PhotosViewModel(app: Application) :
    MediaLibraryViewModel(app, "photos_source", "photos_list.json", { thumbKindOf(it) == ThumbKind.IMAGE }, cachePreviews = true)

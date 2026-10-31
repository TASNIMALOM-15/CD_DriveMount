package com.drivemount.app.ui.videos

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import com.drivemount.app.engine.parentPath
import com.drivemount.app.ui.browser.ThumbKind
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
        get() = if (selected == null) items.values.flatten() else items[selected].orEmpty()
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
) : AndroidViewModel(app) {
    private val appRef = app as DriveMountApp
    private val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val cacheFile = File(app.filesDir, cacheName)

    private val _state = MutableStateFlow(VideosState())
    val state: StateFlow<VideosState> = _state

    private val allFiles = HashMap<String, List<RemoteEntry>>()
    private var job: Job? = null

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

    fun addSource(account: AccountEntity, path: String) {
        val src = VideoSource(account, path)
        val st = _state.value
        if (st.sources.any { it.key == src.key }) {
            select(src.key)
            return
        }
        _state.update {
            it.copy(
                sources = it.sources + src,
                items = it.items + (src.key to emptyList()),
                selected = src.key,
                loading = true,
                error = null,
            )
        }
        saveSources()
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
                    val videos = files
                        .filter { accept(it) }
                        .map { VideoItem(it, relFolder(src.path, it.path), src.key, acc.id) }
                    synchronized(allFiles) { allFiles[src.key] = files }
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

/** Videos ট্যাব */
class VideosViewModel(app: Application) :
    MediaLibraryViewModel(app, "videos_source", "videos_list.json", { thumbKindOf(it) == ThumbKind.VIDEO })

/** Music ট্যাব */
class MusicViewModel(app: Application) :
    MediaLibraryViewModel(app, "music_source", "music_list.json", { guessMime(it.name, it.mimeType).startsWith("audio/") })

/** Photos ট্যাব */
class PhotosViewModel(app: Application) :
    MediaLibraryViewModel(app, "photos_source", "photos_list.json", { thumbKindOf(it) == ThumbKind.IMAGE })

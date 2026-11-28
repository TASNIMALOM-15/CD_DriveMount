package com.drivemount.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.meta.META_DIR
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.parentPath
import com.drivemount.app.ui.videos.LIBRARY_SPECS
import com.drivemount.app.ui.videos.LibrarySpec
import com.drivemount.app.ui.videos.VideoItem
import com.drivemount.app.ui.videos.readLibraryCache
import com.drivemount.app.ui.videos.writeLibraryCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * ব্যাকগ্রাউন্ড মেটাডেটা সিঙ্ক (পর্যায়ক্রমে, নেট থাকলে): অন্য ডিভাইস বা ক্লাউড প্রোভাইডার থেকে ভিডিও/ছবি/গান
 * যোগ, নাম বদল, সরানো বা মোছা হলে —
 *  - লাইব্রেরি-তালিকা (ট্যাবের স্থায়ী ক্যাশ) নতুন তালিকায় বদলানো
 *  - মুছে/বদলে যাওয়াগুলোর লোকাল থাম্বনেল, প্রিভিউ, সিকবার, মেটাডেটা সরানো
 *  - নতুন/নতুন-নামেরগুলোর থাম্বনেল ও ভিডিও-মেটাডেটা ক্লাউডের সাইডকার থেকে আনা (ক্লাউডে কিছু লেখা হয় না)
 *  - পরিবর্তিত ফোল্ডারের ব্রাউজ-ইনডেক্স (যেগুলো আগে খোলা হয়েছে) নতুন করা
 * নাম বদল/সরানো = আগের পথ মোছা + নতুন পথ যোগ; নতুন পথের তথ্য ক্লাউডের সাইডকার (অন্য ডিভাইসের বানানো) থেকে আসে।
 */
class LibrarySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp

    override suspend fun doWork(): Result {
        var changed = false
        for (spec in LIBRARY_SPECS) {
            coroutineContext.ensureActive()
            changed = runCatching { syncLibrary(spec) }.getOrElse { if (it is CancellationException) throw it else false } || changed
        }
        if (changed) app.sidecar.notifyChanged()
        return Result.success()
    }

    private fun sources(spec: LibrarySpec): List<Pair<Long, String>> {
        val prefs = applicationContext.getSharedPreferences(spec.prefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString("sources", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getJSONObject(i).let { it.getLong("accountId") to it.optString("path") } }
        }.getOrDefault(emptyList())
    }

    private fun isMeta(path: String) = path.startsWith("$META_DIR/") || path.contains("/$META_DIR/")

    private fun unmetered(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return !cm.isActiveNetworkMetered
    }

    private suspend fun syncLibrary(spec: LibrarySpec): Boolean {
        val cacheFile = File(applicationContext.filesDir, spec.cacheName)
        val cached = readLibraryCache(cacheFile).toMutableMap()
        var changed = false
        for ((accountId, path) in sources(spec)) {
            coroutineContext.ensureActive()
            val acc = app.accountRepository.getById(accountId) ?: continue
            if (acc.backendType == "local") continue
            val key = "$accountId|$path"
            val fs = fsRootFor(acc.remoteName, acc.backendType)
            val files = runCatching { app.rcloneApi.listFilesRecursive(fs, path) }.getOrNull() ?: continue
            val fresh = files.filter { !isMeta(it.path) && spec.accept(it) }
            val old = cached[key]
            // নতুন তালিকা খালি অথচ আগে ছিল = সম্ভবত সংযোগ/আনলক-সমস্যা; ভুল করে সব মুছে ফেলা নয়
            if (old != null && old.isNotEmpty() && (fresh.isEmpty() || (old.size > 20 && fresh.size < old.size / 5))) continue

            val sig = { e: RemoteEntry -> "${e.path}|${e.size}" }
            val oldSigs = old?.associateBy { sig(it.entry) }.orEmpty()
            val freshSigs = fresh.associateBy { sig(it) }
            val removed = oldSigs.filterKeys { it !in freshSigs }.values.map { it.entry }
            val added = freshSigs.filterKeys { it !in oldSigs }.values.toList()
            if (old != null && removed.isEmpty() && added.isEmpty()) continue // কিছু বদলায়নি

            // ১. মুছে/সরে/বদলে যাওয়াগুলোর লোকাল তথ্য সাফ
            for (e in removed) app.sidecar.purgeLocal(acc.remoteName, e.path, e.size ?: -1L)

            // ২. নতুন/নতুন-নামেরগুলোর তথ্য ক্লাউডের সাইডকার থেকে (প্যাক ফোল্ডার-প্রতি একবার)
            if (old != null) {
                added.groupBy { parentPath(it.path) }.entries.take(MAX_FOLDERS).forEach { (dir, list) ->
                    coroutineContext.ensureActive()
                    runCatching { app.sidecar.importNow(acc.remoteName, fs, dir, list) }
                }
                val prevSet = if (spec.cachePreviews) files.filter { isMeta(it.path) }.map { it.path }.toHashSet() else emptySet()
                val canPreview = spec.cachePreviews && app.photoPreviews.offlineEnabled && unmetered()
                for (e in added.take(MAX_ITEMS_PER_RUN)) {
                    coroutineContext.ensureActive()
                    val size = e.size ?: continue
                    if (spec.isVideo) runCatching { app.sidecar.syncVideoInfo(acc.remoteName, fs, e.path, size) }
                    if (canPreview) runCatching { app.photoPreviews.ensureCached(acc.remoteName, fs, e.path, size, prevSet) }
                }
            }

            // ৩. লাইব্রেরি-তালিকা (ট্যাবের ক্যাশ) হালনাগাদ
            val srcPrefix = if (path.isEmpty()) "" else "$path/"
            cached[key] = fresh.map { e ->
                val rel = if (srcPrefix.isEmpty()) e.path else e.path.removePrefix(srcPrefix)
                VideoItem(e, parentPath(rel), key, accountId)
            }
            changed = true

            // ৪. যে ফোল্ডারগুলো আগে খোলা হয়েছিল, তাদের ব্রাউজ-ইনডেক্স নতুন করা
            (removed + added).map { parentPath(it.path) }.toSet().take(MAX_FOLDERS).forEach { dir ->
                coroutineContext.ensureActive()
                if (app.fileRepository.syncedAt(acc, dir) != null) runCatching { app.fileRepository.list(acc, dir) }
            }
        }
        if (changed) writeLibraryCache(cacheFile, cached)
        return changed
    }

    companion object {
        const val UNIQUE_NAME = "dm-library-sync"
        private const val MAX_FOLDERS = 40
        private const val MAX_ITEMS_PER_RUN = 300
    }
}

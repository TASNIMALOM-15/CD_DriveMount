package com.drivemount.app.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.work.OfflineWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * "অফলাইনের জন্য রাখো": ফাইল/ফোল্ডার ফোনে পুরো নামিয়ে রাখা।
 *  - ফাইল অ্যাপের ব্যক্তিগত ফোল্ডারে (`filesDir/offline`) থাকে: অন্য অ্যাপ পড়তে পারে না, এবং ফোনের নিজস্ব স্টোরেজ-এনক্রিপশনে সুরক্ষিত।
 *    (ভল্টের ফাইল ডিক্রিপ্ট হয়ে নামে; অতিরিক্ত অ্যাপ-স্তরের এনক্রিপশন নেই।)
 *  - মেমরিতে সূচি রাখা হয়, তাই প্লেয়ার/ছবি-ভিউয়ার/ব্রাউজ "আছে কি" জানতে ডাটাবেস ছোঁয় না (UI থ্রেডে নিরাপদ)।
 *  - ডাউনলোড ব্যাকগ্রাউন্ডে (OfflineWorker); সীমা ও Wi-Fi-শর্ত ব্যবহারকারী ঠিক করে।
 */
class OfflineStore(private val context: Context, private val dao: OfflineDao) {

    /** ব্রাউজে চিহ্ন দেখানোর জন্য: পিন করা ও সম্পূর্ণ নামানো ফাইলের কী ("accountId|path") */
    data class Snapshot(val pinned: Set<String> = emptySet(), val done: Set<String> = emptySet(), val usedBytes: Long = 0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(context.filesDir, "offline").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("offline_ui", Context.MODE_PRIVATE)

    private val doneFiles = ConcurrentHashMap<String, OfflineFile>()
    private val pinList = ConcurrentHashMap<String, OfflinePin>()
    private val _ui = MutableStateFlow(Snapshot())
    val ui: StateFlow<Snapshot> = _ui

    var limitBytes: Long
        get() = prefs.getLong("limit", 5L * 1024 * 1024 * 1024)
        set(v) { prefs.edit().putLong("limit", v).apply() }

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(v) { prefs.edit().putBoolean("wifi_only", v).apply() }

    /** শেষ ব্যর্থতার কারণ (Settings-এ দেখানো হয়, যাতে "কিছু হচ্ছে না" ঠেকলে কারণ জানা যায়) */
    var lastError: String
        get() = prefs.getString("last_error", "") ?: ""
        set(v) { prefs.edit().putString("last_error", v).apply() }

    init {
        scope.launch {
            runCatching {
                dao.files().forEach { doneFiles[key(it.accountId, it.path)] = it }
                dao.pins().forEach { pinList[key(it.accountId, it.path)] = it }
                publish()
            }
        }
    }

    private fun key(accountId: Long, path: String) = "$accountId|$path"

    private fun publish() {
        _ui.value = Snapshot(
            pinned = pinList.keys.toSet(),
            done = doneFiles.keys.toSet(),
            usedBytes = doneFiles.values.sumOf { it.size },
        )
    }

    fun usedBytes(): Long = doneFiles.values.sumOf { it.size }

    /** ফাইলটির অফলাইন কপি থাকলে সেটি (ফাইল মুছে গেলে/অন্য কিছু হলে null) */
    fun localFile(accountId: Long, path: String): File? {
        val rec = doneFiles[key(accountId, path)] ?: return null
        val f = File(root, rec.local)
        return if (f.isFile) f else null
    }

    /** সূচিতে আছে এবং আকার/সময় বদলায়নি, অর্থাৎ আবার নামানো লাগবে না */
    fun isCurrent(accountId: Long, path: String, size: Long?, modTime: Long?): Boolean {
        val rec = doneFiles[key(accountId, path)] ?: return false
        if (!File(root, rec.local).isFile) return false
        if (size != null && size >= 0 && rec.size != size) return false
        if (modTime != null && rec.modTime != null && rec.modTime != modTime) return false
        return true
    }

    /** ব্রাউজের চিহ্ন: 2 = ফোনে আছে, 1 = পিন করা (নামছে/অপেক্ষায়), 0 = কিছু নয় */
    fun stateOf(snapshot: Snapshot, accountId: Long, path: String, isDir: Boolean): Int {
        val k = key(accountId, path)
        return when {
            !isDir && k in snapshot.done -> 2
            k in snapshot.pinned -> if (!isDir) 1 else 2
            else -> 0
        }
    }

    suspend fun pins(): List<OfflinePin> = dao.pins()

    suspend fun pin(accountId: Long, entries: List<RemoteEntry>) {
        for (e in entries) {
            val p = OfflinePin(accountId, e.path, e.isDir, e.name, e.size, e.modTimeMillis)
            dao.upsertPin(p)
            pinList[key(accountId, e.path)] = p
        }
        publish()
        enqueue()
    }

    /** পিন সরায় এবং (ফোল্ডার হলে ভেতরের সব) ফোনের কপি মুছে জায়গা ফিরিয়ে দেয় */
    suspend fun unpin(accountId: Long, entries: List<RemoteEntry>) {
        for (e in entries) {
            dao.deletePin(accountId, e.path)
            pinList.remove(key(accountId, e.path))
            val victims = doneFiles.values.filter {
                it.accountId == accountId && (it.path == e.path || (e.isDir && it.path.startsWith(e.path.trimEnd('/') + "/")))
            }
            // অন্য পিনের (আলাদা ফাইল/ফোল্ডার) আওতায় থাকা ফাইল মুছব না
            val remainingPins = pinList.values.filter { it.accountId == accountId }
            for (v in victims) {
                val covered = remainingPins.any { it.path == v.path || (it.isDir && v.path.startsWith(it.path.trimEnd('/') + "/")) }
                if (!covered) removeFile(v)
            }
        }
        publish()
    }

    suspend fun removeFile(rec: OfflineFile) {
        dao.deleteFile(rec.accountId, rec.path)
        doneFiles.remove(key(rec.accountId, rec.path))
        runCatching { File(root, rec.local).parentFile?.deleteRecursively() }
    }

    fun filesOf(accountId: Long): List<OfflineFile> = doneFiles.values.filter { it.accountId == accountId }

    /** ডাউনলোড হওয়া অস্থায়ী ফাইলটি স্থায়ী জায়গায় সরিয়ে সূচিতে তোলে */
    suspend fun record(accountId: Long, path: String, name: String, modTime: Long?, downloaded: File): Boolean {
        val hash = MessageDigest.getInstance("SHA-1").digest("$accountId|$path".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        val dir = File(root, hash).apply { deleteRecursively(); mkdirs() }
        val dst = File(dir, name)
        val moved = downloaded.renameTo(dst) || runCatching { downloaded.copyTo(dst, overwrite = true); downloaded.delete(); true }.getOrDefault(false)
        if (!moved) return false
        val rec = OfflineFile(accountId, path, dst.length(), modTime, "$hash/$name")
        dao.upsertFile(rec)
        doneFiles[key(accountId, path)] = rec
        publish()
        return true
    }

    /** সব অফলাইন ফাইল ও পিন মুছে দেয় */
    suspend fun clearAll() {
        dao.deleteAllFiles()
        dao.deleteAllPins()
        doneFiles.clear()
        pinList.clear()
        runCatching { root.listFiles()?.forEach { it.deleteRecursively() } }
        publish()
        WorkManager.getInstance(context).cancelUniqueWork(OfflineWorker.UNIQUE_NAME)
    }

    /** ডাউনলোড-কাজ লাইনে দেয় (চলমান থাকলে তার পরে); আবার চালালে আগেরগুলো বাদ দিয়ে বাকিগুলো নামে */
    fun enqueue() {
        // expedited: সিস্টেমের দেরি ছাড়া সঙ্গে সঙ্গে শুরু (কোটা শেষ হলে সাধারণ কাজ হিসেবে চলে)
        val request = OneTimeWorkRequestBuilder<OfflineWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(OfflineWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

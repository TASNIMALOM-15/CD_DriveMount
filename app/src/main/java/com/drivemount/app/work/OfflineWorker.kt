package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.offline.OfflinePin
import com.drivemount.app.engine.TransferTuning
import com.drivemount.app.engine.fsRootFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * "অফলাইনে রাখো" দেওয়া ফাইল/ফোল্ডার ফোনে নামায়। আগে নামানো (আকার/সময় একই) ফাইল বাদ দেয়, তাই থেমে গেলে বা আবার চালালে
 * যেখানে ছিল সেখান থেকে এগোয়। ফোল্ডার পিন হলে মেলানোর পর ক্লাউডে মুছে যাওয়া ফাইল ফোন থেকেও সরে যায়
 * (শুধু তালিকা সম্পূর্ণ আনা গেলে; নেট না থাকলে কখনো কিছু মোছে না)।
 *
 * গতি: ছোট ফাইল একসঙ্গে ৮টি করে (ক্লাউডের প্রতি-ফাইল অপেক্ষা, বিশেষত ভল্টে, ঢেকে দিতে) নামে (একটার পর একটা নয়), আর দুটি বড় ফাইলও একই সময়ে চলে (সেটি নিজে
 * মাল্টি-থ্রেডে)। তালিকা আনা ও নামানো একই সঙ্গে চলে, তাই প্রথম ফাইল সঙ্গে সঙ্গে নামতে শুরু করে।
 */
class OfflineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp

    private val done = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    @Volatile private var overLimit = false

    /** চলমান ফাইলগুলোর বাইট: group -> [নামানো, মোট] (নোটিফিকেশনের মোট শতাংশের জন্য) */
    private val live = ConcurrentHashMap<String, LongArray>()
    private val liveName = ConcurrentHashMap<String, String>()
    private val reserved = AtomicLong(0)
    private val lastNotify = AtomicLong(0)

    /** নোটিফিকেশনে ট্যাপ করলে যে ফাইলে যাবে (সর্বশেষ শুরু হওয়া ফাইল) */
    @Volatile private var curAccount = -1L
    @Volatile private var curParent = ""
    @Volatile private var curPath = ""

    private val smallGate = Semaphore(SMALL_PARALLEL)
    private val bigGate = Semaphore(2)

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground()

    /** নোটিফিকেশন: কোন ফাইল, মোট কত শতাংশ, কয়টি হলো; ট্যাপে ফাইলে যাওয়া, Cancel বাটন */
    private fun foreground(): ForegroundInfo {
        var bytes = 0L
        var total = 0L
        for (v in live.values) { bytes += v[0]; total += v[1] }
        val names = liveName.values.toList()
        val doneNow = done.get()
        val line = when {
            names.isEmpty() && doneNow > 0 -> applicationContext.getString(R.string.offline_notif_text, doneNow)
            names.isEmpty() -> applicationContext.getString(R.string.offline_notif_preparing)
            else -> {
                val pct = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
                val label = if (names.size > 1) names.first() + " +" + (names.size - 1) else names.first()
                applicationContext.getString(R.string.offline_notif_file, label, pct, doneNow)
            }
        }
        val open = if (curAccount >= 0) {
            TransferNotifications.openPathIntent(applicationContext, curAccount, curParent, curPath)
        } else null
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = TransferNotifications.ongoing(
            applicationContext, applicationContext.getString(R.string.offline_notif_title), line, bytes, total, open, cancel,
        )
        val nid = TransferNotifications.ONGOING_ID + 2
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(nid, n)
    }

    private suspend fun notifyNow(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastNotify.get()
        if (!force && now - last < 1000) return
        if (!lastNotify.compareAndSet(last, now)) return
        runCatching { setForeground(foreground()) }
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground()) }
        val store = app.offlineStore
        store.lastError = ""
        val pins = store.pins()
        coroutineScope {
            for (pin in pins) {
                launch {
                    val acc = app.database.accountDao().getById(pin.accountId) ?: return@launch
                    runCatching { if (pin.isDir) syncDir(acc, pin) else syncFile(acc, pin) }
                        .onFailure {
                            if (it is CancellationException) throw it
                            failed.incrementAndGet()
                            store.lastError = (it.message ?: it.toString()).take(300)
                        }
                }
            }
        }
        return when {
            failed.get() > 0 && !overLimit && runAttemptCount < 3 -> Result.retry()
            else -> Result.success(workDataOf(KEY_DONE to done.get(), KEY_FAILED to failed.get(), KEY_LIMIT to overLimit))
        }
    }

    private suspend fun syncFile(acc: AccountEntity, pin: OfflinePin) {
        // মূল ফোল্ডারের তালিকা থেকে ফাইলের সর্বশেষ আকার/সময় (পাওয়া না গেলে পিনের সময়ের তথ্য)
        val parent = pin.path.substringBeforeLast('/', "")
        val listed = app.fileRepository.list(acc, parent).getOrNull()?.firstOrNull { it.path == pin.path && !it.isDir }
        ensure(acc, pin.path, pin.name, listed?.size ?: pin.size, listed?.modTimeMillis ?: pin.modTime)
    }

    private suspend fun syncDir(acc: AccountEntity, pin: OfflinePin): Unit = coroutineScope {
        val seen = ConcurrentHashMap.newKeySet<String>()
        val allOk = java.util.concurrent.atomic.AtomicBoolean(true)
        val queue = ArrayDeque<String>().apply { add(pin.path) }
        var folders = 0
        val downloads = ArrayList<kotlinx.coroutines.Deferred<Unit>>()
        while (queue.isNotEmpty() && folders < MAX_FOLDERS && !overLimit) {
            coroutineContext.ensureActive()
            val dir = queue.removeFirst()
            folders++
            val list = app.fileRepository.list(acc, dir).getOrNull()
            if (list == null) { allOk.set(false); continue }
            for (e in list) {
                if (e.isDir) { queue.add(e.path); continue }
                seen.add(e.path)
                // নামানো সঙ্গে সঙ্গে শুরু; তালিকা আনা চলতে থাকে
                downloads += async<Unit> {
                    if (!ensure(acc, e.path, e.name, e.size, e.modTimeMillis) && !overLimit) allOk.set(false)
                    Unit
                }
            }
        }
        for (d in downloads) d.await()
        if (queue.isNotEmpty()) allOk.set(false)
        if (allOk.get() && !overLimit) prune(acc, pin, seen)
    }

    /** ক্লাউডে এখন নেই এমন ফাইল ফোন থেকে সরায় (অন্য পিন করা আলাদা ফাইল বাদ) */
    private suspend fun prune(acc: AccountEntity, pin: OfflinePin, seen: Set<String>) {
        val prefix = pin.path.trimEnd('/') + "/"
        val filePins = app.offlineStore.pins().filter { it.accountId == acc.id && !it.isDir }.map { it.path }.toSet()
        for (rec in app.offlineStore.filesOf(acc.id)) {
            if ((pin.path.isEmpty() || rec.path.startsWith(prefix)) && rec.path !in seen && rec.path !in filePins) {
                app.offlineStore.removeFile(rec)
            }
        }
    }

    /** ফাইলটি ফোনে আছে/নামিয়েছে হলে true */
    private suspend fun ensure(acc: AccountEntity, path: String, name: String, size: Long?, modTime: Long?): Boolean {
        val store = app.offlineStore
        if (store.isCurrent(acc.id, path, size, modTime)) return true
        val gate = if (size != null && size >= BIG_BYTES) bigGate else smallGate
        return gate.withPermit { download(acc, path, name, size, modTime) }
    }

    private suspend fun download(acc: AccountEntity, path: String, name: String, size: Long?, modTime: Long?): Boolean {
        val store = app.offlineStore
        if (overLimit) return false
        val need = size ?: 0L
        if (size != null && store.usedBytes() + reserved.get() + size > store.limitBytes) { overLimit = true; return false }
        reserved.addAndGet(need)
        val tmp = File(applicationContext.cacheDir, "offline_dl/${System.nanoTime()}").apply { mkdirs() }
        val api = app.rcloneApi
        val group = "offline-${System.nanoTime()}"
        var job: Long? = null
        curAccount = acc.id
        curParent = path.substringBeforeLast('/', "")
        curPath = path
        live[group] = longArrayOf(0L, size ?: 0L)
        liveName[group] = name
        notifyNow(force = true)
        return try {
            // অ্যাসিঙ্ক কাজ + পোলিং: বড় ফাইলেও নোটিফিকেশনে আসল অগ্রগতি দেখায়, আর বাতিল করা যায়
            val tuning = if (TransferTuning.isFast(applicationContext)) TransferTuning.config(acc.backendType) else null
            job = api.startFileJob(fsRootFor(acc.remoteName, acc.backendType), path, tmp.absolutePath, name, false, group, tuning)
            var polls = 0
            while (true) {
                // শুরুতে ঘন ঘন (ছোট ফাইল সঙ্গে সঙ্গে শেষ হয়), পরে ধীরে
                delay(if (polls++ < 8) 120L else 350L)
                val st = api.jobStatus(job)
                if (!st.finished) {
                    val stats = api.groupStats(group)
                    live[group] = longArrayOf(stats.bytes, if (stats.totalBytes > 0) stats.totalBytes else (size ?: 0L))
                    notifyNow()
                    continue
                }
                if (!st.success) {
                    store.lastError = (name + ": " + st.error).take(300)
                    failed.incrementAndGet()
                    return false
                }
                break
            }
            val f = File(tmp, name)
            if (!f.isFile) { failed.incrementAndGet(); return false }
            if (store.usedBytes() + f.length() > store.limitBytes) { overLimit = true; return false }
            val ok = store.record(acc.id, path, name, modTime, f)
            if (ok) {
                done.incrementAndGet()
                setProgress(workDataOf(KEY_DONE to done.get()))
            } else failed.incrementAndGet()
            ok
        } catch (e: CancellationException) {
            withContext(NonCancellable) { job?.let { api.stopJob(it) } }
            throw e
        } catch (e: Exception) {
            store.lastError = (name + ": " + (e.message ?: e.toString())).take(300)
            failed.incrementAndGet()
            false
        } finally {
            reserved.addAndGet(-need)
            live.remove(group)
            liveName.remove(group)
            withContext(NonCancellable) { api.deleteStats(group) }
            tmp.deleteRecursively()
            runCatching { notifyNow() }
        }
    }

    companion object {
        const val UNIQUE_NAME = "dm-offline"
        const val KEY_DONE = "done"
        const val KEY_FAILED = "failed"
        const val KEY_LIMIT = "limit"
        private const val MAX_FOLDERS = 3000
        private const val SMALL_PARALLEL = 8
        private const val BIG_BYTES = 128L * 1024 * 1024
    }
}

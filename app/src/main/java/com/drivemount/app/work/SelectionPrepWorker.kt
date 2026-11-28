package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.meta.AudioMetaReader
import com.drivemount.app.data.meta.META_DIR
import com.drivemount.app.data.meta.ThumbMetaReader
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * বাছাই করা ফাইল/ফোল্ডারের (লোকাল, ক্লাউড, এনক্রিপ্টেড ভল্ট) থাম্বনেল, সিক-শিট, গানের কভার, ছবির এক্সট্রা রেজোল্যুশন বানায়।
 * যেগুলো আগে থেকেই বানানো আছে সেগুলো নিজে থেকে বাদ যায়। ব্যাকগ্রাউন্ডে ফোরগ্রাউন্ড-নোটিফিকেশনসহ চলে।
 * স্ট্রিমে না হলে (এবং একান্ত দরকারে) পুরো ফাইল নামিয়ে বানায়।
 * ইনপুট: KEY_ACCOUNT ও KEY_LIST (cacheDir-এ লাইন-ভিত্তিক ফাইলের নাম: "F\tপথ" বা "D\tপথ")।
 */
class SelectionPrepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp
    private var done = 0
    private var total = 0
    private var phaseScan = true
    private var failed = 0
    private var etaSec = -1L

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground()

    private fun foreground(): ForegroundInfo {
        val ctx = applicationContext
        val text = when {
            phaseScan -> ctx.getString(R.string.backfill_phase_scan)
            etaSec >= 0 -> ctx.getString(R.string.backfill_notif_text_eta, done, total, BackfillWorker.etaText(ctx, etaSec))
            else -> ctx.getString(R.string.backfill_notif_text, done)
        }
        val cancel = WorkManager.getInstance(ctx).createCancelPendingIntent(id)
        val n = TransferNotifications.ongoing(
            ctx, ctx.getString(R.string.selprep_notif_title), text,
            if (phaseScan) 0L else done.toLong(), if (phaseScan) 0L else total.toLong(), null, cancel,
        )
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(TransferNotifications.ONGOING_ID + 2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(TransferNotifications.ONGOING_ID + 2, n)
    }

    private class Item(val e: RemoteEntry, val size: Long, val kind: Int, val needThumb: Boolean, val needSeek: Boolean)

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground()) }
        val account = app.database.accountDao().getById(inputData.getLong(KEY_ACCOUNT, -1L)) ?: return Result.failure()
        val listFile = File(applicationContext.cacheDir, inputData.getString(KEY_LIST).orEmpty())
        if (!listFile.exists()) return Result.failure()
        val isLocal = account.backendType == "local"
        val api = app.rcloneApi
        val sidecar = app.sidecar
        val seeks = app.seekSheets
        val remote = account.remoteName
        val fs = fsRootFor(remote, account.backendType)
        val tmp = File(applicationContext.cacheDir, "selprep/${System.nanoTime()}").apply { mkdirs() }
        var downloaded = 0L
        val failedNames = ArrayList<String>()
        val lines = listFile.readLines().filter { it.length > 2 }
        try {
            // ১) বাছাই করা ফোল্ডার/ফাইল থেকে ফাইলের তালিকা
            val all = LinkedHashMap<String, RemoteEntry>()
            val dirs = lines.filter { it.startsWith("D\t") }.map { it.substring(2) }
            val files = lines.filter { it.startsWith("F\t") }.map { it.substring(2) }.toHashSet()
            for (d in dirs) {
                coroutineContext.ensureActive()
                runCatching { api.listFilesRecursive(fs, d) }.getOrNull()?.forEach { if (!it.isDir) all[it.path] = it }
            }
            for ((parent, group) in files.groupBy { it.substringBeforeLast('/', "") }) {
                coroutineContext.ensureActive()
                val set = group.toHashSet()
                runCatching { api.list(fs, parent) }.getOrNull()?.forEach { if (!it.isDir && it.path in set) all[it.path] = it }
            }
            val list = all.values.toList()
            // ২) ক্লাউডে আগে তৈরি থাকলে আমদানি (লোকালে নয়)
            if (!isLocal) for (round in 0 until 40) {
                coroutineContext.ensureActive()
                if (sidecar.importFromListing(remote, fs, list, 150) < 150) break
            }
            // ৩) কী কী বাকি
            val seekCloud = list.asSequence().map { it.path }.filter { it.contains("/$META_DIR/vseek/") || it.startsWith("$META_DIR/vseek/") }.toHashSet()
            val todo = ArrayList<Item>()
            for (e in list) {
                if (e.path.contains(META_DIR) || !sidecar.supports(e.name)) continue
                val size = e.size ?: continue
                val name = e.name
                val isVideo = guessMime(name, e.mimeType).startsWith("video/") && ThumbMetaReader.isMedia(name) && !ThumbMetaReader.isImage(name)
                val isAudio = AudioMetaReader.isAudio(name)
                val isImage = ThumbMetaReader.isImage(name)
                if (isLocal && isImage) continue // লোকাল ছবি সরাসরি আসল ফাইল থেকেই দেখা যায়
                val kind = when { isAudio -> 2; isVideo -> 1; isImage -> 0; else -> 3 }
                val needThumb = if (isLocal) !sidecar.hasEntry(remote, e.path, size, name) else !sidecar.isComplete(remote, e.path, size, name)
                val needSeek = isVideo && size > 0 && !seeks.has(remote, e.path, size) && !seeks.isNone(remote, e.path, size)
                if (!needThumb && !needSeek) continue // আগেই বানানো: অটো স্কিপ
                todo += Item(e, size, kind, needThumb, needSeek)
            }
            val ordered = todo.sortedBy { if (it.needThumb) 0 else 1 }
            total = ordered.size
            phaseScan = false
            runCatching { setForeground(foreground()) }
            val startedAt = SystemClock.elapsedRealtime()
            // ৪) একটা একটা করে বানানো
            for ((idx, item) in ordered.withIndex()) {
                coroutineContext.ensureActive()
                if (downloaded >= MAX_BYTES) break
                val e = item.e
                val name = e.name
                val mime = guessMime(name, e.mimeType)
                var ok = true
                runCatching {
                    val isVideo = item.kind == 1
                    if (isLocal) {
                        val url = api.streamUrl(fs, e.path, mime, background = true)
                        if (item.kind == 2) { sidecar.cacheAudioLocally(remote, e.path, item.size, url) }
                        else if (isVideo && sidecar.prepareLocalVideo(remote, e.path, item.size, url, item.needThumb, item.needSeek) < 0) ok = false
                    } else {
                        if (item.needThumb) {
                            val c = processCloud(e, item.size, fs, tmp, item.kind)
                            if (c < 0) ok = false else downloaded += c
                        }
                        if (ok && item.needSeek) {
                            val url = api.streamUrl(fs, e.path, mime, background = true)
                            val r = sidecar.backfillSeek(fs, e.path, item.size, url, seeks.remotePath(e.path, item.size) in seekCloud)
                            if (r < 0) ok = false else downloaded += SEEK_COST
                        }
                    }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; ok = false }
                done++
                if (!ok) { failed++; if (failedNames.size < 8) failedNames += name }
                val perFile = (SystemClock.elapsedRealtime() - startedAt).toDouble() / done
                etaSec = if (idx >= 2) ((total - done) * perFile / 1000).toLong() else -1L
                if (done % 3 == 0) runCatching { setForeground(foreground()) }
                if (done % 40 == 0 && !isLocal) withContext(NonCancellable) { runCatching { sidecar.flushPending() } }
                delay(80)
            }
        } finally {
            withContext(NonCancellable) {
                if (!isLocal) runCatching { sidecar.flushPending() }
                tmp.deleteRecursively()
                listFile.delete()
            }
        }
        runCatching {
            TransferNotifications.finished(
                applicationContext, TransferNotifications.ONGOING_ID + 3, applicationContext.getString(R.string.selprep_notif_title),
                applicationContext.getString(R.string.selprep_done_text, done - failed, failed, total),
            )
        }
        return Result.success(workDataOf(KEY_DONE to (done - failed), KEY_TOTAL to total, KEY_FAILED to failed, KEY_FAILED_NAMES to failedNames.joinToString("\n")))
    }

    /** ক্লাউড/ভল্ট: আগে স্ট্রিম থেকে; না হলে (একান্ত দরকারে, সীমার মধ্যে) পুরো ফাইল নামিয়ে। ডাউনলোড হওয়া বাইট, ব্যর্থ হলে -1 */
    private suspend fun processCloud(e: RemoteEntry, size: Long, fs: String, tmp: File, kind: Int): Long {
        val name = e.name
        val sidecar = app.sidecar
        val remote = fs.removeSuffix(":")
        val api = app.rcloneApi
        val ext = name.substringAfterLast('.', "").lowercase()
        if (kind == 1 || kind == 2) {
            val url = api.streamUrl(fs, e.path, guessMime(name, e.mimeType), background = true)
            runCatching { if (kind == 1) sidecar.commitVideoFromUrl(fs, e.path, size, url) else sidecar.commitAudioFromUrl(fs, e.path, size, url) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            if (sidecar.hasEntry(remote, e.path, size, name)) return if (kind == 1) 4L * 1024 * 1024 else 2L * 1024 * 1024
        } else if (ext != "apk" && sidecar.hasEntry(remote, e.path, size, name) && app.photoPreviews.fetchRemote(remote, fs, e.path, size)) {
            return 200L * 1024
        }
        // শেষ উপায়: পুরো ফাইল ডাউনলোড (সীমার মধ্যে)
        val limit = when { ext == "apk" -> 300L * 1024 * 1024; kind == 1 -> MAX_FULL_VIDEO; kind == 2 -> 100L * 1024 * 1024; else -> 25L * 1024 * 1024 }
        if (size <= 0 || size > limit) return -1L
        api.transferFile(fs, e.path, tmp.absolutePath, name, false)
        val local = File(tmp, name)
        val entry = try { sidecar.prepare(local) } finally { local.delete() }
        return if (entry == null) -1L else { sidecar.commit(fs, e.path, entry); size }
    }

    companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_LIST = "list"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
        const val KEY_FAILED_NAMES = "failed_names"
        private const val MAX_BYTES = 3_000L * 1024 * 1024
        private const val MAX_FULL_VIDEO = 400L * 1024 * 1024
        private const val SEEK_COST = 12L * 1024 * 1024
    }
}

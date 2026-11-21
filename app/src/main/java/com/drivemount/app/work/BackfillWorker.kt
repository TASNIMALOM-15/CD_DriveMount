package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.ForegroundInfo
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
import android.os.SystemClock
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * পুরোনো ফাইলের প্রিভিউ/আইকন/সিকবার-শিট বানানো। শুধু ব্যবহারকারীর স্পষ্ট অনুমতিতে (সেটিংসের ডায়ালগ) শুরু হয়;
 * কখনো নিজে থেকে বা নিয়মিত চলে না। সীমা: একবারে সর্বোচ্চ ~১.৫GB ডাউনলোড; Wi-Fi ও ব্যাটারি ঠিক থাকলে চলে।
 * আবার চালালে আগেরটা যেখানে শেষ করেছিল সেখান থেকে এগোয় (যা হয়ে গেছে তা বাদ যায়)।
 *
 * ধাপ: ১) পুরো অ্যাকাউন্টের তালিকা একবারে  ২) ক্লাউডে থাকা তথ্য আনা  ৩) কী কী বাকি তার হিসাব (মোট জানা যায়)
 *       ৪) একটা একটা করে বানানো — প্রতিবার অগ্রগতি (কতটা, কোনটা, কত সময় বাকি) জানানো  ৫) ক্লাউডে লেখা
 */
class BackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(PHASE_SCAN, 0, 0, -1L)

    private fun foreground(phase: Int, done: Int, total: Int, etaSec: Long): ForegroundInfo {
        val ctx = applicationContext
        val text = when {
            phase == PHASE_SCAN -> ctx.getString(R.string.backfill_phase_scan)
            phase == PHASE_IMPORT -> ctx.getString(R.string.backfill_phase_import)
            phase == PHASE_SAVE -> ctx.getString(R.string.backfill_phase_save)
            etaSec >= 0 -> ctx.getString(R.string.backfill_notif_text_eta, done, total, etaText(ctx, etaSec))
            else -> ctx.getString(R.string.backfill_notif_text, done)
        }
        val cancel = WorkManager.getInstance(ctx).createCancelPendingIntent(id)
        val n = TransferNotifications.ongoing(
            ctx, ctx.getString(R.string.backfill_notif_title), text,
            if (phase == PHASE_WORK) done.toLong() else 0L, if (phase == PHASE_WORK) total.toLong() else 0L, null, cancel,
        )
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(TransferNotifications.ONGOING_ID + 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(TransferNotifications.ONGOING_ID + 1, n)
        }
    }

    /** কাজের তালিকার একটি ফাইল: কী বানাতে হবে */
    private class Item(val e: RemoteEntry, val size: Long, val kind: Int, val needThumb: Boolean, val needSeek: Boolean, val cloudSeek: Boolean)

    private val cats = IntArray(8) // [ছবি done,total, ভিডিও done,total, গান done,total, অ্যাপ done,total]
    private var phase = PHASE_SCAN
    private var total = 0
    private var done = 0
    private var failed = 0
    private var cur = ""
    private var etaSec = -1L
    private val startedAt = SystemClock.elapsedRealtime()
    private val failedNames = ArrayList<String>()

    private suspend fun publish(force: Boolean = true) {
        val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000
        runCatching {
            setProgress(
                workDataOf(
                    KEY_PHASE to phase, KEY_TOTAL to total, KEY_DONE to done, KEY_FAILED to failed,
                    KEY_CUR to cur.take(80), KEY_ETA to etaSec, KEY_ELAPSED to elapsed, KEY_CATS to cats.copyOf(),
                ),
            )
        }
        if (force) runCatching { setForeground(foreground(phase, done, total, etaSec)) }
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground(PHASE_SCAN, 0, 0, -1L)) }
        val account = app.database.accountDao().getById(inputData.getLong(KEY_ACCOUNT, -1L)) ?: return Result.failure()
        if (account.backendType == "local") return Result.failure()

        val api = app.rcloneApi
        val sidecar = app.sidecar
        val seeks = app.seekSheets
        val remote = account.remoteName
        val fs = fsRootFor(remote, account.backendType)
        val tmp = File(applicationContext.cacheDir, "backfill/${System.nanoTime()}").apply { mkdirs() }
        var downloaded = 0L
        var partial = false
        var skipped = 0

        try {
            // ১) পুরো অ্যাকাউন্টের তালিকা একবারে (ফোল্ডার ধরে ধরে নয়)
            publish()
            val all = runCatching { api.listFilesRecursive(fs, "") }.getOrNull() ?: return Result.retry()

            // ২) ক্লাউডের প্যাক থেকে আগের তৈরি তথ্য আনা (অন্য ফোন/আগের ইনস্টলের)
            phase = PHASE_IMPORT
            publish()
            for (round in 0 until 40) {
                coroutineContext.ensureActive()
                if (sidecar.importFromListing(remote, fs, all, 150) < 150) break
            }

            // ৩) কী কী বাকি: হিসাব করে মোট বের করা
            val seekCloud = all.asSequence().map { it.path }.filter { it.contains("/$META_DIR/vseek/") || it.startsWith("$META_DIR/vseek/") }.toHashSet()
            val todo = ArrayList<Item>()
            var already = 0
            for (e in all) {
                if (e.isDir || e.path.contains(META_DIR) || !sidecar.supports(e.name)) continue
                val size = e.size ?: continue
                val name = e.name
                val isVideo = guessMime(name, e.mimeType).startsWith("video/") && ThumbMetaReader.isMedia(name) && !ThumbMetaReader.isImage(name)
                val kind = when {
                    AudioMetaReader.isAudio(name) -> KIND_AUDIO
                    isVideo -> KIND_VIDEO
                    ThumbMetaReader.isImage(name) -> KIND_PHOTO
                    else -> KIND_APP
                }
                val needThumb = !sidecar.isComplete(remote, e.path, size, name)
                val needSeek = isVideo && size > 0 && !seeks.has(remote, e.path, size) && !seeks.isNone(remote, e.path, size)
                if (!needThumb && !needSeek) { already++; continue }
                todo += Item(e, size, kind, needThumb, needSeek, seeks.remotePath(e.path, size) in seekCloud)
            }
            skipped = already
            // থাম্বনেল বাকি থাকাগুলো আগে (দেখা যায় এমন কাজ), তারপর শুধু সিকবার-শিট
            val ordered = todo.sortedBy { if (it.needThumb) 0 else 1 }
            total = ordered.size
            for (x in ordered) cats[x.kind * 2 + 1]++

            phase = PHASE_WORK
            publish()

            // ৪) একটা একটা করে বানানো; প্রতিবার অগ্রগতি
            var avgMs = 0.0
            for ((idx, item) in ordered.withIndex()) {
                coroutineContext.ensureActive()
                if (downloaded >= MAX_BYTES) { partial = true; break }
                cur = item.e.name
                val t0 = SystemClock.elapsedRealtime()
                var ok = true
                var cost = 0L
                if (item.needThumb) {
                    val c = process(item.e, item.size, fs, tmp)
                    if (c < 0) ok = false else cost += c
                }
                if (ok && item.needSeek) {
                    val url = runCatching { api.streamUrl(fs, item.e.path, guessMime(item.e.name, item.e.mimeType), background = true) }.getOrNull()
                    val r = if (url == null) -1 else runCatching { sidecar.backfillSeek(fs, item.e.path, item.size, url, item.cloudSeek) }
                        .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else -1 }
                    if (r < 0) ok = false else cost += if (item.cloudSeek) PREVIEW_COST else SEEK_COST
                }
                downloaded += cost
                done++
                if (ok) cats[item.kind * 2]++ else { failed++; if (failedNames.size < 8) failedNames += item.e.name }
                // বাকি সময়: প্রতি ফাইলের গড় সময় (ধীরে বদলানো) × বাকি ফাইল
                val took = (SystemClock.elapsedRealtime() - t0).toDouble()
                avgMs = if (avgMs == 0.0) took else avgMs * 0.85 + took * 0.15
                etaSec = if (idx >= 2) ((total - done) * avgMs / 1000).toLong() else -1L
                publish(force = done % 3 == 0)
                if (done % 40 == 0) withContext(NonCancellable) { runCatching { sidecar.flushPending() } }
                delay(100) // CPU/নেটওয়ার্কে চাপ কম রাখতে
            }
            if (done < total) partial = true
        } finally {
            // ৫) ক্লাউডে লেখা
            withContext(NonCancellable) {
                phase = PHASE_SAVE
                publish()
                runCatching { sidecar.flushPending() }
                tmp.deleteRecursively()
            }
        }
        return Result.success(
            workDataOf(
                KEY_DONE to (done - failed), KEY_TOTAL to total, KEY_FAILED to failed, KEY_SKIPPED to skipped,
                KEY_PARTIAL to partial, KEY_ELAPSED to (SystemClock.elapsedRealtime() - startedAt) / 1000,
                KEY_FAILED_NAMES to failedNames.joinToString("\n"), KEY_CATS to cats.copyOf(),
            ),
        )
    }

    /** একটি ফাইলের সাইডকার বানায়; ডাউনলোড হওয়া বাইট ফেরত দেয়, ব্যর্থ/বাদ হলে -1 */
    private suspend fun process(e: RemoteEntry, size: Long, fs: String, tmp: File): Long {
        val name = e.name
        val ext = name.substringAfterLast('.', "").lowercase()
        val isVideo = guessMime(name, e.mimeType).startsWith("video/")
        val sidecar = app.sidecar
        return runCatching {
            if (com.drivemount.app.data.meta.AudioMetaReader.isAudio(name)) {
                val url = app.rcloneApi.streamUrl(fs, e.path, guessMime(name, e.mimeType), background = true)
                sidecar.commitAudioFromUrl(fs, e.path, size, url)
                if (sidecar.hasEntry(fsName(fs), e.path, size, name)) AUDIO_COST else -1L
            } else if (isVideo) {
                val url = app.rcloneApi.streamUrl(fs, e.path, guessMime(name, e.mimeType), background = true)
                sidecar.commitVideoFromUrl(fs, e.path, size, url)
                if (sidecar.hasEntry(fsName(fs), e.path, size, name)) VIDEO_COST else -1L
            } else {
                // থাম্বনেল আছে, শুধু প্রিভিউ নেই: ক্লাউডে আগেই থাকলে (অন্য ফোনের তৈরি) ছোট ফাইলটাই আনা যথেষ্ট
                if (ext != "apk" && sidecar.hasEntry(fsName(fs), e.path, size, name) &&
                    app.photoPreviews.fetchRemote(fsName(fs), fs, e.path, size)
                ) return PREVIEW_COST
                val limit = if (ext == "apk") MAX_APK else MAX_IMAGE
                if (size <= 0 || size > limit) return -1L
                app.rcloneApi.transferFile(fs, e.path, tmp.absolutePath, name, false)
                val local = File(tmp, name)
                val entry = try { sidecar.prepare(local) } finally { local.delete() }
                if (entry == null) -1L else { sidecar.commit(fs, e.path, entry); size }
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            -1L
        }
    }

    private fun fsName(fs: String) = fs.removeSuffix(":")

    companion object {
        const val UNIQUE_NAME = "dm-backfill"
        const val KEY_ACCOUNT = "account"
        const val KEY_DONE = "done"
        const val KEY_PARTIAL = "partial"
        const val KEY_PHASE = "phase"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
        const val KEY_SKIPPED = "skipped"
        const val KEY_CUR = "cur"
        const val KEY_ETA = "eta"
        const val KEY_ELAPSED = "elapsed"
        const val KEY_CATS = "cats"
        const val KEY_FAILED_NAMES = "failed_names"
        const val PHASE_SCAN = 0
        const val PHASE_IMPORT = 1
        const val PHASE_WORK = 2
        const val PHASE_SAVE = 3
        private const val KIND_PHOTO = 0
        private const val KIND_VIDEO = 1
        private const val KIND_AUDIO = 2
        private const val KIND_APP = 3
        private const val MAX_BYTES = 1_500L * 1024 * 1024
        private const val MAX_APK = 300L * 1024 * 1024
        private const val MAX_IMAGE = 25L * 1024 * 1024
        private const val PREVIEW_COST = 200L * 1024
        private const val AUDIO_COST = 2L * 1024 * 1024
        private const val VIDEO_COST = 4L * 1024 * 1024
        private const val SEEK_COST = 12L * 1024 * 1024

        /** "প্রায় ১২ মিনিট বাকি" ধরনের লেখা (নোটিফিকেশন ও কার্ড দুটোতেই) */
        fun etaText(ctx: Context, sec: Long): String = when {
            sec < 60 -> ctx.getString(R.string.backfill_eta_lt_min)
            sec < 3600 -> ctx.getString(R.string.backfill_eta_min, ((sec + 30) / 60).toInt())
            else -> ctx.getString(R.string.backfill_eta_hr, (sec / 3600).toInt(), ((sec % 3600) / 60).toInt())
        }
    }
}

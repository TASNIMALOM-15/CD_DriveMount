package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
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
 * পুরোনো ফাইলের প্রিভিউ/আইকন বানানো। শুধু ব্যবহারকারীর স্পষ্ট অনুমতিতে (সেটিংসের ডায়ালগ) শুরু হয়;
 * কখনো নিজে থেকে বা নিয়মিত চলে না। সীমা: একবারে সর্বোচ্চ ~১.৫GB ডাউনলোড; Wi-Fi ও ব্যাটারি ঠিক থাকলে চলে।
 * আবার চালালে আগেরটা যেখানে শেষ করেছিল (যেসব ফাইলের সাইডকার হয়ে গেছে সেগুলো বাদ দিয়ে) সেখান থেকে এগোয়।
 */
class BackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0)

    private fun foreground(done: Int): ForegroundInfo {
        val text = if (done == 0) "" else applicationContext.getString(R.string.backfill_notif_text, done)
        val n = TransferNotifications.ongoing(applicationContext, applicationContext.getString(R.string.backfill_notif_title), text, 0L, 0L)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(TransferNotifications.ONGOING_ID + 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(TransferNotifications.ONGOING_ID + 1, n)
        }
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground(0)) }
        val account = app.database.accountDao().getById(inputData.getLong(KEY_ACCOUNT, -1L)) ?: return Result.failure()
        if (account.backendType == "local") return Result.failure()

        val api = app.rcloneApi
        val sidecar = app.sidecar
        val fs = fsRootFor(account.remoteName, account.backendType)
        val tmp = File(applicationContext.cacheDir, "backfill/${System.nanoTime()}").apply { mkdirs() }
        var done = 0
        var downloaded = 0L
        val queue = ArrayDeque<String>().apply { add("") }
        var folders = 0

        try {
            while (queue.isNotEmpty() && folders < MAX_FOLDERS && downloaded < MAX_BYTES) {
                coroutineContext.ensureActive()
                val dir = queue.removeFirst()
                folders++
                val list = app.fileRepository.list(account, dir).getOrNull() ?: continue
                list.filter { it.isDir }.forEach { queue.add(it.path) }
                sidecar.importNow(account.remoteName, fs, dir, list)

                for (e in list) {
                    coroutineContext.ensureActive()
                    if (e.isDir || !sidecar.supports(e.name)) continue
                    val size = e.size ?: continue
                    if (sidecar.isComplete(account.remoteName, e.path, size, e.name)) continue
                    if (downloaded >= MAX_BYTES) break
                    val cost = process(e, size, fs, tmp)
                    if (cost >= 0) {
                        downloaded += cost
                        done++
                        setProgress(workDataOf(KEY_DONE to done))
                        if (done % 5 == 0) runCatching { setForeground(foreground(done)) }
                        delay(150) // CPU/নেটওয়ার্কে চাপ কম রাখতে
                    }
                }
                // ফোল্ডার-প্রতি একবার ক্লাউডের প্যাকে লেখা
                withContext(NonCancellable) { runCatching { sidecar.flushPending() } }
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { sidecar.flushPending() }
                tmp.deleteRecursively()
            }
        }
        return Result.success(workDataOf(KEY_DONE to done, KEY_PARTIAL to (queue.isNotEmpty() || downloaded >= MAX_BYTES)))
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
        private const val MAX_BYTES = 1_500L * 1024 * 1024
        private const val MAX_APK = 300L * 1024 * 1024
        private const val MAX_IMAGE = 25L * 1024 * 1024
        private const val PREVIEW_COST = 200L * 1024
        private const val AUDIO_COST = 2L * 1024 * 1024
        private const val VIDEO_COST = 4L * 1024 * 1024
        private const val MAX_FOLDERS = 2000
    }
}

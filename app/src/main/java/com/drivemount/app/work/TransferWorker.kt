package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.drivemount.app.DriveMountApp
import com.drivemount.app.engine.TransferTuning
import com.drivemount.app.R
import com.drivemount.app.data.meta.SidecarEntry
import com.drivemount.app.data.transfer.TransferRepository
import com.drivemount.app.data.transfer.OpenRequest
import com.drivemount.app.data.transfer.TransferEntity
import com.drivemount.app.data.transfer.TransferStatus
import com.drivemount.app.engine.GroupStats
import com.drivemount.app.engine.fsJoin
import com.drivemount.app.engine.guessMime
import com.drivemount.app.ui.components.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.drivemount.app.data.transfer.TransferStage
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * ট্রান্সফারের লাইনের কর্মী: একটার পর একটা কাজ চালায়, অগ্রগতি ডাটাবেস ও নোটিফিকেশনে জানায়।
 * foreground হিসেবে চলে, তাই অ্যাপ ব্যাকগ্রাউন্ডে গেলেও কাজ চলতে থাকে।
 */
class TransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private enum class Outcome { DONE, FAILED, CANCELLED }

    private class UserCancelled : Exception()

    @Volatile private var lastError: String? = null

    private companion object {
        const val VERIFY_FAILED = "Upload verification failed"
        const val MAX_AUTO_RETRIES = 5
        const val MAX_NET_WAIT_MS = 15L * 60 * 1000
    }

    private fun isRetryable(error: String?): Boolean {
        val e = error?.lowercase() ?: return false
        return VERIFY_FAILED.lowercase() in e || listOf(
            "timeout", "timed out", "connection", "no such host", "network", "unreachable", "eof", "reset by peer",
            "broken pipe", "temporarily", "dial tcp", "tls", "502", "503", "504",
        ).any { it in e }
    }

    private fun online(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** নেট ফিরলে (সর্বোচ্চ ১৫ মিনিট অপেক্ষা) true */
    private suspend fun awaitNetwork(): Boolean {
        var waited = 0L
        while (!online()) {
            if (waited >= MAX_NET_WAIT_MS) return false
            delay(3_000); waited += 3_000
        }
        return true
    }

    private val repo get() = (applicationContext as DriveMountApp).transferRepository
    private val sidecar get() = (applicationContext as DriveMountApp).sidecar
    private val fileDates get() = (applicationContext as DriveMountApp).fileDates

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foreground(applicationContext.getString(R.string.tr_notif_title), "", 0L, 0L)

    private fun foreground(title: String, text: String, done: Long, total: Long): ForegroundInfo {
        val notification = TransferNotifications.ongoing(applicationContext, title, text, done, total)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                TransferNotifications.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(TransferNotifications.ONGOING_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        // ব্যাকগ্রাউন্ড থেকে শুরু হলে সিস্টেম foreground মোড না-ও দিতে পারে; তখনও কাজ চলবে
        runCatching { setForeground(getForegroundInfo()) }

        val dao = repo.dao
        dao.resetRunning()

        var ok = 0
        var failed = 0
        val autoRetries = HashMap<Long, Int>()
        try {
            while (true) {
                val task = dao.nextQueued() ?: break
                when (runTask(task)) {
                    Outcome.DONE -> ok++
                    Outcome.FAILED -> {
                        // নেট-সংক্রান্ত/যাচাই-ব্যর্থ হলে ব্যবহারকারীকে ব্যর্থ না দেখিয়ে নেট ফেরার অপেক্ষায় আবার লাইনে
                        val n = (autoRetries[task.id] ?: 0) + 1
                        if (n <= MAX_AUTO_RETRIES && isRetryable(lastError) && awaitNetwork()) {
                            autoRetries[task.id] = n
                            dao.setStatus(task.id, TransferStatus.QUEUED, null, null)
                            delay(minOf(2_000L * n, 10_000L))
                        } else failed++
                    }
                    Outcome.CANCELLED -> Unit
                }
            }
        } finally {
            withContext(NonCancellable) { dropLeftoverPrefetch() }
        }
        // লাইন ফাঁকা: আপলোড হওয়া APK-গুলোর আইকন ক্লাউডের ফোল্ডার-প্যাকে লেখা (ফোল্ডার-প্রতি একবার)
        runCatching { sidecar.flushPending() }
        if (ok + failed > 0) {
            // রিপোর্ট: কতগুলো ফাইল হলো, কতগুলো ব্যর্থ, আর কতগুলো প্রিভিউ ক্লাউডে যেতে বাকি
            val left = runCatching { sidecar.backlog().total }.getOrDefault(0)
            TransferNotifications.summary(applicationContext, ok, failed, left)
        }
        cleanOldCache()
        return Result.success()
    }

    /** একটি ফাইলের আপলোড-পূর্ব প্রস্তুতির ফল (কপি + তারিখ + প্রিভিউ + জিপ-তালিকা) */
    private class Prep(
        val srcFs: String,
        val srcPath: String,
        val takenMs: Long?,
        val upSize: Long,
        val apkEntry: SidecarEntry?,
        val zipIdx: ByteArray?,
        val zipSize: Long,
    )

    /** পরের কাজগুলোর প্রস্তুতি আগেই করে রাখা; এক সময়ে সর্বোচ্চ ১টি (ডিস্ক/CPU ও জায়গা বাঁচাতে) */
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetched = HashMap<Long, Deferred<Prep>>()

    private suspend fun prefetchNext() {
        val dao = repo.dao
        // বাতিল/মুছে যাওয়া কাজের পুরোনো এন্ট্রি সরানো, নইলে "একটি" সীমা চিরতরে আটকে যেত
        for (pid in prefetched.keys.toList()) {
            if (dao.getById(pid)?.status != TransferStatus.QUEUED) discardPrefetch(pid, deleteStage = true)
        }
        if (prefetched.isNotEmpty()) return
        val next = dao.nextQueued() ?: return
        if (next.isDir || next.id in repo.cancelRequested) return
        // ক্লাউড থেকে ক্লাউডে কপির জন্য লোকাল প্রস্তুতি নেই
        val local = if (next.sourceUri == null) localFileOf(next.srcFs, next.srcPath) else null
        if (next.sourceUri == null && local == null) return
        if (!hasRoomFor(next, local)) return
        prefetched[next.id] = prefetchScope.async { prepareTask(next, false) }
    }

    /** পরের ফাইলের অস্থায়ী কপি (বাছাই করা হলে) + fast-start কপির জন্য জায়গা আছে কি */
    private fun hasRoomFor(task: TransferEntity, local: File?): Boolean {
        val size = local?.length() ?: runCatching {
            applicationContext.contentResolver.query(Uri.parse(task.sourceUri), arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()
        val need = if (size != null && size >= 0) size * 2 + 256L * 1024 * 1024 else 2L * 1024 * 1024 * 1024
        return applicationContext.cacheDir.usableSpace > need
    }

    private fun discardPrefetch(id: Long, deleteStage: Boolean) {
        val d = prefetched.remove(id) ?: return
        repo.cancelRequested.remove(id)
        if (d.isActive) d.cancel()
        else if (d.isCompleted && !d.isCancelled) runCatching { d.getCompleted().apkEntry?.fast?.delete() }
        if (deleteStage) File(applicationContext.cacheDir, "upload/$id").deleteRecursively()
    }

    /** কাজ শেষে/থামলে অব্যবহৃত প্রস্তুতি সাফ: কাজ এখনো লাইনে থাকলে (সিস্টেম থামালে) অস্থায়ী কপি রাখা, পরেরবার কাজে লাগবে */
    private suspend fun dropLeftoverPrefetch() {
        prefetchScope.cancel()
        for (pid in prefetched.keys.toList()) {
            val stillQueued = repo.dao.getById(pid)?.status == TransferStatus.QUEUED
            discardPrefetch(pid, deleteStage = !stillQueued)
        }
    }

    /** আপলোডের আগের কাজ: বাছাই করা ফাইলের কপি, তোলার তারিখ, আইকন/থাম্বনেল/seek sheet/মেটা, জিপের তালিকা */
    private fun stageLabel(stage: Int): String = applicationContext.getString(
        when (stage) {
            TransferStage.THUMB -> R.string.tr_stage_thumb
            TransferStage.SEEK -> R.string.tr_stage_seek
            TransferStage.FAST -> R.string.tr_stage_fast
            TransferStage.UPLOAD -> R.string.tr_stage_upload
            else -> R.string.tr_stage_working
        },
    )

    /** ধাপ জানানো: UI-তে সবসময়; নোটিফিকেশনে শুধু এখন যে ফাইল সামনে চলছে তার জন্য */
    private fun reportStage(task: TransferEntity, stage: Int, notify: Boolean) {
        repo.setStage(task.id, stage)
        if (notify) {
            TransferNotifications.update(
                applicationContext,
                TransferNotifications.ongoing(
                    applicationContext, task.name, stageLabel(stage), 0L, 0L,
                    cancel = TransferNotifications.cancelIntent(applicationContext, task.id),
                ),
            )
        }
    }

    /** প্রস্তুতি চলাকালে Cancel চাপলে প্রায় সঙ্গে সঙ্গে থামে (পেছনের ভারী কাজও [cancelRequested] দেখে থামে) */
    private suspend fun prepareCancellable(task: TransferEntity, pre: Deferred<Prep>?): Prep = try {
        coroutineScope {
            val d = pre ?: async(Dispatchers.IO) { prepareTask(task, true) }
            val watcher = launch {
                while (true) {
                    delay(100)
                    if (task.id in repo.cancelRequested) { d.cancel(); throw UserCancelled() }
                }
            }
            try { d.await() } finally { watcher.cancel() }
        }
    } catch (e: CancellationException) {
        // ব্যবহারকারী বাতিল করেছেন (কর্মী নিজে বাতিল হয়নি) হলে সাধারণ "বাতিল" হিসেবে ধরা
        if (task.id in repo.cancelRequested && currentCoroutineContext().isActive) throw UserCancelled() else throw e
    }

    private suspend fun prepareTask(task: TransferEntity, notify: Boolean = false): Prep {
        reportStage(task, TransferStage.WORKING, notify)
        var srcFs = task.srcFs
        var srcPath = task.srcPath
        var takenMs: Long? = null
        var upSize = -1L
        var apkEntry: SidecarEntry? = null
        var zipIdx: ByteArray? = null
        var zipSize = 0L
        if (task.sourceUri != null) {
            val staged = stageUpload(task)
            srcFs = staged.parentFile?.absolutePath ?: throw IOException("Could not prepare the file")
            srcPath = staged.name
            // অস্থায়ী কপির তারিখ হতো "এখন"; আসল তারিখ বের করে বসানো, যাতে ক্লাউডেও তোলার তারিখই থাকে (rclone ফাইলের তারিখ রাখে)
            takenMs = com.drivemount.app.data.meta.FileDates.fromMedia(staged)
                ?: com.drivemount.app.data.meta.FileDates.fromUri(applicationContext, Uri.parse(task.sourceUri))
            takenMs?.let { staged.setLastModified(it) }
            upSize = staged.length()
        } else if (!task.isDir) {
            localFileOf(srcFs, srcPath)?.let { f ->
                takenMs = com.drivemount.app.data.meta.FileDates.fromMedia(f) ?: f.lastModified().takeIf { it > 0 }
                upSize = f.length()
            }
        }
        if (task.id in repo.cancelRequested) throw UserCancelled()

        // APK/ছবি/ভিডিও হলে আইকন/প্রিভিউ আগেই বের করা: "move" হলে কাজ শেষে সোর্স ফাইল আর থাকে না
        if (!task.isDir && isCloudDst(task) && sidecar.supports(task.name)) {
            apkEntry = runCatching {
                sidecar.prepare(
                    localFileOf(srcFs, srcPath),
                    onStage = { reportStage(task, it, notify) },
                    cancelled = { task.id in repo.cancelRequested },
                )
            }.getOrNull()
            if (task.id in repo.cancelRequested) { apkEntry?.fast?.delete(); throw UserCancelled() }
        }

        // জিপ হলে ভেতরের ফোল্ডার-ট্রী/সাইজের তালিকা আগেই বানানো (আপলোডের সঙ্গে ক্লাউডে যাবে)
        if (!task.isDir && isCloudDst(task) && task.name.endsWith(".zip", ignoreCase = true)) {
            runCatching {
                localFileOf(srcFs, srcPath)?.let { f ->
                    zipSize = f.length()
                    zipIdx = (applicationContext as DriveMountApp).zipIndex.buildFromFile(f)
                }
            }
        }

        return Prep(srcFs, srcPath, takenMs, upSize, apkEntry, zipIdx, zipSize)
    }

    private class DirItem(val dstPath: String, val size: Long, val entry: SidecarEntry?, val zip: ByteArray?)

    /**
     * ফোল্ডার আপলোড/Sync-এর প্রতিটি ফাইলের থাম্বনেইল, seek-sheet, fast-start (moov) কপি, APK আইকন/প্যাকেজ-তথ্য ও জিপের ফোল্ডার-ট্রী বানানো।
     * যেগুলোর ক্লাউড-তথ্য আগেই আছে সেগুলো বাদ (তাই আবার Sync করলে শুধু নতুন/বদলানো ফাইলে কাজ হয়)।
     * [collect] দিলে এখনই কমিট না করে তালিকায় রাখে ("move": কাজ শেষে সোর্স থাকবে না বলে আগে বানাতে হয়)।
     */
    private suspend fun dirSidecars(task: TransferEntity, root: File, collect: MutableList<DirItem>?, allowFast: Boolean) {
        val remote = task.dstFs.removeSuffix(":")
        val base = task.dstPath.trim('/')
        val zipIndex = (applicationContext as DriveMountApp).zipIndex
        val files = withContext(Dispatchers.IO) {
            root.walkTopDown().onEnter { !it.name.startsWith(".dm") }.filter { it.isFile }.toList()
        }
        var stage = TransferStage.WORKING
        for ((i, f) in files.withIndex()) {
            if (task.id in repo.cancelRequested) throw UserCancelled()
            val isZip = f.name.endsWith(".zip", ignoreCase = true)
            val sup = sidecar.supports(f.name)
            if (!sup && !isZip) continue
            val rel = f.relativeTo(root).path.replace('\\', '/')
            val dstPath = if (base.isEmpty()) rel else "$base/$rel"
            val size = f.length()
            fun progress() {
                TransferNotifications.update(
                    applicationContext,
                    TransferNotifications.ongoing(
                        applicationContext, task.name, stageLabel(stage) + " ${i + 1}/${files.size}", 0L, 0L,
                        cancel = TransferNotifications.cancelIntent(applicationContext, task.id),
                    ),
                )
            }
            if (sup && !sidecar.isComplete(remote, dstPath, size, f.name)) {
                val entry = runCatching {
                    sidecar.prepare(
                        f,
                        onStage = { stage = it; repo.setStage(task.id, it); progress() },
                        cancelled = { task.id in repo.cancelRequested },
                        allowFast = allowFast,
                    )
                }.getOrNull()
                if (task.id in repo.cancelRequested) { entry?.fast?.delete(); throw UserCancelled() }
                if (entry != null) {
                    if (collect != null) collect += DirItem(dstPath, size, entry, null)
                    else runCatching { sidecar.commit(task.dstFs, dstPath, entry) }
                }
            }
            if (isZip && !zipIndex.has(task.dstFs, dstPath, size)) {
                stage = TransferStage.WORKING; repo.setStage(task.id, stage); progress()
                val b = runCatching { zipIndex.buildFromFile(f) }.getOrNull()
                if (b != null) {
                    if (collect != null) collect += DirItem(dstPath, size, null, b)
                    else runCatching { zipIndex.storeAndUpload(task.dstFs, dstPath, size, b) }
                }
            }
        }
    }

    private fun isCloudDst(t: TransferEntity) = t.dstFs.endsWith(":") && !t.dstFs.startsWith("/")

    private fun localPathOf(fs: String, path: String): File? = when {
        fs.startsWith("/") -> File(fs, path)
        fs.endsWith(":/") -> File("/$path")
        else -> null
    }

    /**
     * ফোন থেকে ফোনে কপি/সরানো: rclone, থাম্বনেল, moov ছাড়াই সরাসরি।
     * সরানো = rename (একই ড্রাইভে মিলিসেকেন্ড, ফাইলের আকার যাই হোক); কপি = কার্নেলের sendfile।
     * কিছু না মিললে বা ব্যর্থ হলে false (তখন আগের সাধারণ পথ)।
     */
    private suspend fun tryFastLocal(task: TransferEntity): Boolean = withContext(Dispatchers.IO) {
        if (task.sourceUri != null || task.mirror || task.profileId != null) return@withContext false
        val src = localPathOf(task.srcFs, task.srcPath) ?: return@withContext false
        val dst = localPathOf(task.dstFs, task.dstPath) ?: return@withContext false
        if (!src.exists() || dst.exists()) return@withContext false
        runCatching {
            dst.parentFile?.mkdirs()
            if (task.move) {
                java.nio.file.Files.move(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                true
            } else if (src.isFile) {
                java.io.FileInputStream(src).channel.use { from ->
                    java.io.FileOutputStream(dst).channel.use { to ->
                        var pos = 0L
                        val size = from.size()
                        while (pos < size) {
                            ensureActive()
                            pos += from.transferTo(pos, minOf(size - pos, 64L * 1024 * 1024), to)
                        }
                    }
                }
                dst.setLastModified(src.lastModified())
                true
            } else false
        }.getOrElse { dst.takeIf { d -> !task.move && d.isFile }?.delete(); false }
    }

    private suspend fun runTask(task: TransferEntity): Outcome {
        val dao = repo.dao
        val api = repo.api
        val id = task.id
        val group = "dm-$id"
        var jobId: Long? = null
        var outcome = Outcome.FAILED
        var error: String? = null
        var apkEntry: SidecarEntry? = null
        var zipIdx: ByteArray? = null
        var zipSize = 0L
        // আপলোডের দুটি তারিখের একটি: তোলার/বানানোর তারিখ (ms) ও ফাইলের আকার; আপলোডের তারিখ শেষে 'এখন'
        var takenMs: Long? = null
        var upSize = -1L

        val fast = tryFastLocal(task)
        if (fast) outcome = Outcome.DONE else try {
            dao.setStatus(id, TransferStatus.PREPARING, null, null)

            // আগের ফাইল আপলোড হওয়ার সময়ই এটির প্রস্তুতি (কপি/থাম্বনেল/seek sheet) হয়ে থাকলে সেটাই নেওয়া
            val prep = prepareCancellable(task, prefetched.remove(id))
            val srcFs = prep.srcFs
            val srcPath = prep.srcPath
            takenMs = prep.takenMs
            upSize = prep.upSize
            apkEntry = prep.apkEntry
            zipIdx = prep.zipIdx
            zipSize = prep.zipSize
            if (id in repo.cancelRequested) throw UserCancelled()

            val cloudUp = task.dstFs.endsWith(":") && !task.dstFs.startsWith("/") &&
                (task.sourceUri != null || srcFs.startsWith("/") || srcFs.endsWith(":/"))
            if (cloudUp) reportStage(task, TransferStage.UPLOAD, true) else repo.clearStage(id)
            val tuning = tuningFor(srcFs, task.dstFs)
            // ফোল্ডার ("move"): সোর্স মুছে যাবে, তাই প্রতিটি ফাইলের তথ্য আগেই বানানো (fast-start কপি বাদ: ডিস্ক জমে যাবে)
            val dirRoot = if (task.isDir && cloudUp) localPathOf(srcFs, srcPath)?.takeIf { it.isDirectory } else null
            val movedItems = if (dirRoot != null && task.move) {
                mutableListOf<DirItem>().also { dirSidecars(task, dirRoot, it, allowFast = false) }
            } else null
            if (movedItems != null) reportStage(task, TransferStage.UPLOAD, true)
            val job = if (task.isDir) {
                api.startDirJob(fsJoin(srcFs, srcPath), fsJoin(task.dstFs, task.dstPath), task.move, group, task.mirror, tuning)
            } else {
                api.startFileJob(srcFs, srcPath, task.dstFs, task.dstPath, task.move, group, tuning)
            }
            jobId = job
            repo.runningJobs[id] = job
            dao.setStatus(id, TransferStatus.RUNNING, null, null)
            runCatching { prefetchNext() } // এই ফাইল আপলোড হওয়ার ফাঁকে পরেরটার প্রস্তুতি

            var lastWrite = 0L
            var stopSent = false
            // ফোল্ডার-কাজ: কোন ফাইল চলছে/শেষ — ফোল্ডার ব্রাউজে ধূসর→রঙিন দেখানোর জন্য
            val dirDone = HashSet<String>()
            var prevKeys: Set<String> = emptySet()
            var doneSnap: Set<String> = emptySet()
            while (true) {
                delay(500)
                // Cancel, job তৈরির ঠিক আগে/পরে চাপা হলেও যেন মিস না হয়
                if (!stopSent && id in repo.cancelRequested) { api.stopJob(job); stopSent = true }
                val status = api.jobStatus(job)
                val stats = api.groupStats(group)
                if (task.isDir) {
                    val cur = stats.transferring.associate { it.name to (it.bytes to it.size) }
                    val gone = prevKeys - cur.keys
                    if (gone.isNotEmpty()) { dirDone += gone; doneSnap = dirDone.toSet() }
                    prevKeys = cur.keys
                    repo.setDirProgress(id, TransferRepository.DirProgress(cur, doneSnap))
                }
                val now = SystemClock.elapsedRealtime()
                if (status.finished || now - lastWrite >= 1000) {
                    lastWrite = now
                    dao.updateProgress(id, stats.bytes, stats.totalBytes, stats.speed.toLong())
                    TransferNotifications.update(
                        applicationContext,
                        TransferNotifications.ongoing(
                            applicationContext,
                            task.name,
                            if (cloudUp) stageLabel(TransferStage.UPLOAD) + progressText(stats).let { if (it.isEmpty()) "" else " · $it" } else progressText(stats),
                            stats.bytes,
                            stats.totalBytes,
                            cancel = TransferNotifications.cancelIntent(applicationContext, id),
                        ),
                    )
                }
                if (status.finished) {
                    if (status.success) {
                        // ফোল্ডার: আপলোড/Sync শেষে প্রতিটি ফাইলের থাম্বনেইল, seek-sheet, moov, APK/জিপ তথ্য কমিট (Sync = সোর্স অক্ষত, তাই এখন বানানো)
                        if (dirRoot != null) {
                            try {
                                if (movedItems != null) {
                                    movedItems.forEach { it2 ->
                                        it2.entry?.let { e -> runCatching { sidecar.commit(task.dstFs, it2.dstPath, e) } }
                                        it2.zip?.let { b -> runCatching { (applicationContext as DriveMountApp).zipIndex.storeAndUpload(task.dstFs, it2.dstPath, it2.size, b) } }
                                    }
                                } else {
                                    dirSidecars(task, dirRoot, null, allowFast = true)
                                }
                            } catch (e: UserCancelled) {
                                throw e
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // থাম্বনেইল ব্যর্থ হলে আপলোড ব্যর্থ ধরা হয় না; পরের Sync/Backfill আবার চেষ্টা করবে
                            }
                        }
                        // যাচাই: ক্লাউডে ফাইলটি আছে ও আকার ঠিক আছে (rclone নিজেও আপলোডের পর আকার/হ্যাশ মেলায়)
                        if (!task.isDir && upSize >= 0 && task.dstFs.endsWith(":") && !task.dstFs.startsWith("/")) {
                            val remoteSize = runCatching { api.stat(task.dstFs, task.dstPath)?.size }.getOrNull()
                            if (remoteSize != upSize) throw IOException("$VERIFY_FAILED: expected $upSize, found $remoteSize")
                        }
                        outcome = Outcome.DONE
                    } else {
                        outcome = if (id in repo.cancelRequested) Outcome.CANCELLED else Outcome.FAILED
                        error = status.error.ifBlank { null }
                    }
                    break
                }
            }
        } catch (e: CancellationException) {
            // সিস্টেম কর্মীকে থামিয়ে দিয়েছে: কাজ থামিয়ে আবার লাইনে রাখা হয়
            withContext(NonCancellable) {
                jobId?.let { api.stopJob(it) }
                if (id in repo.pauseRequested) {
                    dao.setStatus(id, TransferStatus.PAUSED, null, null)
                } else if (id in repo.cancelRequested) {
                    dao.setStatus(id, TransferStatus.CANCELLED, null, System.currentTimeMillis())
                } else {
                    dao.setStatus(id, TransferStatus.QUEUED, null, null)
                }
                release(id, group, keepStage = id !in repo.cancelRequested)
            }
            throw e
        } catch (e: UserCancelled) {
            outcome = Outcome.CANCELLED
        } catch (e: Exception) {
            outcome = if (id in repo.cancelRequested) Outcome.CANCELLED else Outcome.FAILED
            error = e.message ?: e.toString()
        }

        lastError = error
        withContext(NonCancellable) {
            val now = System.currentTimeMillis()
            when (outcome) {
                Outcome.DONE -> {
                    dao.setDone(id, now)
                    if (!task.isDir) runCatching { recordRecent(task) }
                    apkEntry?.let { runCatching { sidecar.commit(task.dstFs, task.dstPath, it) } }
                    zipIdx?.let { b -> runCatching { (applicationContext as DriveMountApp).zipIndex.storeAndUpload(task.dstFs, task.dstPath, zipSize, b) } }
                    // দুটি তারিখ জমা: তোলার তারিখ আর আপলোডের তারিখ (এখন); শুধু ক্লাউডে আপলোড হলে, যেকোনো ধরনের ফাইলে
                    if (!task.isDir && upSize >= 0 && task.dstFs.endsWith(":") && !task.dstFs.startsWith("/")) {
                        runCatching {
                            fileDates.record(task.dstFs.removeSuffix(":"), task.dstFs, task.dstPath, upSize, takenMs ?: 0L, now)
                        }
                    }
                    if (task.openWhenDone) announceOpen(task)
                }
                Outcome.FAILED -> dao.setStatus(id, TransferStatus.FAILED, error ?: "Unknown error", now)
                Outcome.CANCELLED ->
                    if (id in repo.pauseRequested) dao.setStatus(id, TransferStatus.PAUSED, null, null)
                    else dao.setStatus(id, TransferStatus.CANCELLED, null, now)
            }
            task.profileId?.let { repo.syncDao.setResult(it, now, outcome.name, error) }
            // নেট/যাচাই-ব্যর্থ হলে আবার চেষ্টা হবে: স্টেজ করা কপি রেখে দেওয়া, যাতে আবার কপি করতে না হয়
            release(id, group, keepStage = outcome == Outcome.FAILED && isRetryable(error))
            repo.emitFinished()
        }
        return outcome
    }

    /** কাজ শেষের পরিষ্কার-পরিচ্ছন্নতা */
    private suspend fun release(id: Long, group: String, keepStage: Boolean = false) {
        repo.api.deleteStats(group)
        repo.clearStage(id)
        repo.clearDirProgress(id)
        repo.runningJobs.remove(id)
        repo.cancelRequested.remove(id)
        repo.pauseRequested.remove(id)
        if (!keepStage) File(applicationContext.cacheDir, "upload/$id").deleteRecursively()
    }

    /** সোর্স যদি ফোনের লোকাল ফাইল হয় তবে সেটি (আপলোডের অস্থায়ী কপি, ফোনের পথ, বা Local অ্যাকাউন্ট); নইলে null */
    /** গতির সেটিং: ক্লাউড-দিকের অ্যাকাউন্টের ধরন দেখে; বন্ধ থাকলে null (rclone-এর ডিফল্ট) */
    private suspend fun tuningFor(srcFs: String, dstFs: String): org.json.JSONObject? {
        if (!TransferTuning.isFast(applicationContext)) return TransferTuning.baseConfig()
        val remote = listOf(srcFs, dstFs)
            .firstOrNull { !it.startsWith("/") && it.contains(':') && !it.endsWith(":/") }
            ?.substringBefore(':')
        val type = remote?.let { r ->
            runCatching {
                (applicationContext as DriveMountApp).database.accountDao().getAll().firstOrNull { it.remoteName == r }?.backendType
            }.getOrNull()
        }
        return TransferTuning.config(type)
    }

    /** Home "Recent"-এর জন্য: ক্লাউডে ওঠা (UPLOADED) বা ক্লাউড থেকে নামা (DOWNLOADED) ফাইল */
    private suspend fun recordRecent(task: TransferEntity) {
        fun remoteOf(fs: String) = fs.takeIf { !it.startsWith("/") && it.contains(':') && !it.endsWith(":/") }?.substringBefore(':')
        val dao = (applicationContext as DriveMountApp).database.accountDao()
        val recent = (applicationContext as DriveMountApp).recent
        val dstRemote = remoteOf(task.dstFs)
        val srcRemote = remoteOf(task.srcFs)
        if (dstRemote != null) {
            val acc = dao.getAll().firstOrNull { it.remoteName == dstRemote } ?: return
            recent.record(com.drivemount.app.data.RecentStore.Kind.UPLOADED, acc.id, task.dstPath, task.name)
        } else if (srcRemote != null) {
            val acc = dao.getAll().firstOrNull { it.remoteName == srcRemote } ?: return
            recent.record(com.drivemount.app.data.RecentStore.Kind.DOWNLOADED, acc.id, task.srcPath, task.name)
        }
    }

    private fun localFileOf(fs: String, path: String): File? {
        val file = when {
            fs.startsWith("/") -> File(fs, path)
            fs.endsWith(":/") -> File("/$path")
            else -> return null
        }
        return file.takeIf { it.isFile }
    }

    private fun announceOpen(task: TransferEntity) {
        val file = File(task.dstFs, task.dstPath)
        if (!file.exists()) return
        val mime = guessMime(file.name)
        repo.emitOpen(OpenRequest(file.absolutePath, mime))
        TransferNotifications.openFile(applicationContext, file, mime)
    }

    /** বাছাই করা ফাইলের (content URI) অস্থায়ী কপি বানায়, কারণ rclone শুধু পথ থেকে পড়তে পারে */
    private suspend fun stageUpload(task: TransferEntity): File = withContext(Dispatchers.IO) {
        val dir = File(applicationContext.cacheDir, "upload/${task.id}").apply { mkdirs() }
        val safeName = task.name.replace('/', '_').ifBlank { "file" }
        val out = File(dir, safeName)
        // Process Kill-এর পরের চেষ্টা: আগে পুরো কপি হয়ে থাকলে (.part থেকে নাম বদল হয়েছিল) আবার কপি নয়
        val expected = runCatching {
            applicationContext.contentResolver.query(Uri.parse(task.sourceUri), arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()
        if (out.isFile && out.length() > 0 && (expected == null || expected == out.length())) return@withContext out
        val part = File(dir, "$safeName.part")
        val input = applicationContext.contentResolver.openInputStream(Uri.parse(task.sourceUri))
            ?: throw IOException("Cannot read the selected file")
        input.use { source ->
            part.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    if (task.id in repo.cancelRequested) throw UserCancelled()
                    val n = source.read(buffer)
                    if (n < 0) break
                    sink.write(buffer, 0, n)
                }
            }
        }
        if (expected != null && part.length() != expected) { part.delete(); throw IOException("Copy incomplete") }
        out.delete()
        if (!part.renameTo(out)) throw IOException("Could not finalize the staged copy")
        out
    }

    private fun progressText(stats: GroupStats): String =
        if (stats.totalBytes > 0) {
            "${formatBytes(stats.bytes)} / ${formatBytes(stats.totalBytes)}"
        } else {
            ""
        }

    /** পুরোনো অস্থায়ী ফাইল (খোলার জন্য নামানো, আপলোডের কপি) ২৪ ঘণ্টা পরে মুছে ফেলে */
    private fun cleanOldCache() {
        val limit = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        listOf("open", "upload", "saf", "fast_tmp").forEach { name ->
            File(applicationContext.cacheDir, name).listFiles()?.forEach {
                if (it.lastModified() < limit) it.deleteRecursively()
            }
        }
    }
}

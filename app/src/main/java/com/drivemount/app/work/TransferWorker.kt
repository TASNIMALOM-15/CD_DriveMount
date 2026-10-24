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
import com.drivemount.app.R
import com.drivemount.app.data.transfer.OpenRequest
import com.drivemount.app.data.transfer.TransferEntity
import com.drivemount.app.data.transfer.TransferStatus
import com.drivemount.app.engine.GroupStats
import com.drivemount.app.engine.fsJoin
import com.drivemount.app.engine.guessMime
import com.drivemount.app.ui.components.formatBytes
import kotlinx.coroutines.CancellationException
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

    private val repo get() = (applicationContext as DriveMountApp).transferRepository

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
        while (true) {
            val task = dao.nextQueued() ?: break
            when (runTask(task)) {
                Outcome.DONE -> ok++
                Outcome.FAILED -> failed++
                Outcome.CANCELLED -> Unit
            }
        }
        if (ok + failed > 0) TransferNotifications.summary(applicationContext, ok, failed)
        cleanOldCache()
        return Result.success()
    }

    private suspend fun runTask(task: TransferEntity): Outcome {
        val dao = repo.dao
        val api = repo.api
        val id = task.id
        val group = "dm-$id"
        var jobId: Long? = null
        var outcome = Outcome.FAILED
        var error: String? = null

        try {
            dao.setStatus(id, TransferStatus.PREPARING, null, null)

            var srcFs = task.srcFs
            var srcPath = task.srcPath
            if (task.sourceUri != null) {
                val staged = stageUpload(task)
                srcFs = staged.parentFile?.absolutePath ?: throw IOException("Could not prepare the file")
                srcPath = staged.name
            }
            if (id in repo.cancelRequested) throw UserCancelled()

            val job = if (task.isDir) {
                api.startDirJob(fsJoin(srcFs, srcPath), fsJoin(task.dstFs, task.dstPath), task.move, group, task.mirror)
            } else {
                api.startFileJob(srcFs, srcPath, task.dstFs, task.dstPath, task.move, group)
            }
            jobId = job
            repo.runningJobs[id] = job
            dao.setStatus(id, TransferStatus.RUNNING, null, null)

            var lastWrite = 0L
            while (true) {
                delay(500)
                val status = api.jobStatus(job)
                val stats = api.groupStats(group)
                val now = SystemClock.elapsedRealtime()
                if (status.finished || now - lastWrite >= 1000) {
                    lastWrite = now
                    dao.updateProgress(id, stats.bytes, stats.totalBytes, stats.speed.toLong())
                    TransferNotifications.update(
                        applicationContext,
                        TransferNotifications.ongoing(
                            applicationContext,
                            task.name,
                            progressText(stats),
                            stats.bytes,
                            stats.totalBytes,
                        ),
                    )
                }
                if (status.finished) {
                    if (status.success) {
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
                if (id in repo.cancelRequested) {
                    dao.setStatus(id, TransferStatus.CANCELLED, null, System.currentTimeMillis())
                } else {
                    dao.setStatus(id, TransferStatus.QUEUED, null, null)
                }
                release(id, group)
            }
            throw e
        } catch (e: UserCancelled) {
            outcome = Outcome.CANCELLED
        } catch (e: Exception) {
            outcome = if (id in repo.cancelRequested) Outcome.CANCELLED else Outcome.FAILED
            error = e.message ?: e.toString()
        }

        withContext(NonCancellable) {
            val now = System.currentTimeMillis()
            when (outcome) {
                Outcome.DONE -> {
                    dao.setDone(id, now)
                    if (task.openWhenDone) announceOpen(task)
                }
                Outcome.FAILED -> dao.setStatus(id, TransferStatus.FAILED, error ?: "Unknown error", now)
                Outcome.CANCELLED -> dao.setStatus(id, TransferStatus.CANCELLED, null, now)
            }
            task.profileId?.let { repo.syncDao.setResult(it, now, outcome.name, error) }
            release(id, group)
            repo.emitFinished()
        }
        return outcome
    }

    /** কাজ শেষের পরিষ্কার-পরিচ্ছন্নতা */
    private suspend fun release(id: Long, group: String) {
        repo.api.deleteStats(group)
        repo.runningJobs.remove(id)
        repo.cancelRequested.remove(id)
        File(applicationContext.cacheDir, "upload/$id").deleteRecursively()
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
        val input = applicationContext.contentResolver.openInputStream(Uri.parse(task.sourceUri))
            ?: throw IOException("Cannot read the selected file")
        input.use { source ->
            out.outputStream().use { sink ->
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
        listOf("open", "upload", "saf").forEach { name ->
            File(applicationContext.cacheDir, name).listFiles()?.forEach {
                if (it.lastModified() < limit) it.deleteRecursively()
            }
        }
    }
}

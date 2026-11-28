package com.drivemount.app.data.transfer

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.work.TransferWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** আপলোড-পূর্ব ধাপ (শুধু UI/নোটিফিকেশনে দেখানোর জন্য, ডাটাবেসে নয়) */
object TransferStage {
    const val WORKING = 0
    const val THUMB = 1
    const val SEEK = 2
    const val FAST = 3
    const val UPLOAD = 4
}

/** নতুন ট্রান্সফার কাজের অনুরোধ */
data class NewTransfer(
    val name: String,
    val isDir: Boolean,
    val move: Boolean,
    val srcFs: String,
    val srcPath: String,
    val dstFs: String,
    val dstPath: String,
    val sourceUri: String? = null,
    val openWhenDone: Boolean = false,
    val mirror: Boolean = false,
    val profileId: Long? = null,
)

/** ডাউনলোড শেষ হওয়া ফাইল খোলার অনুরোধ */
data class OpenRequest(val path: String, val mime: String)

/** ট্রান্সফারের লাইন, অবস্থা ও বাতিল ব্যবস্থাপনা; আসল কাজ করে TransferWorker */
class TransferRepository(
    private val context: Context,
    internal val dao: TransferDao,
    internal val syncDao: SyncProfileDao,
    internal val api: RcloneApi,
) {
    val transfers: Flow<List<TransferEntity>> = dao.observeAll()
    val activeCount: Flow<Int> = dao.observeActiveCount()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** কোনো কাজ শেষ হলে (তালিকা নতুন করে আনার সংকেত) */
    val finished: SharedFlow<Unit> = _finished

    private val _openRequests = MutableSharedFlow<OpenRequest>(extraBufferCapacity = 4)
    val openRequests: SharedFlow<OpenRequest> = _openRequests

    /** ফোল্ডার-কাজের ফাইল-পিছু অগ্রগতি (taskId -> ...); ফোল্ডার ব্রাউজে ফাইলগুলো ধূসর থেকে রঙিন দেখাতে */
    class DirProgress(val active: Map<String, Pair<Long, Long>>, val done: Set<String>)

    private val _dirProgress = MutableStateFlow<Map<Long, DirProgress>>(emptyMap())
    val dirProgress: StateFlow<Map<Long, DirProgress>> = _dirProgress

    internal fun setDirProgress(id: Long, p: DirProgress) { _dirProgress.update { it + (id to p) } }
    internal fun clearDirProgress(id: Long) { _dirProgress.update { if (id in it) it - id else it } }

    private val _stages = MutableStateFlow<Map<Long, Int>>(emptyMap())
    /** taskId -> বর্তমান ধাপ ([TransferStage]) */
    val stages: StateFlow<Map<Long, Int>> = _stages

    internal fun setStage(id: Long, stage: Int) { _stages.update { if (it[id] == stage) it else it + (id to stage) } }
    internal fun clearStage(id: Long) { _stages.update { it - id } }

    /** চলমান কাজ: taskId -> rclone jobId */
    internal val runningJobs = ConcurrentHashMap<Long, Long>()

    /** যেসব কাজ বাতিল করতে বলা হয়েছে */
    internal val cancelRequested: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    /** cancelRequested-এর মধ্যে যেগুলো আসলে "Pause" (শেষে PAUSED হবে, CANCELLED নয়) */
    internal val pauseRequested: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    suspend fun enqueue(items: List<NewTransfer>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        items.forEachIndexed { index, t ->
            dao.insert(
                TransferEntity(
                    name = t.name,
                    isDir = t.isDir,
                    move = t.move,
                    srcFs = t.srcFs,
                    srcPath = t.srcPath,
                    dstFs = t.dstFs,
                    dstPath = t.dstPath,
                    sourceUri = t.sourceUri,
                    openWhenDone = t.openWhenDone,
                    mirror = t.mirror,
                    profileId = t.profileId,
                    createdAt = now + index, // তালিকার ক্রম ঠিক রাখতে
                ),
            )
        }
        schedule()
    }

    /** ব্যাকগ্রাউন্ড কর্মী চালু করে। আগেরটি চললে তার পরে চলে (একসঙ্গে একটিই)। */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<TransferWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun hasActiveForProfile(profileId: Long): Boolean = dao.countActiveForProfile(profileId) > 0

    suspend fun resumeIfNeeded() {
        if (dao.countPending() > 0) schedule()
    }

    suspend fun cancel(id: Long) {
        val task = dao.getById(id) ?: return
        when (task.status) {
            TransferStatus.QUEUED ->
                dao.setStatus(id, TransferStatus.CANCELLED, null, System.currentTimeMillis())

            TransferStatus.PREPARING, TransferStatus.RUNNING -> {
                cancelRequested += id
                runningJobs[id]?.let { api.stopJob(it) }
                // সঙ্গে সঙ্গে "Cancelled" দেখানো; কর্মী পেছনে প্রস্তুতি থামিয়ে সাফ করবে
                if (task.status == TransferStatus.PREPARING) {
                    dao.setStatus(id, TransferStatus.CANCELLED, null, System.currentTimeMillis())
                    clearStage(id)
                }
            }
        }
    }

    /** থামানো: চলতি কাজ থামে (পুনরায় শুরু করলে ফাইলটি আবার প্রথম থেকে), লাইনের কাজ অপেক্ষায় থাকে */
    suspend fun pause(id: Long) {
        val task = dao.getById(id) ?: return
        when (task.status) {
            TransferStatus.QUEUED -> dao.setStatus(id, TransferStatus.PAUSED, null, null)
            TransferStatus.PREPARING, TransferStatus.RUNNING -> {
                pauseRequested += id
                cancelRequested += id
                runningJobs[id]?.let { api.stopJob(it) }
                if (task.status == TransferStatus.PREPARING) {
                    dao.setStatus(id, TransferStatus.PAUSED, null, null)
                    clearStage(id)
                }
            }
        }
    }

    suspend fun resume(id: Long) {
        if (dao.getById(id)?.status != TransferStatus.PAUSED) return
        dao.requeue(id)
        schedule()
    }

    suspend fun pauseAll() = dao.getActive().forEach { pause(it.id) }

    suspend fun resumeAll() = dao.getPaused().forEach { resume(it.id) }

    suspend fun retry(id: Long) {
        dao.requeue(id)
        schedule()
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun clearFinished() = dao.deleteFinished()

    /** কোনো ফোল্ডারে আগে থেকে থাকা নামগুলো (না থাকলে খালি) */
    suspend fun existingNames(fs: String, path: String): Set<String> =
        runCatching { api.list(fs, path).map { it.name }.toSet() }.getOrDefault(emptySet())

    internal fun emitFinished() {
        _finished.tryEmit(Unit)
    }

    internal fun emitOpen(request: OpenRequest) {
        _openRequests.tryEmit(request)
    }

    private companion object {
        const val UNIQUE_NAME = "drivemount-transfers"
    }
}

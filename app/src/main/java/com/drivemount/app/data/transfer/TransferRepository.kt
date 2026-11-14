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
import java.util.concurrent.ConcurrentHashMap

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

    /** চলমান কাজ: taskId -> rclone jobId */
    internal val runningJobs = ConcurrentHashMap<Long, Long>()

    /** যেসব কাজ বাতিল করতে বলা হয়েছে */
    internal val cancelRequested: MutableSet<Long> = ConcurrentHashMap.newKeySet()

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
            }
        }
    }

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

package com.drivemount.app.data.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.drivemount.app.data.AccountRepository
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.work.SyncWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/** Sync প্রোফাইল সংরক্ষণ, চালানো ও স্বয়ংক্রিয় সময়সূচি */
class SyncRepository(
    private val context: Context,
    private val dao: SyncProfileDao,
    private val accounts: AccountRepository,
    private val transfers: TransferRepository,
) {
    val profiles: Flow<List<SyncProfileEntity>> = dao.observeAll()

    suspend fun get(id: Long): SyncProfileEntity? = dao.getById(id)

    suspend fun save(profile: SyncProfileEntity): Long {
        val id = if (profile.id == 0L) {
            dao.insert(profile)
        } else {
            dao.update(profile)
            profile.id
        }
        schedule(profile.copy(id = id))
        return id
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    /** এখনই চালায় (ট্রান্সফার লাইনে দেয়)। আগের রান চললে নতুন করে দেয় না। */
    suspend fun runNow(id: Long): Result<Unit> = runCatching {
        val p = dao.getById(id) ?: error("Sync profile not found")
        if (transfers.hasActiveForProfile(id)) return@runCatching
        val src = accounts.getById(p.srcAccountId) ?: error("Source account no longer exists")
        val dst = accounts.getById(p.dstAccountId) ?: error("Destination account no longer exists")
        transfers.enqueue(
            listOf(
                NewTransfer(
                    name = p.name,
                    isDir = true,
                    move = p.mode == SyncMode.MOVE,
                    mirror = p.mode == SyncMode.MIRROR,
                    srcFs = fsRootFor(src.remoteName, src.backendType),
                    srcPath = p.srcPath,
                    dstFs = fsRootFor(dst.remoteName, dst.backendType),
                    dstPath = p.dstPath,
                    profileId = p.id,
                ),
            ),
        )
    }

    fun schedule(p: SyncProfileEntity) {
        val manager = WorkManager.getInstance(context)
        if (p.intervalMinutes <= 0) {
            manager.cancelUniqueWork(workName(p.id))
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(p.intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to p.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(workName(p.id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun workName(id: Long) = "sync-$id"
}

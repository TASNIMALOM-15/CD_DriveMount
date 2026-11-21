package com.drivemount.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.work.BackfillWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * backfill-এর অবস্থা। চলার সময়: ধাপ, মোট/হয়েছে/ব্যর্থ, এখন কোন ফাইল, ধরন-অনুযায়ী গণনা, কত সময় বাকি।
 * শেষে: শেষবারের সারাংশ (কতগুলো হলো, ব্যর্থ কোনগুলো, কত সময় লাগল, আরও বাকি আছে কি না)।
 */
data class BackfillState(
    val active: Boolean = false,
    val waiting: Boolean = false,
    val phase: Int = 0,
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val cur: String = "",
    val etaSec: Long = -1L,
    val elapsedSec: Long = 0L,
    /** [ছবি done,total, ভিডিও done,total, গান done,total, অ্যাপ done,total] */
    val cats: List<Int> = List(8) { 0 },
    // শেষবারের ফল
    val hasLast: Boolean = false,
    val lastDone: Int = 0,
    val lastTotal: Int = 0,
    val lastFailed: Int = 0,
    val lastSkipped: Int = 0,
    val lastPartial: Boolean = false,
    val lastElapsedSec: Long = 0L,
    val lastFailedNames: String = "",
)

class BackfillViewModel(app: Application) : AndroidViewModel(app) {

    private val dm = app as DriveMountApp
    private val work = WorkManager.getInstance(app)

    val accounts: StateFlow<List<AccountEntity>> = dm.database.accountDao().observeAll()
        .map { list -> list.filter { it.backendType != "local" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<BackfillState> = work.getWorkInfosForUniqueWorkFlow(BackfillWorker.UNIQUE_NAME)
        .map { infos ->
            val w = infos.firstOrNull { !it.state.isFinished }
            val last = infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }?.outputData
            val lastFields = BackfillState(
                hasLast = last != null && last.keyValueMap.containsKey(BackfillWorker.KEY_TOTAL),
                lastDone = last?.getInt(BackfillWorker.KEY_DONE, 0) ?: 0,
                lastTotal = last?.getInt(BackfillWorker.KEY_TOTAL, 0) ?: 0,
                lastFailed = last?.getInt(BackfillWorker.KEY_FAILED, 0) ?: 0,
                lastSkipped = last?.getInt(BackfillWorker.KEY_SKIPPED, 0) ?: 0,
                lastPartial = last?.getBoolean(BackfillWorker.KEY_PARTIAL, false) ?: false,
                lastElapsedSec = last?.getLong(BackfillWorker.KEY_ELAPSED, 0L) ?: 0L,
                lastFailedNames = last?.getString(BackfillWorker.KEY_FAILED_NAMES).orEmpty(),
            )
            when {
                w == null -> lastFields
                w.state == WorkInfo.State.RUNNING -> {
                    val p = w.progress
                    lastFields.copy(
                        active = true,
                        phase = p.getInt(BackfillWorker.KEY_PHASE, 0),
                        total = p.getInt(BackfillWorker.KEY_TOTAL, 0),
                        done = p.getInt(BackfillWorker.KEY_DONE, 0),
                        failed = p.getInt(BackfillWorker.KEY_FAILED, 0),
                        cur = p.getString(BackfillWorker.KEY_CUR).orEmpty(),
                        etaSec = p.getLong(BackfillWorker.KEY_ETA, -1L),
                        elapsedSec = p.getLong(BackfillWorker.KEY_ELAPSED, 0L),
                        cats = p.getIntArray(BackfillWorker.KEY_CATS)?.toList() ?: List(8) { 0 },
                    )
                }
                else -> lastFields.copy(active = true, waiting = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackfillState())

    /** ব্যবহারকারী ডায়ালগে "শুরু করো" চাপলে তবেই ডাকা হয় */
    fun start(accountId: Long, onlyWhileCharging: Boolean) {
        val request = OneTimeWorkRequestBuilder<BackfillWorker>()
            .setInputData(workDataOf(BackfillWorker.KEY_ACCOUNT to accountId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(onlyWhileCharging)
                    .build(),
            )
            .build()
        work.enqueueUniqueWork(BackfillWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun stop() {
        work.cancelUniqueWork(BackfillWorker.UNIQUE_NAME)
    }
}

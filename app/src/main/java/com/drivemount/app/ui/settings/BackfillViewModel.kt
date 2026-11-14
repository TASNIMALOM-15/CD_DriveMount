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

/** backfill-এর অবস্থা: কাজ চলছে/অপেক্ষায় (Wi-Fi/চার্জের শর্ত) কি না ও কতগুলো হয়েছে */
data class BackfillState(val active: Boolean = false, val waiting: Boolean = false, val done: Int = 0)

class BackfillViewModel(app: Application) : AndroidViewModel(app) {

    private val dm = app as DriveMountApp
    private val work = WorkManager.getInstance(app)

    val accounts: StateFlow<List<AccountEntity>> = dm.database.accountDao().observeAll()
        .map { list -> list.filter { it.backendType != "local" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<BackfillState> = work.getWorkInfosForUniqueWorkFlow(BackfillWorker.UNIQUE_NAME)
        .map { infos ->
            val w = infos.firstOrNull { !it.state.isFinished }
            when {
                w == null -> BackfillState(done = infos.firstOrNull()?.outputData?.getInt(BackfillWorker.KEY_DONE, 0) ?: 0)
                w.state == WorkInfo.State.RUNNING -> BackfillState(true, false, w.progress.getInt(BackfillWorker.KEY_DONE, 0))
                else -> BackfillState(true, true, 0)
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

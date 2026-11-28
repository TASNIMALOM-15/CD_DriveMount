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
import com.drivemount.app.work.IndexWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** পুরো-ড্রাইভ ইনডেক্সের অবস্থা (BackfillState-এর মতো) */
data class IndexState(val active: Boolean = false, val waiting: Boolean = false, val folders: Int = 0, val partial: Boolean = false)

class IndexViewModel(app: Application) : AndroidViewModel(app) {

    private val dm = app as DriveMountApp
    private val work = WorkManager.getInstance(app)

    val accounts: StateFlow<List<AccountEntity>> = dm.database.accountDao().observeAll()
        .map { list -> list.filter { it.backendType != "local" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<IndexState> = work.getWorkInfosForUniqueWorkFlow(IndexWorker.UNIQUE_NAME)
        .map { infos ->
            val w = infos.firstOrNull { !it.state.isFinished }
            when {
                w == null -> {
                    val o = infos.firstOrNull()?.outputData
                    IndexState(folders = o?.getInt(IndexWorker.KEY_DONE, 0) ?: 0, partial = o?.getBoolean(IndexWorker.KEY_PARTIAL, false) ?: false)
                }
                w.state == WorkInfo.State.RUNNING -> IndexState(true, false, w.progress.getInt(IndexWorker.KEY_DONE, 0))
                else -> IndexState(true, true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexState())

    /** accountId = IndexWorker.ALL হলে সব অ্যাকাউন্ট; ডায়ালগে ব্যবহারকারী "শুরু করো" চাপলেই ডাকা হয় */
    fun start(accountId: Long) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(workDataOf(IndexWorker.KEY_ACCOUNT to accountId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        work.enqueueUniqueWork(IndexWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun stop() {
        work.cancelUniqueWork(IndexWorker.UNIQUE_NAME)
    }
}

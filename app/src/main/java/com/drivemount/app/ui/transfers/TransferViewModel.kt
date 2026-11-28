package com.drivemount.app.ui.transfers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.transfer.TransferEntity
import com.drivemount.app.data.meta.SidecarManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).transferRepository

    val transfers: StateFlow<List<TransferEntity>> =
        repo.transfers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stages: StateFlow<Map<Long, Int>> = repo.stages

    private val sidecar = (app as DriveMountApp).sidecar

    /** ক্লাউডে যেতে বাকি/ব্যর্থ প্রিভিউ-ফাইলের সংখ্যা (স্ক্রিন খোলা থাকলে ২ সেকেন্ড পরপর হালনাগাদ) */
    val backlog: StateFlow<SidecarManager.Backlog> = flow {
        while (true) { emit(sidecar.backlog()); delay(2_000) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SidecarManager.Backlog(0, 0))

    fun retryPreviews() = sidecar.retryFailed()

    fun cancel(id: Long) {
        viewModelScope.launch { repo.cancel(id) }
    }

    fun pause(id: Long) {
        viewModelScope.launch { repo.pause(id) }
    }

    fun resume(id: Long) {
        viewModelScope.launch { repo.resume(id) }
    }

    fun pauseAll() {
        viewModelScope.launch { repo.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { repo.resumeAll() }
    }

    fun retry(id: Long) {
        viewModelScope.launch { repo.retry(id) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repo.remove(id) }
    }

    fun clearFinished() {
        viewModelScope.launch { repo.clearFinished() }
    }
}

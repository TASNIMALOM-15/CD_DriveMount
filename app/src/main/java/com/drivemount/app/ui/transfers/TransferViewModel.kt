package com.drivemount.app.ui.transfers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.transfer.TransferEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).transferRepository

    val transfers: StateFlow<List<TransferEntity>> =
        repo.transfers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: Long) {
        viewModelScope.launch { repo.cancel(id) }
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

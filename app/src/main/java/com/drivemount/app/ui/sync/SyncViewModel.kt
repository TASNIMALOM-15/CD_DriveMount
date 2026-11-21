package com.drivemount.app.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.transfer.SyncProfileEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp
    private val repo = appRef.syncRepository

    val profiles: StateFlow<List<SyncProfileEntity>> =
        repo.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** অ্যাকাউন্ট id -> দেখানোর নাম */
    val accountNames: StateFlow<Map<Long, String>> =
        appRef.accountRepository.accounts
            .map { list -> list.associate { it.id to it.displayName } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun runNow(id: Long) {
        viewModelScope.launch {
            repo.runNow(id).fold(
                onSuccess = { _events.send(getApplication<Application>().getString(R.string.sync_started)) },
                onFailure = { _events.send(it.message ?: it.toString()) },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}

package com.drivemount.app.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).accountRepository

    /** চলমান বা অপেক্ষারত ট্রান্সফারের সংখ্যা (Home-এর ব্যাজের জন্য) */
    val activeTransfers: StateFlow<Int> = (app as DriveMountApp).transferRepository.activeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** null = এখনও লোড হচ্ছে */
    val accounts: StateFlow<List<AccountEntity>?> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                repo.reconcile()
            } catch (t: Throwable) {
                _events.send(t.message ?: t.toString())
            }
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }

    fun delete(account: AccountEntity) {
        viewModelScope.launch {
            repo.delete(account).onFailure { _events.send(it.message ?: it.toString()) }
        }
    }

    fun refreshUsage(account: AccountEntity) {
        viewModelScope.launch {
            _refreshing.update { it + account.id }
            repo.refreshUsage(account).onFailure { _events.send(it.message ?: it.toString()) }
            _refreshing.update { it - account.id }
        }
    }
}

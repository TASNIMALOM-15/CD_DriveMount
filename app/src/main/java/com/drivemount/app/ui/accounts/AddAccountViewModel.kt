package com.drivemount.app.ui.accounts

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.NewAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface AddState {
    data object Idle : AddState
    data object Saving : AddState

    /** browser-এ সাইন-ইন চলছে; url জানা গেলে সেটি থাকে */
    data class WaitingForBrowser(val url: String?) : AddState
    data object Done : AddState
    data class Error(val message: String) : AddState
}

class AddAccountViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).accountRepository

    private val _state = MutableStateFlow<AddState>(AddState.Idle)
    val state: StateFlow<AddState> = _state

    /** UI যখন শোনে, browser খোলার URL এখানে আসে */
    private val _openUrl = Channel<String>(Channel.BUFFERED)
    val openUrl = _openUrl.receiveAsFlow()

    private var cancelRequested = false
    private var timedOut = false

    fun add(account: NewAccount) {
        val current = _state.value
        if (current is AddState.Saving || current is AddState.WaitingForBrowser) return

        cancelRequested = false
        timedOut = false
        _state.value = if (account.oauth) AddState.WaitingForBrowser(null) else AddState.Saving

        viewModelScope.launch {
            val watcher = if (account.oauth) launch { watchForAuthUrl() } else null
            val result = repo.addAccount(account)
            watcher?.cancel()

            _state.value = result.fold(
                onSuccess = { AddState.Done },
                onFailure = {
                    when {
                        cancelRequested -> AddState.Idle
                        timedOut -> AddState.Error(
                            getApplication<Application>().getString(R.string.add_timeout),
                        )
                        else -> AddState.Error(it.message ?: it.toString())
                    }
                },
            )
        }
    }

    /** rclone-এর সাইন-ইন server চালু হলেই URL নিয়ে browser খুলতে বলা হয় */
    private suspend fun watchForAuthUrl() {
        val start = SystemClock.elapsedRealtime()
        var opened = false
        while (true) {
            delay(300)
            val url = repo.oauthAuthUrl()
            if (url != null && !opened) {
                opened = true
                _state.value = AddState.WaitingForBrowser(url)
                _openUrl.send(url)
            }
            if (SystemClock.elapsedRealtime() - start > SIGN_IN_TIMEOUT_MS) {
                timedOut = true
                repo.cancelOAuth()
                break
            }
        }
    }

    fun cancel() {
        if (_state.value !is AddState.WaitingForBrowser) return
        cancelRequested = true
        viewModelScope.launch { repo.cancelOAuth() }
    }

    override fun onCleared() {
        if (_state.value is AddState.WaitingForBrowser) {
            CoroutineScope(Dispatchers.IO).launch { repo.cancelOAuth() }
        }
        super.onCleared()
    }

    private companion object {
        const val SIGN_IN_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

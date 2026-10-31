package com.drivemount.app.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.transfer.SyncMode
import com.drivemount.app.data.transfer.SyncProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** কোনো অ্যাকাউন্টের একটি ফোল্ডার */
data class Location(val accountId: Long, val path: String)

data class EditState(
    val name: String = "",
    val src: Location? = null,
    val dst: Location? = null,
    val mode: String = SyncMode.COPY,
    val interval: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

class EditSyncViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp
    private val repo = appRef.syncRepository
    private val profileId: Long = handle.get<Long>("id") ?: 0L
    private var existing: SyncProfileEntity? = null

    val accounts: StateFlow<List<AccountEntity>> =
        appRef.accountRepository.accounts
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    init {
        if (profileId != 0L) {
            viewModelScope.launch {
                val p = repo.get(profileId) ?: return@launch
                existing = p
                _state.value = EditState(
                    name = p.name,
                    src = Location(p.srcAccountId, p.srcPath),
                    dst = Location(p.dstAccountId, p.dstPath),
                    mode = p.mode,
                    interval = p.intervalMinutes,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setSrc(v: Location) = _state.update { it.copy(src = v, error = null) }
    fun setDst(v: Location) = _state.update { it.copy(dst = v, error = null) }
    fun setMode(v: String) = _state.update { it.copy(mode = v) }
    fun setInterval(v: Int) = _state.update { it.copy(interval = v) }

    private fun str(id: Int) = getApplication<Application>().getString(id)

    /** একই অ্যাকাউন্টে একটি ফোল্ডার অন্যটির ভেতরে (বা একই) হলে true */
    private fun overlaps(a: Location, b: Location): Boolean {
        if (a.accountId != b.accountId) return false
        if (a.path.isEmpty() || b.path.isEmpty()) return true
        return a.path == b.path || a.path.startsWith(b.path + "/") || b.path.startsWith(a.path + "/")
    }

    fun save() {
        val s = _state.value
        val src = s.src
        val dst = s.dst
        if (s.name.isBlank() || src == null || dst == null) {
            _state.update { it.copy(error = str(R.string.sync_err_incomplete)) }
            return
        }
        if (overlaps(src, dst)) {
            _state.update { it.copy(error = str(R.string.sync_err_overlap)) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val old = existing
            repo.save(
                SyncProfileEntity(
                    id = old?.id ?: 0L,
                    name = s.name.trim(),
                    srcAccountId = src.accountId,
                    srcPath = src.path,
                    dstAccountId = dst.accountId,
                    dstPath = dst.path,
                    mode = s.mode,
                    intervalMinutes = s.interval,
                    lastRunAt = old?.lastRunAt,
                    lastStatus = old?.lastStatus,
                    lastMessage = old?.lastMessage,
                    createdAt = old?.createdAt ?: System.currentTimeMillis(),
                ),
            )
            _state.update { it.copy(saving = false, done = true) }
        }
    }
}

package com.drivemount.app.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home-এর একটি কার্ড: একটি ক্লাউড (বা ফোনের স্টোরেজ) আর তার ভেতরের এনক্রিপ্টেড ভল্টগুলো */
data class AccountGroup(val base: AccountEntity, val vaults: List<AccountEntity>)

class AccountsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).accountRepository
    private val rclone = (app as DriveMountApp).rcloneApi

    /** চলমান বা অপেক্ষারত ট্রান্সফারের সংখ্যা (Home-এর ব্যাজের জন্য) */
    val activeTransfers: StateFlow<Int> = (app as DriveMountApp).transferRepository.activeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** null = এখনও লোড হচ্ছে */
    val accounts: StateFlow<List<AccountEntity>?> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * ভল্ট (crypt) যে অ্যাকাউন্টের ভেতরে বানানো, তার নিচেই দেখানোর জন্য গোছানো তালিকা।
     * কোন ভল্ট কোন অ্যাকাউন্টের তা rclone.conf-এর "remote" মান থেকে জানা যায়।
     * null = এখনও লোড হচ্ছে
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: StateFlow<List<AccountGroup>?> = repo.accounts
        .mapLatest { list -> buildGroups(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun buildGroups(list: List<AccountEntity>): List<AccountGroup> {
        val crypts = list.filter { it.backendType == "crypt" }
        if (crypts.isEmpty()) return list.map { AccountGroup(it, emptyList()) }

        val config = runCatching { rclone.dumpConfig() }.getOrDefault(emptyMap())
        val byRemote = list.associateBy { it.remoteName }

        // ভল্টের ওপরের (মূল) অ্যাকাউন্ট: crypt-এর ওপর crypt থাকলেও শেষ পর্যন্ত গিয়ে মূলটি ধরা হয়
        fun baseOf(vault: AccountEntity): AccountEntity? {
            var current = vault
            repeat(4) {
                val parentName = config[current.remoteName]?.optString("remote")?.substringBefore(':')
                val parent = parentName?.let { byRemote[it] } ?: return null
                if (parent.backendType != "crypt") return parent
                current = parent
            }
            return null
        }

        val vaultsByBase = LinkedHashMap<Long, MutableList<AccountEntity>>()
        val orphans = mutableSetOf<Long>()
        crypts.forEach { vault ->
            val base = baseOf(vault)
            if (base == null) orphans += vault.id else vaultsByBase.getOrPut(base.id) { mutableListOf() }.add(vault)
        }
        return list.filter { it.backendType != "crypt" || it.id in orphans }
            .map { AccountGroup(it, vaultsByBase[it.id].orEmpty()) }
    }

    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // ব্যবহারের তথ্য নেই এমন অ্যাকাউন্ট (import-করা সহ) নিজে থেকে হালনাগাদ হয়; পুরোনো তথ্যও ৬ ঘণ্টা পরপর
        viewModelScope.launch { runCatching { repo.keepUsageFilled() } }
        viewModelScope.launch { runCatching { repo.refreshStaleUsage(6L * 60 * 60 * 1000) } }
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

    /** অ্যাকাউন্ট মোছে; তার ভেতরের ভল্টগুলোও আগে মোছা হয় (নইলে সেগুলো অচল হয়ে পড়ে থাকত) */
    fun delete(account: AccountEntity, vaults: List<AccountEntity> = emptyList()) {
        viewModelScope.launch {
            vaults.forEach { v -> repo.delete(v).onFailure { _events.send(it.message ?: it.toString()) } }
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

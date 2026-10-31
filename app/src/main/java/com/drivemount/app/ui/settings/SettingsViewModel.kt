package com.drivemount.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.ui.theme.ColorSource
import com.drivemount.app.ui.theme.DEFAULT_SEED
import com.drivemount.app.ui.theme.ThemeMode
import com.drivemount.app.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).themeRepository

    private val accountRepo = (app as DriveMountApp).accountRepository

    val settings: StateFlow<ThemeSettings> = repo.settings

    init {
        // Settings আগে খুললেও (import-এর পর) ব্যবহারের তথ্য নিজে থেকে আসে
        viewModelScope.launch { runCatching { accountRepo.keepUsageFilled() } }
    }

    /** মোট স্টোরেজে গোনা অ্যাকাউন্ট: ক্লাউড ও ফোনের স্টোরেজ (ভল্ট বাদে, কারণ ভল্ট ক্লাউডের ভেতরেই) */
    val cloudAccounts: StateFlow<List<AccountEntity>> = accountRepo.accounts
        .map { list -> list.filter { it.backendType != "crypt" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** সব ক্লাউডের ব্যবহার নতুন করে আনে; কোনোটি ব্যর্থ হলেও বাকিগুলো চলে */
    fun refreshUsage() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            cloudAccounts.value.forEach { runCatching { accountRepo.refreshUsage(it) } }
            _refreshing.value = false
        }
    }

    fun setMode(mode: ThemeMode) {
        repo.update { it.copy(mode = mode) }
    }

    fun setMaterialColor(argb: Int) {
        repo.update { it.copy(source = ColorSource.MATERIAL, seedColor = argb) }
    }

    fun setCustomColor(argb: Int) {
        repo.update { it.copy(source = ColorSource.CUSTOM, seedColor = argb) }
    }

    fun setOriginalIcons(on: Boolean) {
        repo.update { it.copy(originalIcons = on) }
    }

    fun setThumbnails(on: Boolean) {
        repo.update { it.copy(thumbnails = on) }
    }

    /** Clear/Reset: রং আবার ডিফল্ট (Material You) */
    fun resetColor() {
        repo.update { it.copy(source = ColorSource.DEFAULT, seedColor = DEFAULT_SEED) }
    }
}

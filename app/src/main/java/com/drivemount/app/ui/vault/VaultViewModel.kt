package com.drivemount.app.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.NewAccount
import com.drivemount.app.engine.LocalPaths
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultUiState {
    data object Idle : VaultUiState
    data object Working : VaultUiState
    data class ShowRecovery(val code: String) : VaultUiState
    data object Done : VaultUiState
    data class Error(val message: String) : VaultUiState
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp

    /** ভল্ট যে ক্লাউড অ্যাকাউন্টের ভেতরে থাকবে তার তালিকা (ফোনের স্টোরেজ আলাদা অপশন) */
    val baseAccounts: StateFlow<List<AccountEntity>> =
        appRef.accountRepository.accounts
            .map { list -> list.filter { it.backendType != "crypt" && it.backendType != "local" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<VaultUiState>(VaultUiState.Idle)
    val state: StateFlow<VaultUiState> = _state

    private fun str(id: Int) = getApplication<Application>().getString(id)

    /** নতুন ভল্ট। generate = true হলে র‍্যান্ডম recovery code বানিয়ে শেষে দেখানো হয়। */
    fun create(name: String, base: AccountEntity?, folder: String, generate: Boolean, ownPassword: String) {
        if (generate) {
            val code = VaultKeys.generateRecoveryCode()
            val secret = VaultKeys.normalizeRecoveryCode(code) ?: return
            submit(name, base, folder, secret, createRoot = true, showCode = code)
        } else {
            submit(name, base, folder, ownPassword, createRoot = true, showCode = null)
        }
    }

    /** আগে বানানো ভল্ট খোলা (recovery code বা পাসওয়ার্ড দিয়ে) */
    fun open(name: String, base: AccountEntity?, folder: String, useRecoveryCode: Boolean, input: String) {
        val secret = if (useRecoveryCode) {
            VaultKeys.normalizeRecoveryCode(input)
        } else {
            input.takeIf { it.isNotEmpty() }
        }
        if (secret == null) {
            _state.value = VaultUiState.Error(str(R.string.vault_bad_code))
            return
        }
        submit(name, base, folder, secret, createRoot = false, showCode = null)
    }

    fun finish() {
        _state.value = VaultUiState.Done
    }

    fun clearError() {
        if (_state.value is VaultUiState.Error) _state.value = VaultUiState.Idle
    }

    /** base == null মানে ফোনের স্টোরেজ (Local) */
    private fun submit(
        name: String,
        base: AccountEntity?,
        folder: String,
        secret: String,
        createRoot: Boolean,
        showCode: String?,
    ) {
        if (_state.value == VaultUiState.Working) return
        _state.value = VaultUiState.Working
        viewModelScope.launch {
            val result = runCatching {
                // ভল্টের চাবি কনফিগে থাকে, তাই আগে কনফিগ সুরক্ষিত করা হয়
                appRef.configProtector.ensureEnabled().getOrThrow()

                val (password, salt) = withContext(Dispatchers.Default) { VaultKeys.derive(secret) }
                val rawFs = if (base == null) {
                    localVaultFs(ensureLocalAccount(), folder)
                } else {
                    "${base.remoteName}:${folder.trim().trim('/')}"
                }

                val id = appRef.accountRepository.addAccount(
                    NewAccount(
                        displayName = name,
                        type = "crypt",
                        params = mapOf(
                            "remote" to rawFs,
                            "password" to password,
                            "password2" to salt,
                            "filename_encryption" to "standard",
                            "directory_name_encryption" to "true",
                        ),
                        obscure = true,
                        makeRoot = createRoot,
                    ),
                ).getOrThrow()

                if (!createRoot) checkOpened(id, rawFs)
            }
            _state.value = result.fold(
                onSuccess = { if (showCode != null) VaultUiState.ShowRecovery(showCode) else VaultUiState.Done },
                onFailure = { VaultUiState.Error(it.message ?: it.toString()) },
            )
        }
    }

    /** ফোনের স্টোরেজের (Local) অ্যাকাউন্ট; না থাকলে নীরবে বানানো হয় */
    private suspend fun ensureLocalAccount(): AccountEntity {
        val repo = appRef.accountRepository
        repo.getAll().firstOrNull { it.backendType == "local" }?.let { return it }
        val id = repo.addAccount(
            NewAccount(displayName = str(R.string.provider_local), type = "local"),
        ).getOrThrow()
        return repo.getById(id) ?: throw IllegalStateException(str(R.string.vault_local_failed))
    }

    /**
     * ফোনের স্টোরেজে ভল্ট ফোল্ডারের rclone পথ।
     * "/" দিয়ে শুরু হলে পূর্ণ পথ (যেমন /storage/XXXX-XXXX/Vault), নইলে অভ্যন্তরীণ স্টোরেজের ভেতরে।
     */
    private fun localVaultFs(local: AccountEntity, folder: String): String {
        val input = folder.trim()
        val relative = if (input.startsWith("/")) {
            LocalPaths.resolveToRelative(input)
        } else {
            joinPath(LocalPaths.internalRelative(), input.trim('/'))
        }
        if (relative.isNullOrBlank()) throw IllegalStateException(str(R.string.vault_local_bad_path))
        return "${local.remoteName}:/$relative"
    }

    /** ভল্ট ফোল্ডারে ফাইল আছে অথচ ডিক্রিপ্ট করে কিছুই দেখা যাচ্ছে না: ভুল recovery code বা পাসওয়ার্ড */
    private suspend fun checkOpened(id: Long, rawFs: String) {
        val account = appRef.accountRepository.getById(id) ?: return
        val raw = try {
            appRef.rcloneApi.list(rawFs, "")
        } catch (e: Exception) {
            appRef.accountRepository.delete(account)
            throw IllegalStateException(str(R.string.vault_not_found))
        }
        val decrypted = runCatching { appRef.rcloneApi.list("${account.remoteName}:", "") }
            .getOrDefault(emptyList())
        if (raw.isNotEmpty() && decrypted.isEmpty()) {
            appRef.accountRepository.delete(account)
            throw IllegalStateException(str(R.string.vault_wrong_secret))
        }
    }
}

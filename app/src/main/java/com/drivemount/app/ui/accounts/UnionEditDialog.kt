package com.drivemount.app.ui.accounts

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UnionEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as DriveMountApp).accountRepository

    val candidates: StateFlow<List<AccountEntity>> =
        repo.accounts
            .map { list -> list.filter { it.backendType != "union" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** (বর্তমান upstream টোকেন, create policy); লোড হওয়ার আগে null */
    val loaded = MutableStateFlow<Pair<List<String>, String>?>(null)
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun load(remote: String) {
        viewModelScope.launch {
            loaded.value = runCatching { repo.unionConfig(remote) }.getOrElse {
                error.value = it.message
                emptyList<String>() to "mfs"
            }
        }
    }

    fun save(account: AccountEntity, tokens: List<String>, policy: String, onDone: () -> Unit) {
        if (saving.value) return
        saving.value = true
        error.value = null
        viewModelScope.launch {
            repo.updateUnion(account, tokens, policy).fold(
                onSuccess = { saving.value = false; onDone() },
                onFailure = { saving.value = false; error.value = it.message ?: "Could not save" },
            )
        }
    }
}

/** 3-dot মেনু → "Manage drives": union-এ অ্যাকাউন্ট যোগ/বাদ দেওয়া */
@Composable
fun UnionEditDialog(account: AccountEntity, onDismiss: () -> Unit) {
    val vm: UnionEditViewModel = viewModel(key = "union-" + account.remoteName)
    val candidates by vm.candidates.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(account.remoteName) { vm.load(account.remoteName) }

    val original = loaded?.first.orEmpty()
    val tokenByRemote = original.associateBy { it.substringBefore(":") } // পুরোনো "remote:path" অক্ষত রাখতে
    val selected = remember(loaded) { mutableStateListOf<String>().also { l -> l.addAll(original.map { it.substringBefore(":") }) } }
    var policy by remember(loaded) { mutableStateOf(loaded?.second ?: "mfs") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Manage drives in ${account.displayName}") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (loaded == null) {
                    Text("Loading…")
                } else {
                    UnionPicker(candidates, selected, policy, { policy = it }, enabled = !saving)
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = loaded != null && selected.size >= 2 && !saving,
                onClick = {
                    vm.save(account, selected.map { tokenByRemote[it] ?: "$it:" }, policy, onDismiss)
                },
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

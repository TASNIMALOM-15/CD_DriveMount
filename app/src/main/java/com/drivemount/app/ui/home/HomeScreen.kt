package com.drivemount.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.drivemount.app.CrashReporter
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.saf.openInFilesApp
import com.drivemount.app.ui.accounts.AccountsViewModel
import com.drivemount.app.ui.components.CrashReportDialog
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.components.providerIcon
import com.drivemount.app.ui.components.providerLabel
import com.drivemount.app.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenBackup: () -> Unit,
    vm: AccountsViewModel = viewModel(),
) {
    val groups by vm.groups.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val activeTransfers by vm.activeTransfers.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var crashReport by remember { mutableStateOf(CrashReporter.readAndClear(context)) }
    val sp = MaterialTheme.spacing

    var renameTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var newName by remember(renameTarget) { mutableStateOf(renameTarget?.displayName.orEmpty()) }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSync) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = stringResource(R.string.sync_title),
                        )
                    }
                    IconButton(onClick = onOpenTransfers) {
                        BadgedBox(
                            badge = {
                                if (activeTransfers > 0) Badge { Text(activeTransfers.toString()) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = stringResource(R.string.transfers),
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = groups
        when {
            list == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            list.isEmpty() -> EmptyState(
                onAdd = onAddAccount,
                onImport = onOpenBackup,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = sp.md,
                    end = sp.md,
                    top = padding.calculateTopPadding() + sp.sm,
                    bottom = padding.calculateBottomPadding() + sp.md,
                ),
                verticalArrangement = Arrangement.spacedBy(sp.md),
            ) {
                items(list, key = { it.base.id }) { group ->
                    val account = group.base
                    fun openInFiles(target: AccountEntity) {
                        if (!openInFilesApp(context, target.id)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.files_app_missing)) }
                        }
                    }
                    AccountCard(
                        account = account,
                        vaults = group.vaults,
                        onOpenVault = { onOpenAccount(it.id) },
                        onOpenVaultInFiles = { openInFiles(it) },
                        onRenameVault = { renameTarget = it },
                        onDeleteVault = { deleteTarget = it },
                        onOpen = { onOpenAccount(account.id) },
                        onOpenInFiles = if (account.backendType == "local") {
                            null
                        } else {
                            { openInFiles(account) }
                        },
                        refreshing = account.id in refreshing,
                        onRefresh = { vm.refreshUsage(account) },
                        onRename = { renameTarget = account },
                        onDelete = { deleteTarget = account },
                    )
                }
            }
        }
    }

    crashReport?.let { report ->
        CrashReportDialog(report = report, onDismiss = { crashReport = null })
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.rename(target.id, newName)
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                val inside = groups.orEmpty().firstOrNull { it.base.id == target.id }?.vaults.orEmpty()
                Text(
                    stringResource(R.string.delete_body, target.displayName) +
                        if (inside.isEmpty()) "" else "\n\n" + stringResource(R.string.delete_with_vaults, inside.size),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(target, groups.orEmpty().firstOrNull { it.base.id == target.id }?.vaults.orEmpty())
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    vaults: List<AccountEntity>,
    onOpenVault: (AccountEntity) -> Unit,
    onOpenVaultInFiles: (AccountEntity) -> Unit,
    onRenameVault: (AccountEntity) -> Unit,
    onDeleteVault: (AccountEntity) -> Unit,
    onOpen: () -> Unit,
    onOpenInFiles: (() -> Unit)?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }

    DmCard(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
        SettingsItem(
            icon = providerIcon(account.backendType),
            providerType = account.backendType,
            title = account.displayName,
            subtitle = providerLabel(account.backendType),
            trailing = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_refresh)) },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRefresh()
                            },
                        )
                        if (onOpenInFiles != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_open_files)) },
                                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenInFiles()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            },
        )

        // এই ক্লাউডের ভেতরের এনক্রিপ্টেড ভল্ট (একই কার্ডে)
        if (vaults.isNotEmpty()) {
            Column(modifier = Modifier.padding(bottom = sp.xs)) {
                vaults.forEach { vault ->
                    VaultRow(
                        vault = vault,
                        onOpen = { onOpenVault(vault) },
                        onOpenInFiles = { onOpenVaultInFiles(vault) },
                        onRename = { onRenameVault(vault) },
                        onDelete = { onDeleteVault(vault) },
                    )
                }
            }
        }

        val used = account.usedBytes
        val total = account.totalBytes
        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.md)
                    .padding(bottom = sp.md),
            )
        } else if (used != null) {
            Column(
                modifier = Modifier
                    .padding(horizontal = sp.md)
                    .padding(bottom = sp.md),
                verticalArrangement = Arrangement.spacedBy(sp.xs),
            ) {
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = if (total != null && total > 0) {
                        stringResource(R.string.usage_of, formatBytes(used), formatBytes(total))
                    } else {
                        stringResource(R.string.usage_used, formatBytes(used))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    val sp = MaterialTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(sp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(sp.lg))
        Text(
            text = stringResource(R.string.no_accounts_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(sp.sm))
        Text(
            text = stringResource(R.string.no_accounts_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(sp.lg))
        FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.add_account)) }
        TextButton(onClick = onImport) { Text(stringResource(R.string.home_import)) }
    }
}

/** কার্ডের ভেতরের একটি ভল্ট সারি: আলাদা রঙের ব্লক নয়, কার্ডের নিজের রঙেই; শুধু এই সারিতে ট্যাপ করলেই ভল্ট খোলে */
@Composable
private fun VaultRow(
    vault: AccountEntity,
    onOpen: () -> Unit,
    onOpenInFiles: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    // ক্লাউডের শিরোনাম সারির (SettingsItem) সাথে একই মাপ ও ফাঁক: আইকন ৪০dp ঘরে, নাম একই জায়গায়, ৩-ডট একই কলামে
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = sp.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = vault.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_open_files)) },
                    leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenInFiles() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_rename)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

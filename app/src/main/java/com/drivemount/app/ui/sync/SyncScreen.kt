package com.drivemount.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.data.transfer.SyncMode
import com.drivemount.app.data.transfer.SyncProfileEntity
import com.drivemount.app.data.transfer.TransferStatus
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.components.formatDate
import com.drivemount.app.ui.theme.spacing

/** মিনিটে সময়ের ব্যবধানের লেখা */
@Composable
fun intervalLabel(minutes: Int): String = stringResource(
    when (minutes) {
        60 -> R.string.sync_interval_hour
        360 -> R.string.sync_interval_6h
        720 -> R.string.sync_interval_12h
        1440 -> R.string.sync_interval_day
        else -> R.string.sync_interval_manual
    },
)

@Composable
fun modeLabel(mode: String): String = stringResource(
    when (mode) {
        SyncMode.MIRROR -> R.string.mode_mirror
        SyncMode.MOVE -> R.string.mode_move
        else -> R.string.mode_copy
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: SyncViewModel = viewModel(),
) {
    val profiles by vm.profiles.collectAsState()
    val names by vm.accountNames.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val sp = MaterialTheme.spacing

    var confirmRun by remember { mutableStateOf<SyncProfileEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SyncProfileEntity?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sync_new)) },
            )
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(sp.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.sync_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = sp.md,
                    end = sp.md,
                    top = padding.calculateTopPadding() + sp.sm,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(sp.md),
            ) {
                items(profiles, key = { it.id }) { p ->
                    ProfileCard(
                        profile = p,
                        names = names,
                        onRun = {
                            if (p.mode == SyncMode.COPY) vm.runNow(p.id) else confirmRun = p
                        },
                        onEdit = { onEdit(p.id) },
                        onDelete = { confirmDelete = p },
                    )
                }
            }
        }
    }

    confirmRun?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text(stringResource(R.string.sync_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (p.mode == SyncMode.MIRROR) R.string.sync_mirror_body else R.string.sync_move_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.runNow(p.id)
                        confirmRun = null
                    },
                ) { Text(stringResource(R.string.sync_run_now)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRun = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.sync_delete_title)) },
            text = { Text(stringResource(R.string.sync_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(p.id)
                        confirmDelete = null
                    },
                ) { Text(stringResource(R.string.sync_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: SyncProfileEntity,
    names: Map<Long, String>,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }

    fun place(accountId: Long, path: String): String {
        val name = names[accountId] ?: "?"
        return if (path.isEmpty()) name else "$name/$path"
    }

    val lastRun = profile.lastRunAt?.let { at ->
        val status = when (profile.lastStatus) {
            TransferStatus.DONE -> stringResource(R.string.tr_done)
            TransferStatus.CANCELLED -> stringResource(R.string.tr_cancelled)
            else -> stringResource(R.string.tr_failed)
        }
        stringResource(R.string.sync_last_run_fmt, formatDate(at)) + " · " + status
    } ?: stringResource(R.string.sync_never)

    DmCard(contentPadding = PaddingValues(0.dp)) {
        SettingsItem(
            icon = Icons.Rounded.Sync,
            title = profile.name,
            subtitle = place(profile.srcAccountId, profile.srcPath) + "  →  " +
                place(profile.dstAccountId, profile.dstPath),
            trailing = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_delete)) },
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.md)
                .padding(bottom = sp.md),
            verticalArrangement = Arrangement.spacedBy(sp.xs),
        ) {
            Text(
                text = modeLabel(profile.mode) + " · " + intervalLabel(profile.intervalMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = lastRun,
                style = MaterialTheme.typography.bodySmall,
                color = if (profile.lastStatus == TransferStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(sp.xs))
            FilledTonalButton(onClick = onRun) { Text(stringResource(R.string.sync_run_now)) }
        }
    }
}

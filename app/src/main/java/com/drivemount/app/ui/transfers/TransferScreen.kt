package com.drivemount.app.ui.transfers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.data.transfer.TransferEntity
import com.drivemount.app.data.transfer.TransferStatus
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.IconBadge
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.theme.spacing

private val FinishedStatuses = setOf(TransferStatus.DONE, TransferStatus.FAILED, TransferStatus.CANCELLED)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    vm: TransferViewModel = viewModel(),
) {
    val list by vm.transfers.collectAsState()
    val sp = MaterialTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (list.any { it.status in FinishedStatuses }) {
                        IconButton(onClick = { vm.clearFinished() }) {
                            Icon(
                                imageVector = Icons.Rounded.ClearAll,
                                contentDescription = stringResource(R.string.tr_clear_finished),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(sp.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.tr_empty),
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
                    bottom = padding.calculateBottomPadding() + sp.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                items(list, key = { it.id }) { task ->
                    TransferRow(
                        task = task,
                        onCancel = { vm.cancel(task.id) },
                        onRetry = { vm.retry(task.id) },
                        onRemove = { vm.remove(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferRow(
    task: TransferEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val active = task.status == TransferStatus.QUEUED ||
        task.status == TransferStatus.PREPARING ||
        task.status == TransferStatus.RUNNING

    val icon = when (task.status) {
        TransferStatus.DONE -> Icons.Rounded.CheckCircle
        TransferStatus.FAILED -> Icons.Rounded.Error
        TransferStatus.CANCELLED -> Icons.Rounded.Cancel
        TransferStatus.QUEUED -> Icons.Rounded.Schedule
        else -> Icons.Rounded.Sync
    }
    val subtitle = when (task.status) {
        TransferStatus.QUEUED -> stringResource(R.string.tr_waiting)
        TransferStatus.PREPARING -> stringResource(R.string.tr_preparing)
        TransferStatus.RUNNING ->
            if (task.bytesTotal > 0) {
                stringResource(
                    R.string.tr_running_fmt,
                    formatBytes(task.bytesDone),
                    formatBytes(task.bytesTotal),
                    formatBytes(task.speed),
                )
            } else {
                stringResource(R.string.tr_working)
            }
        TransferStatus.DONE -> stringResource(R.string.tr_done)
        TransferStatus.CANCELLED -> stringResource(R.string.tr_cancelled)
        else -> task.error ?: stringResource(R.string.tr_failed)
    }

    DmCard(contentPadding = PaddingValues(horizontal = sp.md, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            IconBadge(icon)
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == TransferStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tr_cancel))
                }
            } else {
                if (task.status != TransferStatus.DONE) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.tr_retry))
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.tr_remove))
                }
            }
        }
        if (task.status == TransferStatus.RUNNING || task.status == TransferStatus.PREPARING) {
            Spacer(Modifier.height(sp.sm))
            if (task.status == TransferStatus.RUNNING && task.bytesTotal > 0) {
                LinearProgressIndicator(
                    progress = { (task.bytesDone.toFloat() / task.bytesTotal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

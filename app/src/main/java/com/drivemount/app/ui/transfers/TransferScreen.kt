package com.drivemount.app.ui.transfers

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.CircleAlert as LuCircleAlert
import com.composables.icons.lucide.CircleCheck as LuCircleCheck
import com.composables.icons.lucide.CircleX as LuCircleX
import com.composables.icons.lucide.Clock as LuClock
import com.composables.icons.lucide.ListX as LuListX
import com.composables.icons.lucide.Pause as LuPause
import com.composables.icons.lucide.Play as LuPlay
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Trash2 as LuTrash2
import com.composables.icons.lucide.X as LuX
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
private val ActiveStatuses = setOf(TransferStatus.QUEUED, TransferStatus.PREPARING, TransferStatus.RUNNING)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    /** true: নিচের নেভিগেশন-বারসহ ট্যাব হিসেবে দেখানো (ব্যাক-তীর নেই) */
    embedded: Boolean = false,
    vm: TransferViewModel = viewModel(),
) {
    val list by vm.transfers.collectAsState()
    val backlog by vm.backlog.collectAsState()
    val stages by vm.stages.collectAsState()
    val sp = MaterialTheme.spacing
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold(
        topBar = { com.drivemount.app.ui.components.TopBarIcons {
            TopAppBar(
                title = { Text(stringResource(R.string.transfers)) },
                navigationIcon = {
                    if (!embedded) IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.LuArrowLeft,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (list.any { it.status in ActiveStatuses }) {
                        IconButton(onClick = { vm.pauseAll() }) {
                            Icon(Lucide.LuPause, contentDescription = stringResource(R.string.tr_pause_all))
                        }
                    } else if (list.any { it.status == TransferStatus.PAUSED }) {
                        IconButton(onClick = { vm.resumeAll() }) {
                            Icon(Lucide.LuPlay, contentDescription = stringResource(R.string.tr_resume_all))
                        }
                    }
                    if (list.any { it.status in FinishedStatuses }) {
                        IconButton(onClick = { vm.clearFinished() }) {
                            Icon(
                                imageVector = Lucide.LuListX,
                                contentDescription = stringResource(R.string.tr_clear_finished),
                            )
                        }
                    }
                },
            )
        }},
    ) { padding ->
        if (list.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = com.drivemount.app.ui.home.LocalBottomBarInset.current)
                    .padding(sp.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (backlog.total > 0) {
                    PreviewBacklogCard(backlog.waiting, backlog.failed, onRetry = { vm.retryPreviews() })
                    Spacer(Modifier.height(sp.lg))
                }
                Text(
                    text = stringResource(R.string.tr_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + sp.sm,
                    bottom = padding.calculateBottomPadding() + sp.lg + com.drivemount.app.ui.home.LocalBottomBarInset.current,
                ),
            ) {
                if (backlog.total > 0) {
                    item(key = "previews_backlog") {
                        PreviewBacklogCard(backlog.waiting, backlog.failed, onRetry = { vm.retryPreviews() })
                    }
                }
                items(list, key = { it.id }) { task ->
                    TransferRow(
                        task = task,
                        stage = stages[task.id],
                        onPause = { vm.pause(task.id) },
                        onResume = { vm.resume(task.id) },
                        onCancel = { vm.cancel(task.id) },
                        onRetry = { vm.retry(task.id) },
                        onRemove = { vm.remove(task.id) },
                    )
                }
            }
        }
    }
}

/** আপলোডের পরে ক্লাউডে যেতে বাকি প্রিভিউ (থাম্বনেল/seek sheet/মেটা): সংখ্যা + ব্যর্থ হলে "Try again" */
@Composable
private fun PreviewBacklogCard(waiting: Int, failed: Int, onRetry: () -> Unit) {
    val sp = MaterialTheme.spacing
    DmCard(contentPadding = PaddingValues(horizontal = sp.md, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            Text(
                text = if (failed > 0) {
                    stringResource(R.string.tr_previews_failed, waiting, failed)
                } else {
                    stringResource(R.string.tr_previews_waiting, waiting)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (failed > 0) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.tr_retry)) }
            }
        }
    }
}

/** কম্প্যাক্ট সারি — ফোল্ডার-ব্রাউজের সারির মতো: কার্ড নেই, অতি সূক্ষ্ম রেখা, ছোট আইকন, প্রগ্রেস সারির ওপর হালকা ভরাট */
@Composable
private fun TransferRow(
    task: TransferEntity,
    stage: Int?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val active = task.status in ActiveStatuses
    val paused = task.status == TransferStatus.PAUSED

    val icon = when (task.status) {
        TransferStatus.DONE -> Lucide.LuCircleCheck
        TransferStatus.FAILED -> Lucide.LuCircleAlert
        TransferStatus.CANCELLED -> Lucide.LuCircleX
        TransferStatus.PAUSED -> Lucide.LuPause
        TransferStatus.QUEUED -> Lucide.LuClock
        else -> Lucide.LuRefreshCcw
    }
    val subtitle = when (task.status) {
        TransferStatus.QUEUED -> stringResource(R.string.tr_waiting)
        TransferStatus.PAUSED -> stringResource(R.string.tr_paused)
        TransferStatus.PREPARING -> stage?.let { com.drivemount.app.ui.browser.stageText(it) } ?: stringResource(R.string.tr_preparing)
        TransferStatus.RUNNING ->
            if (task.bytesTotal > 0) {
                stringResource(
                    R.string.tr_running_fmt,
                    formatBytes(task.bytesDone),
                    formatBytes(task.bytesTotal),
                    formatBytes(task.speed),
                )
            } else {
                stage?.let { com.drivemount.app.ui.browser.stageText(it) } ?: stringResource(R.string.tr_working)
            }
        TransferStatus.DONE -> stringResource(R.string.tr_done)
        TransferStatus.CANCELLED -> stringResource(R.string.tr_cancelled)
        else -> task.error ?: stringResource(R.string.tr_failed)
    }
    val fraction = if (task.status == TransferStatus.RUNNING && task.bytesTotal > 0) {
        (task.bytesDone.toFloat() / task.bytesTotal.toFloat()).coerceIn(0f, 1f)
    } else null
    val fill = colors.primary.copy(alpha = 0.16f)
    val divider = colors.outlineVariant.copy(alpha = 0.45f)
    val tint = when (task.status) {
        TransferStatus.FAILED -> colors.error
        TransferStatus.DONE -> colors.primary
        else -> colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                fraction?.let { drawRect(fill, size = androidx.compose.ui.geometry.Size(size.width * it, size.height)) }
                val inset = 12.dp.toPx()
                val y = size.height - 0.25.dp.toPx()
                drawLine(
                    color = divider,
                    start = androidx.compose.ui.geometry.Offset(inset, y),
                    end = androidx.compose.ui.geometry.Offset(size.width - inset, y),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .padding(start = sp.md + sp.sm, end = sp.sm, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        Box(
            Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = if (task.status == TransferStatus.FAILED) colors.error else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (fraction != null) {
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.onSurfaceVariant,
            )
        }
        @Composable
        fun act(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: Int, onClick: () -> Unit) {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(icon, contentDescription = stringResource(desc), modifier = Modifier.size(20.dp))
            }
        }
        when {
            active -> {
                act(Lucide.LuPause, R.string.tr_pause, onPause)
                act(Lucide.LuX, R.string.tr_cancel, onCancel)
            }
            paused -> {
                act(Lucide.LuPlay, R.string.tr_resume, onResume)
                act(Lucide.LuX, R.string.tr_cancel, onRemove)
            }
            else -> {
                if (task.status != TransferStatus.DONE) act(Lucide.LuRefreshCw, R.string.tr_retry, onRetry)
                act(Lucide.LuTrash2, R.string.tr_remove, onRemove)
            }
        }
    }
}

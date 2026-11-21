package com.drivemount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.theme.spacing

/**
 * পুরোনো ফাইলের প্রিভিউ বানানোর অনুমতি-কার্ড। কিছুই নিজে থেকে চলে না:
 * ব্যবহারকারী বোতাম চাপে → ডায়ালগে কী হবে পরিষ্কার লেখা → "শুরু করো" চাপলে তবেই কাজ লাইনে যায়।
 */
@Composable
fun BackfillCard(vm: BackfillViewModel = viewModel()) {
    val sp = MaterialTheme.spacing
    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var onlyCharging by remember { mutableStateOf(true) }

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            Text(stringResource(R.string.backfill_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.backfill_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.active) {
                ActiveProgress(state, vm)
            } else {
                if (state.hasLast) LastRunSummary(state)
                FilledTonalButton(onClick = { selected = accounts.firstOrNull()?.id; showDialog = true }, enabled = accounts.isNotEmpty()) {
                    Text(stringResource(if (state.hasLast && state.lastPartial) R.string.backfill_continue else R.string.backfill_button))
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backfill_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Text(stringResource(R.string.backfill_dialog_body), style = MaterialTheme.typography.bodyMedium)
                    accounts.forEach { a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selected = a.id },
                        ) {
                            RadioButton(selected = selected == a.id, onClick = { selected = a.id })
                            Text(a.displayName)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onlyCharging = !onlyCharging }.padding(top = sp.xs),
                    ) {
                        Checkbox(checked = onlyCharging, onCheckedChange = { onlyCharging = it })
                        Text(stringResource(R.string.backfill_only_charging))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected != null,
                    onClick = {
                        selected?.let { vm.start(it, onlyCharging) }
                        showDialog = false
                    },
                ) { Text(stringResource(R.string.backfill_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.backfill_cancel)) }
            },
        )
    }
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** চলার সময়: ধাপ, শতাংশ-বার, এখনকার ফাইল, ধরন-অনুযায়ী গণনা, বাকি সময়, ব্যর্থ সংখ্যা */
@Composable
private fun ActiveProgress(state: BackfillState, vm: BackfillViewModel) {
    val sp = MaterialTheme.spacing
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        if (state.waiting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.backfill_waiting), style = MaterialTheme.typography.bodySmall)
        } else {
            val working = state.phase == com.drivemount.app.work.BackfillWorker.PHASE_WORK && state.total > 0
            if (working) {
                LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                val pct = state.done * 100 / state.total
                Text(
                    stringResource(R.string.backfill_phase_work, state.done, state.total, pct),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.cur.isNotEmpty()) {
                    Text(
                        stringResource(R.string.backfill_now, state.cur),
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                val c = state.cats
                Text(
                    stringResource(R.string.backfill_cats, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
                val eta = if (state.etaSec >= 0) com.drivemount.app.work.BackfillWorker.etaText(ctx, state.etaSec) + " · " else ""
                Text(
                    eta + stringResource(R.string.backfill_elapsed, formatDuration(state.elapsedSec)),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
                if (state.failed > 0) {
                    Text(
                        stringResource(R.string.backfill_failed_count, state.failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                // তালিকা বানানো / ক্লাউডের তথ্য আনা / ক্লাউডে লেখা: মোট তখনো অজানা, তাই অনির্দিষ্ট বার, কিন্তু ধাপের নাম দেখায়
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        when (state.phase) {
                            com.drivemount.app.work.BackfillWorker.PHASE_IMPORT -> R.string.backfill_phase_import
                            com.drivemount.app.work.BackfillWorker.PHASE_SAVE -> R.string.backfill_phase_save
                            else -> R.string.backfill_phase_scan
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.backfill_elapsed, formatDuration(state.elapsedSec)),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
            }
        }
        OutlinedButton(onClick = { vm.stop() }) { Text(stringResource(R.string.backfill_stop)) }
    }
}

/** শেষবারের সারাংশ: কতগুলো হলো, কতগুলো ব্যর্থ (নামসহ), কত সময় লাগল, আরও বাকি কি না */
@Composable
private fun LastRunSummary(state: BackfillState) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.lastTotal == 0) {
            Text(stringResource(R.string.backfill_nothing), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                stringResource(R.string.backfill_summary, state.lastDone, state.lastFailed, state.lastSkipped, formatDuration(state.lastElapsedSec)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.lastFailedNames.isNotEmpty()) {
            Text(
                stringResource(R.string.backfill_failed_names, state.lastFailedNames.replace("\n", ", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (state.lastPartial) Text(stringResource(R.string.backfill_partial), style = MaterialTheme.typography.bodySmall, color = dim)
    }
}

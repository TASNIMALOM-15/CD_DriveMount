package com.drivemount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.theme.spacing
import com.drivemount.app.work.IndexWorker

/** সব ফোল্ডারের নামের ইনডেক্স: অনুমতি-ভিত্তিক, নিজে থেকে চলে না */
@Composable
fun IndexCard(vm: IndexViewModel = viewModel()) {
    val sp = MaterialTheme.spacing
    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableLongStateOf(IndexWorker.ALL) }

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            Text(stringResource(R.string.index_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.index_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.active) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (state.waiting) stringResource(R.string.index_waiting) else stringResource(R.string.index_running, state.folders),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { vm.stop() }) { Text(stringResource(R.string.backfill_stop)) }
            } else {
                if (state.folders > 0) {
                    Text(
                        stringResource(if (state.partial) R.string.index_last_partial else R.string.index_last, state.folders),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalButton(onClick = { selected = IndexWorker.ALL; showDialog = true }, enabled = accounts.isNotEmpty()) {
                    Text(stringResource(R.string.index_button))
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.index_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Text(stringResource(R.string.index_dialog_body), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selected = IndexWorker.ALL },
                    ) {
                        RadioButton(selected = selected == IndexWorker.ALL, onClick = { selected = IndexWorker.ALL })
                        Text(stringResource(R.string.index_all_accounts))
                    }
                    accounts.forEach { a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selected = a.id },
                        ) {
                            RadioButton(selected = selected == a.id, onClick = { selected = a.id })
                            Text(a.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.start(selected); showDialog = false }) { Text(stringResource(R.string.backfill_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.backfill_cancel)) } },
        )
    }
}

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
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (state.waiting) stringResource(R.string.backfill_waiting)
                    else stringResource(R.string.backfill_running, state.done),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { vm.stop() }) { Text(stringResource(R.string.backfill_stop)) }
            } else {
                if (state.done > 0) {
                    Text(stringResource(R.string.backfill_last, state.done), style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = { selected = accounts.firstOrNull()?.id; showDialog = true }, enabled = accounts.isNotEmpty()) {
                    Text(stringResource(R.string.backfill_button))
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

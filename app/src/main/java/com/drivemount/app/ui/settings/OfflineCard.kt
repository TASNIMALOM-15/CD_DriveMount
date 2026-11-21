package com.drivemount.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.theme.spacing
import kotlinx.coroutines.launch

/** অফলাইন ফাইল: কত জায়গা নিচ্ছে, সীমা, শুধু Wi-Fi, এখনই মেলাও, সব মুছো */
@Composable
fun OfflineCard() {
    val context = LocalContext.current
    val store = (context.applicationContext as DriveMountApp).offlineStore
    val sp = MaterialTheme.spacing
    val scope = rememberCoroutineScope()
    val snap by store.ui.collectAsState()
    var limit by remember { mutableLongStateOf(store.limitBytes) }
    var wifiOnly by remember { mutableStateOf(store.wifiOnly) }
    var confirmClear by remember { mutableStateOf(false) }
    val gb = 1024L * 1024 * 1024

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            Text(stringResource(R.string.offline_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.offline_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.offline_used,
                    Formatter.formatShortFileSize(context, snap.usedBytes),
                    Formatter.formatShortFileSize(context, limit),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            store.lastError.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.offline_last_error, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(stringResource(R.string.offline_limit), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                for (g in listOf(2L, 5L, 10L, 20L)) {
                    FilterChip(
                        selected = limit == g * gb,
                        onClick = { limit = g * gb; store.limitBytes = limit },
                        label = { Text("$g GB") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.offline_wifi_only), modifier = Modifier.weight(1f))
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; store.wifiOnly = it })
            }
            Text(
                stringResource(R.string.offline_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                FilledTonalButton(onClick = { store.enqueue() }) { Text(stringResource(R.string.offline_sync_now)) }
                OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.offline_clear)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.offline_clear_title)) },
            text = { Text(stringResource(R.string.offline_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { store.clearAll() }
                }) { Text(stringResource(R.string.offline_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

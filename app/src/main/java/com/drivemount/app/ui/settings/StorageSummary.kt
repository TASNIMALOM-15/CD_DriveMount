package com.drivemount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.formatBytesPrecise
import com.drivemount.app.ui.components.ProviderIcon
import com.drivemount.app.ui.components.providerLabel
import com.drivemount.app.ui.theme.spacing

/** Settings-এর সবার ওপরে: সব ক্লাউডের মোট স্টোরেজ (বড় করে) আর নিচে সংযুক্ত ক্লাউডের ছোট আইকন */
@Composable
fun StorageSummaryCard(accounts: List<AccountEntity>, onClick: () -> Unit) {
    val sp = MaterialTheme.spacing
    val total = accounts.sumOf { it.totalBytes ?: 0L }
    DmCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(sp.md)) {
        Text(
            text = if (total > 0) formatBytesPrecise(total) else "—",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = sp.xs),
        )
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(sp.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                accounts.forEach {
                    ProviderIcon(
                        type = it.backendType,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** প্রতিটি ক্লাউডের ধরন, নাম, মোট ও ব্যবহৃত স্টোরেজ, সাথে নিচে progress bar */
@Composable
fun StorageDetailsDialog(
    accounts: List<AccountEntity>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.md),
        ) {
            Column(modifier = Modifier.padding(vertical = sp.md)) {
                Text(
                    text = stringResource(R.string.storage_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = sp.md),
                )
                Spacer(Modifier.height(sp.sm))
                StorageRow(
                    type = stringResource(R.string.storage_col_type),
                    name = stringResource(R.string.storage_col_name),
                    total = stringResource(R.string.storage_col_total),
                    used = stringResource(R.string.storage_col_used),
                    header = true,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = sp.md),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (accounts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.storage_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(sp.md),
                        )
                    }
                    accounts.forEach { a ->
                        val t = a.totalBytes
                        val u = a.usedBytes
                        Column {
                            StorageRow(
                                type = providerLabel(a.backendType),
                                name = a.displayName,
                                total = t?.let { formatBytesPrecise(it) } ?: "—",
                                used = u?.let { formatBytesPrecise(it) } ?: "—",
                                header = false,
                            )
                            if (t != null && t > 0 && u != null) {
                                val fraction = (u.toFloat() / t.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    color = if (fraction > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = sp.md)
                                        .padding(bottom = sp.sm),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sp.sm),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRefresh, enabled = !refreshing) {
                        Text(stringResource(R.string.storage_refresh))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.storage_close)) }
                }
            }
        }
    }
}

@Composable
private fun StorageRow(type: String, name: String, total: String, used: String, header: Boolean) {
    val sp = MaterialTheme.spacing
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.md, vertical = sp.xs + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(sp.xs),
    ) {
        Text(type, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.1f))
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(total, style = style, color = color, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(used, style = style, color = color, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

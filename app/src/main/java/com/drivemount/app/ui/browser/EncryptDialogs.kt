package com.drivemount.app.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.ui.components.IconBadge
import com.drivemount.app.ui.components.providerIcon
import com.drivemount.app.ui.theme.spacing

private val Points = listOf(
    R.string.enc_point1,
    R.string.enc_point2,
    R.string.enc_point3,
    R.string.enc_point4,
    R.string.enc_point5,
)

/** প্রতিবার আপলোডের আগে: ফাইলগুলো এনক্রিপ্ট করা হবে কি না */
@Composable
fun EncryptPromptDialog(onYes: () -> Unit, onNo: () -> Unit, onDismiss: () -> Unit) {
    val sp = MaterialTheme.spacing
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(sp.lg),
                verticalArrangement = Arrangement.spacedBy(sp.md),
            ) {
                IconBadge(Icons.Rounded.Lock)
                Text(
                    text = stringResource(R.string.enc_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Points.forEach { point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                            Text("•", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(point),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(sp.xs))
                Button(onClick = onYes, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enc_yes))
                }
                OutlinedButton(onClick = onNo, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enc_no))
                }
            }
        }
    }
}

/** একাধিক ভল্ট থাকলে কোনটিতে যাবে তা বাছাই */
@Composable
fun VaultPickerDialog(
    vaults: List<AccountEntity>,
    onPick: (AccountEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(sp.lg), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                Text(stringResource(R.string.vault_pick_title), style = MaterialTheme.typography.titleLarge)
                vaults.forEach { vault ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(vault) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.md),
                    ) {
                        IconBadge(providerIcon(vault.backendType))
                        Text(vault.displayName, style = MaterialTheme.typography.titleMedium)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

/** "হ্যাঁ" চাপা হয়েছে কিন্তু কোনো ভল্ট নেই */
@Composable
fun NoVaultDialog(onCreate: () -> Unit, onDismiss: () -> Unit) {
    val sp = MaterialTheme.spacing
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(sp.lg), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Text(stringResource(R.string.vault_none_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.vault_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.vault_create_now))
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

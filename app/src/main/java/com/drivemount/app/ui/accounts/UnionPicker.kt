package com.drivemount.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.ui.components.ProviderIcon

/** Union বানানো ও সম্পাদনার জন্য সাধারণ বাছাই-অংশ (আইকনসহ অ্যাকাউন্ট তালিকা + নীতি) */
@Composable
fun UnionPicker(
    candidates: List<AccountEntity>,
    selected: SnapshotStateList<String>,
    policy: String,
    onPolicy: (String) -> Unit,
    enabled: Boolean,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Combine two or more of your drives into one big drive. Pick the drives to include:",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (candidates.isEmpty()) {
            Text(
                "Add some cloud accounts first, then come back here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        candidates.forEach { a ->
            val checked = a.remoteName in selected
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled) {
                    if (checked) selected.remove(a.remoteName) else selected.add(a.remoteName)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                ProviderIcon(a.backendType, Modifier.padding(start = 8.dp).size(28.dp))
                Text(
                    a.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Text("Where should new files go?", style = MaterialTheme.typography.titleSmall)
        listOf(
            "mfs" to "Drive with the most free space (recommended)",
            "epmfs" to "Existing folder, most free space",
            "ff" to "First drive in the list",
            "rand" to "A random drive",
        ).forEach { (key, label) ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled) { onPolicy(key) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = policy == key, onClick = null, enabled = enabled)
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "Existing files stay where they are and all drives appear merged in one place. " +
                "If a provider does not report free space, use \"First drive\" or \"Random\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

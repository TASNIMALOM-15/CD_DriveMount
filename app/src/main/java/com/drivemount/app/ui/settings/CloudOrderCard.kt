package com.drivemount.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDown as LuArrowDown
import com.composables.icons.lucide.ArrowUp as LuArrowUp
import com.composables.icons.lucide.Lucide
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.Icon
import com.drivemount.app.ui.components.providerLabel
import com.drivemount.app.ui.theme.spacing

/** Cloud ট্যাবে ক্লাউডগুলো কোন ধরনের পরে কোন ধরন আসবে, সেই সিরিয়াল বদলানো */
@Composable
fun CloudOrderCard() {
    val app = LocalContext.current.applicationContext as DriveMountApp
    val settings = app.cloudOrder
    val saved by settings.order.collectAsState()
    val accounts by app.accountRepository.accounts.collectAsState(initial = emptyList())
    val sp = MaterialTheme.spacing
    val present = accounts.map { it.backendType }.filter { it != "crypt" }.distinct()
    // saved বদলালে পুনরায় হিসাব হয়
    val types = remember(saved, present) { settings.effective(present).filter { it != "crypt" } }

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            Text(stringResource(R.string.cloud_order_help), style = MaterialTheme.typography.bodyMedium)
            types.forEachIndexed { i, type ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        providerLabel(type),
                        modifier = Modifier.weight(1f),
                        color = if (type in present) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { settings.move(type, -1, present) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Lucide.LuArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { settings.move(type, +1, present) }, enabled = i < types.lastIndex, modifier = Modifier.size(36.dp)) {
                        Icon(Lucide.LuArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { settings.reset() }) { Text(stringResource(R.string.cloud_order_reset)) }
        }
    }
}

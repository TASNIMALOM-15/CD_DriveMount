package com.drivemount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.drivemount.app.ui.components.FabIcon
import com.drivemount.app.ui.components.FabIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.FabCatalog
import com.composables.icons.lucide.ChevronsLeft as LuChevronsLeft
import com.composables.icons.lucide.ChevronsRight as LuChevronsRight
import com.composables.icons.lucide.Lucide
import com.drivemount.app.ui.theme.spacing

/**
 * ডান-নিচের ফ্লোটিং বাটনের কাস্টমাইজ: পুরো বাটন চালু/বন্ধ, আর প্রতিটি পেজে কোন কোন কাজের আইকন থাকবে
 * (যেটা টিক নেই সেটা ওই পেজে আসবে না)।
 */
@Composable
fun FabSettingsCard() {
    val settings = (LocalContext.current.applicationContext as DriveMountApp).fabSettings
    val enabled by settings.enabled.collectAsState()
    val hidden by settings.hidden.collectAsState()
    val icons by settings.icons.collectAsState()
    val colorsMap by settings.colors.collectAsState()
    var pick by remember { mutableStateOf<Pair<String, String>?>(null) }
    val sp = MaterialTheme.spacing

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fab_enable), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { settings.setEnabled(it) })
            }
            Text(
                stringResource(R.string.fab_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                Text(
                    stringResource(R.string.fab_toggle_buttons),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = sp.sm),
                )
                listOf(
                    FabCatalog.TOGGLE_CLOSED to (R.string.fab_toggle_closed to Lucide.LuChevronsLeft),
                    FabCatalog.TOGGLE_OPEN to (R.string.fab_toggle_open to Lucide.LuChevronsRight),
                ).forEach { (id, info) ->
                    val (label, def) = info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pick = FabCatalog.TOGGLE to id },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    ) {
                        FabIcon(
                            icons["${FabCatalog.TOGGLE}:$id"], def, stringResource(R.string.fab_icon_title),
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .padding(6.dp),
                            color = colorsMap["${FabCatalog.TOGGLE}:$id"],
                        )
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FabCatalog.pages.forEach { page ->
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = sp.sm),
                    )
                    page.actions.forEach { def ->
                        val shown = "${page.key}:${def.id}" !in hidden
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settings.setVisible(page.key, def.id, !shown) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(sp.sm),
                        ) {
                            Checkbox(checked = shown, onCheckedChange = { settings.setVisible(page.key, def.id, it) })
                            FabIcon(
                                icons["${page.key}:${def.id}"], def.icon, stringResource(R.string.fab_icon_title),
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { pick = page.key to def.id }
                                    .padding(6.dp),
                                color = colorsMap["${page.key}:${def.id}"],
                            )
                            Text(stringResource(def.label), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                TextButton(onClick = { settings.resetActions() }) { Text(stringResource(R.string.fab_reset)) }
            }
        }
    }

    pick?.let { (pg, id) ->
        val k = "$pg:$id"
        val def = when {
            pg == FabCatalog.TOGGLE && id == FabCatalog.TOGGLE_OPEN -> Lucide.LuChevronsRight
            pg == FabCatalog.TOGGLE -> Lucide.LuChevronsLeft
            else -> FabCatalog.pages.first { it.key == pg }.actions.first { it.id == id }.icon
        }
        IconEditorDialog(
            title = stringResource(R.string.fab_icon_title),
            vector = def,
            image = icons[k],
            color = colorsMap[k],
            onPick = { settings.setIcon(pg, id, it) },
            onImport = { settings.importIcon(pg, id, it) },
            onColor = { settings.setColor(pg, id, it) },
            onDismiss = { pick = null },
        )
    }
}

package com.drivemount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.CloudGlowSettings
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.ProviderIcon
import com.drivemount.app.ui.components.providerName
import com.drivemount.app.ui.theme.spacing

/** সেটিংস: কোন ক্লাউডের কার্ডে কোন রঙের আলো পড়বে (যে ক্লাউডগুলো যোগ করা আছে শুধু সেগুলোই দেখায়) */
@Composable
fun CloudGlowCard() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DriveMountApp
    val settings = app.cloudGlow
    val overrides by settings.overrides.collectAsState()
    val accounts by app.accountRepository.accounts.collectAsState(initial = emptyList())
    val types = remember(accounts) {
        accounts.filter { it.backendType != "crypt" && it.remoteName != com.drivemount.app.data.galaxy.Galaxy.REMOTE }
            .map { it.backendType }
            .distinct()
    }
    var editing by remember { mutableStateOf<String?>(null) }
    val sp = MaterialTheme.spacing

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            Text("Cloud card glow", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose the glow colour of each cloud's card on the Cloud tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (types.isEmpty()) {
                Text("No clouds added yet.", style = MaterialTheme.typography.bodyMedium)
            }
            types.forEach { type ->
                val color = CloudGlowSettings.resolve(overrides, type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = type }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                ) {
                    ProviderIcon(type, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(providerName(ctx, type), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color ?: Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            }
        }
    }

    editing?.let { type ->
        var picked by remember(type) {
            mutableStateOf(CloudGlowSettings.resolve(overrides, type)?.toArgb() ?: Color(0xFF7C8CF8).toArgb())
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(providerName(ctx, type)) },
            text = {
                // CustomColorPicker নিজেই verticalScroll করে; বাইরে আরেকটি verticalScroll
                // দিলে infinite-height constraint এ ক্র্যাশ হতো, তাই সরাসরি বসানো হলো।
                CustomColorPicker(
                    initialArgb = picked,
                    onColorChange = { picked = it or (0xFF shl 24) },
                )
            },
            confirmButton = {
                TextButton(onClick = { settings.set(type, picked or (0xFF shl 24)); editing = null }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { settings.off(type); editing = null }) { Text("No glow") }
                    TextButton(onClick = { settings.reset(type); editing = null }) { Text("Default") }
                }
            },
        )
    }
}

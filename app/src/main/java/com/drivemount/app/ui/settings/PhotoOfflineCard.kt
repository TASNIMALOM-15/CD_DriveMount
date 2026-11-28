package com.drivemount.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Photos ট্যাবের অফলাইন প্রিভিউ: চালু/বন্ধ, কতটুকু জায়গা নিচ্ছে, মুছে ফেলা */
@Composable
fun PhotoOfflineCard() {
    val context = LocalContext.current
    val previews = (context.applicationContext as DriveMountApp).photoPreviews
    val sp = MaterialTheme.spacing
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(previews.offlineEnabled) }
    var used by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { previews.usedBytes() } }

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.photo_offline_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.photo_offline_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it; previews.offlineEnabled = it })
            }
            Text(
                stringResource(R.string.photo_offline_used, Formatter.formatShortFileSize(context, used)),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { previews.clear() }
                    used = 0L
                }
            }) { Text(stringResource(R.string.photo_offline_clear)) }
        }
    }
}

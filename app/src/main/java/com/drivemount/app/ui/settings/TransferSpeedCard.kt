package com.drivemount.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.drivemount.app.R
import com.drivemount.app.engine.TransferTuning
import com.drivemount.app.ui.components.DmCard

/** দ্রুত (multi-thread) ট্রান্সফারের সুইচ */
@Composable
fun TransferSpeedCard() {
    val context = LocalContext.current
    var fast by remember { mutableStateOf(TransferTuning.isFast(context)) }
    DmCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.transfer_fast_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.transfer_fast_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = fast, onCheckedChange = { fast = it; TransferTuning.setFast(context, it) })
        }
    }
}

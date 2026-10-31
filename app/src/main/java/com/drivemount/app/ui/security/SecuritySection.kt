package com.drivemount.app.ui.security

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.security.Biometrics
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.theme.spacing

/** Settings-এর "Security" অংশ: অ্যাপ লক ও সংরক্ষিত লগইনের এনক্রিপশন */
@Composable
fun SecuritySection(vm: SecurityViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val protectedConfig by vm.configProtected.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val sp = MaterialTheme.spacing

    val noLockMessage = stringResource(R.string.sec_no_screen_lock)
    val authTitle = stringResource(R.string.sec_confirm_title)

    DmCard(contentPadding = PaddingValues(0.dp)) {
        SettingsItem(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.sec_app_lock),
            subtitle = stringResource(R.string.sec_app_lock_desc),
            trailing = {
                Switch(
                    checked = settings.appLock,
                    onCheckedChange = { want ->
                        if (activity == null) return@Switch
                        if (!Biometrics.isAvailable(context)) {
                            vm.showMessage(noLockMessage)
                        } else {
                            // চালু বা বন্ধ, দুটোতেই আগে পরিচয় যাচাই
                            Biometrics.authenticate(
                                activity = activity,
                                title = authTitle,
                                onSuccess = { vm.setAppLock(want) },
                            )
                        }
                    },
                )
            },
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = sp.md),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        SettingsItem(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.sec_protect_logins),
            subtitle = stringResource(
                if (protectedConfig) R.string.sec_protect_on else R.string.sec_protect_off,
            ),
            trailing = {
                when {
                    busy -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    protectedConfig -> Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    else -> FilledTonalButton(onClick = { vm.enableConfigProtection() }) {
                        Text(stringResource(R.string.sec_turn_on))
                    }
                }
            },
        )
    }
    message?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = sp.sm),
        )
    }
}

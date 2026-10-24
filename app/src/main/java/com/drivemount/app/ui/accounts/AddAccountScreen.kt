package com.drivemount.app.ui.accounts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.data.NewAccount
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SectionTitle
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.components.providerIcon
import com.drivemount.app.ui.components.providerLabel
import com.drivemount.app.ui.theme.spacing

/** দেখানোর ক্রম: আগে Google Drive, Mega, pCloud */
private val Providers = listOf("drive", "mega", "pcloud", "terabox", "webdav", "local")

private const val DRIVE_HELP_URL = "https://rclone.org/drive/#making-your-own-client-id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    vm: AddAccountViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val sp = MaterialTheme.spacing

    var type by remember { mutableStateOf("drive") }
    var name by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var twoFa by remember { mutableStateOf("") }
    var ndus by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is AddState.Done) onBack()
    }
    LaunchedEffect(Unit) {
        vm.openUrl.collect { openBrowser(context, it) }
    }

    val waiting = state as? AddState.WaitingForBrowser
    val busy = state is AddState.Saving || waiting != null
    BackHandler(enabled = waiting != null) { vm.cancel() }

    val valid = name.isNotBlank() && when (type) {
        "webdav" -> url.trim().startsWith("http://") || url.trim().startsWith("https://")
        "mega" -> email.isNotBlank() && pass.isNotEmpty()
        "terabox" -> ndus.isNotBlank()
        else -> true
    }

    fun submit() {
        val account = when (type) {
            "drive" -> NewAccount(
                displayName = name,
                type = "drive",
                params = buildMap {
                    put("scope", "drive")
                    if (clientId.isNotBlank()) put("client_id", clientId.trim())
                    if (clientSecret.isNotBlank()) put("client_secret", clientSecret.trim())
                },
                obscure = true,
                oauth = true,
            )

            "pcloud" -> NewAccount(displayName = name, type = "pcloud", oauth = true)

            "mega" -> NewAccount(
                displayName = name,
                type = "mega",
                params = buildMap {
                    put("user", email.trim())
                    put("pass", pass)
                    if (twoFa.isNotBlank()) put("2fa", twoFa.trim())
                },
                obscure = true,
                verify = true,
            )

            "terabox" -> NewAccount(
                displayName = name,
                type = "terabox",
                params = mapOf("ndus" to ndus.trim()),
                obscure = true,
                verify = true,
            )

            "webdav" -> NewAccount(
                displayName = name,
                type = "webdav",
                params = buildMap {
                    put("url", url.trim())
                    put("vendor", "other")
                    if (user.isNotBlank()) put("user", user.trim())
                    if (pass.isNotEmpty()) put("pass", pass)
                },
                obscure = true,
            )

            else -> NewAccount(displayName = name, type = "local")
        }
        vm.add(account)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_account)) },
                navigationIcon = {
                    IconButton(onClick = { if (waiting != null) vm.cancel() else onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sp.md, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            SectionTitle(stringResource(R.string.add_provider))
            DmCard(contentPadding = PaddingValues(0.dp)) {
                Providers.forEachIndexed { index, t ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sp.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SettingsItem(
                        icon = providerIcon(t),
                        title = providerLabel(t),
                        onClick = if (busy) null else ({ type = t }),
                        trailing = { RadioButton(selected = type == t, onClick = null, enabled = !busy) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.add_details))
            DmCard {
                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Field(stringResource(R.string.add_name_label), name, { name = it }, !busy)

                    when (type) {
                        "drive" -> {
                            Text(
                                text = stringResource(R.string.add_drive_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Field(stringResource(R.string.add_clientid_label), clientId, { clientId = it }, !busy)
                            Field(
                                stringResource(R.string.add_clientsecret_label),
                                clientSecret,
                                { clientSecret = it },
                                !busy,
                                password = true,
                            )
                            TextButton(onClick = { openBrowser(context, DRIVE_HELP_URL) }) {
                                Text(stringResource(R.string.add_drive_help))
                            }
                        }

                        "pcloud" -> Text(
                            text = stringResource(R.string.add_pcloud_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        "terabox" -> {
                            Text(
                                text = stringResource(R.string.add_terabox_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Field(
                                stringResource(R.string.add_ndus_label),
                                ndus,
                                { ndus = it },
                                !busy,
                                password = true,
                            )
                        }

                        "mega" -> {
                            Field(
                                stringResource(R.string.add_email_label),
                                email,
                                { email = it },
                                !busy,
                                keyboardType = KeyboardType.Email,
                            )
                            Field(stringResource(R.string.add_pass_label), pass, { pass = it }, !busy, password = true)
                            Field(
                                stringResource(R.string.add_2fa_label),
                                twoFa,
                                { twoFa = it },
                                !busy,
                                keyboardType = KeyboardType.Number,
                            )
                        }

                        "webdav" -> {
                            Field(
                                stringResource(R.string.add_url_label),
                                url,
                                { url = it },
                                !busy,
                                keyboardType = KeyboardType.Uri,
                            )
                            Field(stringResource(R.string.add_user_label), user, { user = it }, !busy)
                            Field(stringResource(R.string.add_pass_label), pass, { pass = it }, !busy, password = true)
                        }
                    }
                }
            }

            if (waiting != null) {
                DmCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.md),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(
                            text = stringResource(R.string.add_waiting_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(sp.sm))
                    Text(
                        text = stringResource(R.string.add_waiting_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(sp.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                        FilledTonalButton(
                            onClick = { waiting.url?.let { openBrowser(context, it) } },
                            enabled = waiting.url != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.add_open_browser)) }
                        OutlinedButton(
                            onClick = { vm.cancel() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }

            (state as? AddState.Error)?.let {
                Text(
                    text = it.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = sp.sm),
                )
            }

            Button(
                onClick = { submit() },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            state is AddState.Saving -> R.string.add_saving
                            type == "drive" -> R.string.add_signin_google
                            type == "pcloud" -> R.string.add_signin_pcloud
                            else -> R.string.add_button
                        },
                    ),
                )
            }
            Spacer(Modifier.height(sp.lg))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun openBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

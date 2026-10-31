package com.drivemount.app.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.security.VaultKeys
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SectionTitle
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.browser.hasStorageAccess
import com.drivemount.app.ui.browser.requestStorageAccess
import com.drivemount.app.ui.components.providerIcon
import com.drivemount.app.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaultScreen(
    onBack: () -> Unit,
    vm: VaultViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val bases by vm.baseAccounts.collectAsState()
    val sp = MaterialTheme.spacing

    var name by remember { mutableStateOf("My vault") }
    var baseId by remember { mutableStateOf<Long?>(null) }
    var useLocal by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf("DriveMount Vault") }
    var openExisting by remember { mutableStateOf(false) }
    var ownSecret by remember { mutableStateOf(false) } // false: recovery code (create: generate), true: password
    var secretText by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is VaultUiState.Done) onBack()
    }

    // ক্লাউড অ্যাকাউন্ট না থাকলে ফোনের স্টোরেজই একমাত্র পথ
    val local = useLocal || bases.isEmpty()
    val base = if (local) null else (bases.firstOrNull { it.id == baseId } ?: bases.firstOrNull())
    val working = state is VaultUiState.Working

    // ফোনের স্টোরেজে ভল্ট রাখতে "সব ফাইলের অ্যাক্সেস" লাগে
    val context = LocalContext.current
    var accessGranted by remember { mutableStateOf(hasStorageAccess(context)) }
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { accessGranted = hasStorageAccess(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accessGranted = hasStorageAccess(context) }

    val secretOk = when {
        !openExisting && !ownSecret -> true
        !openExisting -> secretText.length >= VaultKeys.MIN_PASSWORD_LENGTH
        !ownSecret -> VaultKeys.normalizeRecoveryCode(secretText) != null
        else -> secretText.isNotEmpty()
    }
    val placeOk = if (local) accessGranted else base != null
    val valid = name.isNotBlank() && placeOk && folder.isNotBlank() && secretOk

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !openExisting,
                    onClick = { openExisting = false; ownSecret = false; secretText = "" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.vault_create_new)) }
                SegmentedButton(
                    selected = openExisting,
                    onClick = { openExisting = true; ownSecret = false; secretText = "" },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.vault_open_existing)) }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_name_label)) },
                singleLine = true,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.vault_where))
            DmCard(contentPadding = PaddingValues(0.dp)) {
                bases.forEach { account ->
                    SettingsItem(
                        icon = providerIcon(account.backendType),
                        providerType = account.backendType,
                        title = account.displayName,
                        onClick = if (working) null else ({ useLocal = false; baseId = account.id }),
                        trailing = { RadioButton(selected = !local && account.id == base?.id, onClick = null) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = sp.md),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                SettingsItem(
                    icon = providerIcon("local"),
                    providerType = "local",
                    title = stringResource(R.string.provider_local),
                    onClick = if (working) null else ({ useLocal = true }),
                    trailing = { RadioButton(selected = local, onClick = null) },
                )
            }
            if (local) {
                Text(
                    text = stringResource(R.string.vault_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = sp.sm),
                )
                if (!accessGranted) {
                    Text(
                        text = stringResource(R.string.vault_local_need_access),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = sp.sm),
                    )
                    FilledTonalButton(
                        onClick = { requestStorageAccess(context, legacyLauncher) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.access_grant)) }
                }
            }
            OutlinedTextField(
                value = folder,
                onValueChange = { folder = it },
                label = { Text(stringResource(R.string.vault_folder)) },
                supportingText = if (local) ({ Text(stringResource(R.string.vault_local_folder_hint)) }) else null,
                singleLine = true,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.vault_secret))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !ownSecret,
                    onClick = { ownSecret = false; secretText = "" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(if (openExisting) R.string.vault_use_code else R.string.vault_generate_code))
                }
                SegmentedButton(
                    selected = ownSecret,
                    onClick = { ownSecret = true; secretText = "" },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.vault_use_password)) }
            }

            if (openExisting || ownSecret) {
                OutlinedTextField(
                    value = secretText,
                    onValueChange = { secretText = it },
                    label = {
                        Text(
                            stringResource(
                                if (!ownSecret) R.string.vault_code_label else R.string.vault_password_label,
                            ),
                        )
                    },
                    supportingText = {
                        Text(
                            if (!openExisting && ownSecret) {
                                stringResource(R.string.vault_password_hint, secretText.length, VaultKeys.MIN_PASSWORD_LENGTH)
                            } else {
                                stringResource(R.string.vault_chars, secretText.length)
                            },
                        )
                    },
                    singleLine = true,
                    enabled = !working,
                    visualTransformation = if (ownSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (ownSecret) KeyboardType.Password else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.vault_generate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = sp.sm),
                )
            }

            Text(
                text = stringResource(R.string.vault_no_recovery),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = sp.sm),
            )

            (state as? VaultUiState.Error)?.let {
                Text(
                    text = it.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = sp.sm),
                )
            }

            Button(
                onClick = {
                    if (openExisting) {
                        vm.open(name, base, folder, useRecoveryCode = !ownSecret, input = secretText)
                    } else {
                        vm.create(name, base, folder, generate = !ownSecret, ownPassword = secretText)
                    }
                },
                enabled = valid && !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            working -> R.string.vault_working
                            openExisting -> R.string.vault_open_button
                            else -> R.string.vault_create_button
                        },
                    ),
                )
            }
            Spacer(Modifier.height(sp.lg))
        }
    }

    (state as? VaultUiState.ShowRecovery)?.let { recovery ->
        RecoveryDialog(code = recovery.code, onDone = { vm.finish() })
    }
}

/** recovery code একবার দেখানো হয়; সংরক্ষণের নিশ্চিতকরণ ছাড়া বন্ধ হয় না */
@Composable
private fun RecoveryDialog(code: String, onDone: () -> Unit) {
    val sp = MaterialTheme.spacing
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(sp.lg),
                verticalArrangement = Arrangement.spacedBy(sp.md),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.vault_recovery_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.vault_recovery_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(sp.sm),
                    )
                }
                FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Text(stringResource(R.string.vault_copy))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saved, onCheckedChange = { saved = it })
                    Text(
                        text = stringResource(R.string.vault_saved_check),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onDone,
                    enabled = saved,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.vault_done)) }
            }
        }
    }
}

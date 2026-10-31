package com.drivemount.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import androidx.fragment.app.FragmentActivity
import com.drivemount.app.security.Biometrics
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    vm: BackupViewModel = viewModel(),
) {
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val sp = MaterialTheme.spacing
    val noPicker = stringResource(R.string.backup_no_picker)

    val gateEnabled by vm.gateEnabled.collectAsState()
    val importFormat by vm.importFormat.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    val context = LocalContext.current
    val screenLockMissing = stringResource(R.string.gate_no_screen_lock)

    var exportDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var keyShown by remember { mutableStateOf(false) }
    var gateSetup by remember { mutableStateOf(false) }
    var gateDisable by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        keyShown = false
        if (uri != null && pending != null) vm.export(uri, pending.password1, pending.password2, pending.backupKey)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareImport(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
            DmCard {
                Text(
                    text = stringResource(R.string.backup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(sp.sm))
                Text(
                    text = stringResource(R.string.backup_warn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(sp.sm))
                Text(
                    text = stringResource(R.string.backup_note_sessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DmCard {
                Text(
                    text = stringResource(R.string.gate_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(sp.xs))
                Text(
                    text = stringResource(if (gateEnabled) R.string.gate_on else R.string.gate_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(sp.sm))
                OutlinedButton(
                    onClick = { if (gateEnabled) gateDisable = true else gateSetup = true },
                    enabled = !busy,
                ) { Text(stringResource(if (gateEnabled) R.string.gate_disable else R.string.gate_enable)) }
            }

            Button(
                onClick = { exportDialog = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_export)) }

            OutlinedButton(
                onClick = {
                    try {
                        openLauncher.launch(arrayOf("*/*"))
                    } catch (e: Exception) {
                        vm.showError(noPicker)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_import)) }

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.md),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
                }
            }

            message?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (exportDialog) {
        ExportFormDialog(
            gateEnabled = gateEnabled,
            checkGate = vm::verifyGate,
            onOk = { p1, p2 ->
                exportDialog = false
                pendingExport = PendingExport(p1, p2, vm.newBackupKey())
                keyShown = true
            },
            onDismiss = { exportDialog = false },
        )
    }

    pendingExport?.let { pending ->
        if (keyShown) {
            BackupKeyDialog(
                backupKey = pending.backupKey,
                onContinue = {
                    keyShown = false
                    val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    try {
                        createLauncher.launch("DriveMount-backup-$day.dmbackup")
                    } catch (e: Exception) {
                        pendingExport = null
                        vm.showError(noPicker)
                    }
                },
                onDismiss = {
                    keyShown = false
                    pendingExport = null
                },
            )
        }
    }

    importFormat?.let { format ->
        ImportFormDialog(
            format = format,
            gateEnabled = gateEnabled,
            checkGate = vm::verifyGate,
            onOk = { p1, p2, key -> vm.importBackup(p1, p2, key) },
            onDismiss = { vm.cancelImport() },
        )
    }

    if (gateSetup) {
        val secret = remember { vm.newGateSecret() }
        GateSetupDialog(
            secret = secret,
            uri = vm.gateUri(secret),
            onConfirm = { code -> vm.enableGate(secret, code) },
            onDismiss = { gateSetup = false },
        )
    }

    if (gateDisable) {
        GateDisableDialog(
            checkGate = vm::verifyGate,
            onDisable = {
                vm.disableGate()
                gateDisable = false
            },
            onUseScreenLock = {
                if (activity == null || !Biometrics.isAvailable(context)) {
                    vm.showError(screenLockMissing)
                    gateDisable = false
                } else {
                    Biometrics.authenticate(
                        activity = activity,
                        title = context.getString(R.string.gate_lost_prompt),
                        onSuccess = {
                            vm.disableGate()
                            gateDisable = false
                        },
                    )
                }
            },
            onDismiss = { gateDisable = false },
        )
    }
}

/** export-এর সময় বানানো তিন গোপন; ফাইল লেখা শেষ হলেই ফেলে দেওয়া হয় */
private data class PendingExport(val password1: String, val password2: String, val backupKey: String)

package com.drivemount.app.ui.backup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.drivemount.app.R
import com.drivemount.app.backup.BackupCrypto
import com.drivemount.app.security.TotpGate
import com.drivemount.app.ui.theme.spacing

/** কোড যাচাইয়ের ফল থেকে দেখানোর বার্তা; ঠিক হলে null */
@Composable
fun gateErrorText(result: TotpGate.Result): String? = when (result) {
    TotpGate.Result.OK -> null
    TotpGate.Result.WRONG -> stringResource(R.string.gate_wrong)
    TotpGate.Result.LOCKED -> stringResource(R.string.gate_locked)
}

@Composable
private fun SecretField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else if (hint != null) ({ Text(hint) }) else null,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        label = { Text(stringResource(R.string.gate_code_label)) },
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Export-এর ফর্ম: প্রথম পাসওয়ার্ড → (Authenticator কোড) → দ্বিতীয় পাসওয়ার্ড।
 * checkGate কোড যাচাই করে ফল দেয়; ঠিক হলে onOk ডাকা হয়।
 */
@Composable
fun ExportFormDialog(
    gateEnabled: Boolean,
    checkGate: (String) -> TotpGate.Result,
    onOk: (password1: String, password2: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    var p1 by remember { mutableStateOf("") }
    var r1 by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var r2 by remember { mutableStateOf("") }
    var gateResult by remember { mutableStateOf(TotpGate.Result.OK) }
    var gateTried by remember { mutableStateOf(false) }

    val min = BackupCrypto.MIN_PASSWORD_LENGTH
    val p1Short = p1.isNotEmpty() && p1.length < min
    val p2Short = p2.isNotEmpty() && p2.length < min
    val m1 = r1.isNotEmpty() && r1 != p1
    val m2 = r2.isNotEmpty() && r2 != p2
    val same = p1.isNotEmpty() && p1 == p2
    val valid = p1.length >= min && p1 == r1 && p2.length >= min && p2 == r2 && !same &&
        (!gateEnabled || code.length == 6)
    val mismatchText = stringResource(R.string.backup_pw_mismatch)
    val shortText = stringResource(R.string.backup_pw_short, min)
    val gateText = if (gateTried) gateErrorText(gateResult) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_pw_title_export)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                SecretField(p1, { p1 = it }, stringResource(R.string.backup_pw_label), if (p1Short) shortText else null)
                SecretField(r1, { r1 = it }, stringResource(R.string.backup_pw_confirm), if (m1) mismatchText else null)
                if (gateEnabled) CodeField(code, { code = it }, gateText)
                SecretField(p2, { p2 = it }, stringResource(R.string.backup_pw2_label), if (p2Short) shortText else null)
                SecretField(r2, { r2 = it }, stringResource(R.string.backup_pw2_confirm), if (m2) mismatchText else null)
                if (same) {
                    Text(
                        stringResource(R.string.backup_pw_same),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val result = if (gateEnabled) checkGate(code) else TotpGate.Result.OK
                    gateResult = result
                    gateTried = true
                    if (result == TotpGate.Result.OK) onOk(p1, p2) else code = ""
                },
            ) { Text(stringResource(R.string.backup_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Backup Key দেখানো: সংরক্ষণ নিশ্চিত না করা পর্যন্ত ফাইল তৈরি হয় না */
@Composable
fun BackupKeyDialog(
    backupKey: String,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_key_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                Text(stringResource(R.string.backup_key_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = backupKey,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(backupKey)) }) {
                    Text(stringResource(R.string.copy))
                }
                Text(
                    stringResource(R.string.backup_key_warn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saved, onCheckedChange = { saved = it })
                    Text(stringResource(R.string.backup_key_saved), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = saved, onClick = onContinue) { Text(stringResource(R.string.backup_key_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Import-এর ফর্ম: প্রথম পাসওয়ার্ড → (Authenticator কোড) → দ্বিতীয় পাসওয়ার্ড → Backup Key।
 * পুরোনো (v1) ফাইলে শুধু একটি পাসওয়ার্ড।
 */
@Composable
fun ImportFormDialog(
    format: Int,
    gateEnabled: Boolean,
    checkGate: (String) -> TotpGate.Result,
    onOk: (password1: String, password2: String, backupKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val v2 = format == 2
    var p1 by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var gateResult by remember { mutableStateOf(TotpGate.Result.OK) }
    var gateTried by remember { mutableStateOf(false) }

    val valid = p1.isNotEmpty() && (!gateEnabled || code.length == 6) && (!v2 || (p2.isNotEmpty() && key.isNotBlank()))
    val gateText = if (gateTried) gateErrorText(gateResult) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_pw_title_import)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                if (!v2) {
                    Text(
                        stringResource(R.string.backup_old_format),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SecretField(p1, { p1 = it }, stringResource(R.string.backup_pw_label))
                if (gateEnabled) CodeField(code, { code = it }, gateText)
                if (v2) {
                    SecretField(p2, { p2 = it }, stringResource(R.string.backup_pw2_label))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(stringResource(R.string.backup_key_label)) },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val result = if (gateEnabled) checkGate(code) else TotpGate.Result.OK
                    gateResult = result
                    gateTried = true
                    if (result == TotpGate.Result.OK) onOk(p1, p2, key) else code = ""
                },
            ) { Text(stringResource(R.string.backup_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Authenticator গেট চালু করা: গোপন কোড দেখানো, অ্যাপে যোগ করা, তারপর একটি কোড দিয়ে যাচাই */
@Composable
fun GateSetupDialog(
    secret: String,
    uri: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var noApp by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gate_setup_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                Text(stringResource(R.string.gate_setup_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = secret.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        } catch (e: ActivityNotFoundException) {
                            noApp = true
                        }
                    }) { Text(stringResource(R.string.gate_open_app)) }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(secret)) }) {
                        Text(stringResource(R.string.copy))
                    }
                }
                if (noApp) {
                    Text(
                        stringResource(R.string.gate_no_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                CodeField(code, { code = it; wrong = false }, if (wrong) stringResource(R.string.gate_wrong) else null)
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length == 6,
                onClick = { if (onConfirm(code)) onDismiss() else { wrong = true; code = "" } },
            ) { Text(stringResource(R.string.gate_enable)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** গেট বন্ধ করা: কোড দিয়ে, অথবা Authenticator হারালে ফোনের স্ক্রিন লক দিয়ে */
@Composable
fun GateDisableDialog(
    checkGate: (String) -> TotpGate.Result,
    onDisable: () -> Unit,
    onUseScreenLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(TotpGate.Result.OK) }
    var tried by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gate_disable_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                CodeField(code, { code = it }, if (tried) gateErrorText(result) else null)
                TextButton(onClick = onUseScreenLock) { Text(stringResource(R.string.gate_lost)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length == 6,
                onClick = {
                    result = checkGate(code)
                    tried = true
                    if (result == TotpGate.Result.OK) onDisable() else code = ""
                },
            ) { Text(stringResource(R.string.gate_disable)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

package com.drivemount.app.ui.accounts

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.Checkbox
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
private val Providers: List<String> = listOf("drive", "gphotos", "gphotos_unlimited", "onedrive", "dropbox", "gcs", "mega", "filen", "protondrive", "gofile", "sia", "ftp", "pcloud", "terabox", "teraplay", "crypt", "union", "webdav", "local") + ProviderSpecs.all.map { it.key }

/** এখনো কাজ করে না, শুধু দেখানোর জন্য (চাপলে "Coming soon") */
private val MoreClouds: List<Pair<String, String>> = listOf(
    "Oracle Object Storage" to "oracle",
    "iCloud Drive" to "icloud",
    "Jottacloud" to "jotta",
    "SugarSync" to "sugarsync",
    "MediaFire" to "mediafire",
    "Icedrive" to "icedrive",
    "Degoo" to "degoo",
    "Baidu Netdisk" to "baidu",
    "Aliyun Drive" to "aliyun",
    "4shared" to "fourshared",
    "Sync.com" to "syncom",
)

private const val DRIVE_HELP_URL = "https://rclone.org/drive/#making-your-own-client-id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    onOpenVault: () -> Unit,
    vm: AddAccountViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val sp = MaterialTheme.spacing

    var type by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var twoFa by remember { mutableStateOf("") }
    var ndus by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var extra2 by remember { mutableStateOf("") }
    val unionSel = remember { mutableStateListOf<String>() }
    var unionPolicy by remember { mutableStateOf("mfs") }
    val unionCandidates by vm.unionCandidates.collectAsState()
    val gv = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val spec = ProviderSpecs.get(type)

    LaunchedEffect(state) {
        if (state is AddState.Done) onBack()
    }
    LaunchedEffect(Unit) {
        vm.openUrl.collect { openBrowser(context, it) }
    }

    val waiting = state as? AddState.WaitingForBrowser
    val busy = state is AddState.Saving || waiting != null
    BackHandler(enabled = waiting != null) { vm.cancel() }

    val valid = type.isNotEmpty() && type != "gphotos_unlimited" && name.isNotBlank() && when (type) {
        "webdav" -> url.trim().startsWith("http://") || url.trim().startsWith("https://")
        "mega", "filen", "protondrive" -> email.isNotBlank() && pass.isNotEmpty()
        "gcs" -> extra.isNotBlank() || extra2.isNotBlank()
        "union" -> unionSel.size >= 2
        "gofile" -> pass.isNotBlank()
        "sia", "ftp" -> url.isNotBlank()
        "terabox", "teraplay" -> ndus.isNotBlank()
        else -> spec?.fields?.all { it.optional || !gv[it.param].isNullOrBlank() } ?: true
    }

    fun submit() {
        val account = if (spec != null) {
            NewAccount(
                displayName = name,
                type = spec.type,
                params = buildMap {
                    putAll(spec.fixed)
                    spec.fields.forEach { fld ->
                        val v = (gv[fld.param] ?: fld.default).trim()
                        if (v.isNotEmpty()) put(fld.param, v)
                    }
                    if (spec.oauth) {
                        if (clientId.isNotBlank()) put("client_id", clientId.trim())
                        if (clientSecret.isNotBlank()) put("client_secret", clientSecret.trim())
                    }
                },
                obscure = true,
                oauth = spec.oauth,
                verify = spec.verify,
            )
        } else when (type) {
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

            "gphotos" -> NewAccount(
                displayName = name,
                type = "gphotos",
                params = buildMap {
                    if (clientId.isNotBlank()) put("client_id", clientId.trim())
                    if (clientSecret.isNotBlank()) put("client_secret", clientSecret.trim())
                },
                obscure = true,
                oauth = true,
            )

            "onedrive", "dropbox" -> NewAccount(
                displayName = name,
                type = type,
                params = buildMap {
                    if (clientId.isNotBlank()) put("client_id", clientId.trim())
                    if (clientSecret.isNotBlank()) put("client_secret", clientSecret.trim())
                },
                obscure = true,
                oauth = true,
            )

            "gcs" -> NewAccount(
                displayName = name,
                type = "google cloud storage",
                params = buildMap {
                    if (extra.isNotBlank()) put("project_number", extra.trim())
                    if (extra2.isNotBlank()) {
                        put("service_account_credentials", extra2.trim())
                    } else {
                        if (clientId.isNotBlank()) put("client_id", clientId.trim())
                        if (clientSecret.isNotBlank()) put("client_secret", clientSecret.trim())
                    }
                },
                obscure = true,
                oauth = extra2.isBlank(),
            )

            "protondrive" -> NewAccount(
                displayName = name,
                type = "protondrive",
                params = buildMap {
                    put("username", email.trim())
                    put("password", pass)
                    if (twoFa.isNotBlank()) put("2fa", twoFa.trim())
                },
                obscure = true,
                verify = true,
            )

            "union" -> NewAccount(
                displayName = name,
                type = "union",
                params = mapOf(
                    "upstreams" to unionSel.joinToString(" ") { "$it:" },
                    "create_policy" to unionPolicy,
                ),
            )

            "gofile" -> NewAccount(
                displayName = name,
                type = "gofile",
                params = mapOf("access_token" to pass.trim()),
                obscure = true,
            )

            "sia" -> NewAccount(
                displayName = name,
                type = "sia",
                params = buildMap {
                    put("api_url", url.trim())
                    if (pass.isNotEmpty()) put("api_password", pass)
                },
                obscure = true,
            )

            "ftp" -> {
                val raw = url.trim()
                val lower = raw.lowercase()
                val host = raw.substringAfter("://").substringBefore("/").substringBefore(":")
                val port = extra.trim().ifBlank { raw.substringAfter("://").substringBefore("/").substringAfter(":", "") }
                NewAccount(
                    displayName = name,
                    type = "ftp",
                    params = buildMap {
                        put("host", host)
                        put("user", user.trim().ifBlank { "anonymous" })
                        if (port.isNotBlank()) put("port", port)
                        if (pass.isNotEmpty()) put("pass", pass)
                        if (lower.startsWith("ftps://")) put("tls", "true")
                        if (lower.startsWith("ftpes://")) put("explicit_tls", "true")
                    },
                    obscure = true,
                )
            }

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

            "filen" -> NewAccount(
                displayName = name,
                type = "filen",
                params = buildMap {
                    put("email", email.trim())
                    put("password", pass)
                    if (twoFa.isNotBlank()) put("_2fa", twoFa.trim())
                },
                obscure = true,
                verify = true,
            )

            "terabox", "teraplay" -> NewAccount(
                displayName = name,
                type = "terabox",
                params = mapOf("ndus" to ndus.trim()),
                obscure = true,
                verify = true,
                teraplay = type == "teraplay",
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
                            imageVector = Lucide.LuArrowLeft,
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
            val submitArea: @Composable () -> Unit = {
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
                            type == "drive" || type == "gphotos" -> R.string.add_signin_google
                            type == "pcloud" -> R.string.add_signin_pcloud
                            type == "onedrive" || type == "dropbox" || spec?.oauth == true || (type == "gcs" && extra2.isBlank()) -> R.string.add_signin_generic
                            else -> R.string.add_button
                        },
                    ),
                )
            }
            }

            val detailsContent: @Composable () -> Unit = {
                if (type == "gphotos_unlimited") GPhotosPluginPanel() else Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Field(stringResource(R.string.add_name_label), name, { name = it }, !busy)

                    when (type) {
                        "drive", "gphotos" -> {
                            Text(
                                text = stringResource(if (type == "gphotos") R.string.add_gphotos_hint else R.string.add_drive_hint),
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

                        "terabox", "teraplay" -> {
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

                        "mega", "filen", "protondrive" -> {
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

                        "onedrive", "dropbox", "gcs" -> {
                            if (type == "gcs") {
                                Field(stringResource(R.string.add_project_label), extra, { extra = it }, !busy)
                                Field(stringResource(R.string.add_sa_label), extra2, { extra2 = it }, !busy, password = true)
                            }
                            Field(stringResource(R.string.add_clientid_label), clientId, { clientId = it }, !busy)
                            Field(stringResource(R.string.add_clientsecret_label), clientSecret, { clientSecret = it }, !busy, password = true)
                        }

                        "union" -> {
                            UnionPicker(unionCandidates, unionSel, unionPolicy, { unionPolicy = it }, enabled = !busy)
                        }

                        "gofile" -> {
                            Field(stringResource(R.string.add_token_label), pass, { pass = it }, !busy, password = true)
                        }

                        "sia", "ftp" -> {
                            Field(
                                stringResource(if (type == "sia") R.string.add_url_label else R.string.add_host_label),
                                url,
                                { url = it },
                                !busy,
                                keyboardType = KeyboardType.Uri,
                            )
                            if (type == "ftp") {
                                Field(stringResource(R.string.add_port_label), extra, { extra = it }, !busy, keyboardType = KeyboardType.Number)
                                Field(stringResource(R.string.add_user_label), user, { user = it }, !busy)
                            }
                            Field(stringResource(R.string.add_pass_label), pass, { pass = it }, !busy, password = true)
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
                        else -> if (spec != null) {
                            spec.fields.forEach { fld ->
                                Field(fld.label, gv[fld.param] ?: fld.default, { gv[fld.param] = it }, !busy, password = fld.secret)
                            }
                            if (spec.oauth) {
                                Field(stringResource(R.string.add_clientid_label), clientId, { clientId = it }, !busy)
                                Field(stringResource(R.string.add_clientsecret_label), clientSecret, { clientSecret = it }, !busy, password = true)
                            }
                        }
                    }

                    submitArea()
                }
            }

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
                        providerType = t,
                        title = providerLabel(t),
                        onClick = if (busy) null else ({ if (t == "crypt") onOpenVault() else type = t }),
                        trailing = { RadioButton(selected = type == t, onClick = null, enabled = !busy) },
                    )
                    if (type == t) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sp.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Column(Modifier.fillMaxWidth().padding(sp.md)) { detailsContent() }
                    }
                }
            }

            SectionTitle(stringResource(R.string.add_more_clouds))
            DmCard(contentPadding = PaddingValues(0.dp)) {
                MoreClouds.forEachIndexed { index, (n, slug) ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sp.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    val res = remember(slug) {
                        context.resources.getIdentifier("ic_cloud_$slug", "drawable", context.packageName)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.qa_coming_soon),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(horizontal = sp.md, vertical = 12.dp),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            if (res != 0) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(res),
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        Text(n, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.qa_coming_soon),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

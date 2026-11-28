package com.drivemount.app.ui.galaxy

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Sparkles as LuSparkles
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.galaxy.Galaxy
import com.drivemount.app.data.galaxy.GalaxyConfig
import com.drivemount.app.data.galaxy.GalaxyRepository
import com.drivemount.app.ui.components.ProviderIcon
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.components.providerLabel
import kotlinx.coroutines.launch

private val GalaxyA = Color(0xFF4A1FB8)
private val GalaxyB = Color(0xFF1D4ED8)
private val GalaxyC = Color(0xFFC026D3)
private val GalaxyGlow = Color(0xFF8B5CF6)

/** তারার মতো ছোট বিন্দুর স্থির অবস্থান (কার্ডের মাপের ভগ্নাংশ, ব্যাসার্ধ) */
private val Stars = listOf(
    Triple(0.08f, 0.18f, 1.6f), Triple(0.21f, 0.72f, 1.1f), Triple(0.34f, 0.12f, 1.2f), Triple(0.47f, 0.86f, 1.5f),
    Triple(0.58f, 0.25f, 1.0f), Triple(0.69f, 0.64f, 1.7f), Triple(0.77f, 0.1f, 1.2f), Triple(0.86f, 0.42f, 1.0f),
    Triple(0.93f, 0.8f, 1.5f), Triple(0.14f, 0.45f, 0.9f), Triple(0.52f, 0.52f, 0.9f), Triple(0.97f, 0.2f, 1.1f),
)

/**
 * Cloud ট্যাবের ওপরের "Galaxy Drive" কার্ড: গ্রেডিয়েন্ট + glow। সেট-আপ না হলে "Set up", হলে জায়গার হিসাব ও কপির অবস্থা।
 * glow-এর স্পন্দন শুধু আঁকার ধাপে (recompose ছাড়া), তাই হালকা।
 */
@Composable
fun GalaxyCard(
    config: GalaxyConfig,
    accounts: List<AccountEntity>,
    galaxyAccount: AccountEntity?,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    vaults: List<AccountEntity> = emptyList(),
    onCreateVault: () -> Unit = {},
    onOpenVault: (AccountEntity) -> Unit = {},
    onRenameVault: (AccountEntity) -> Unit = {},
    onDeleteVault: (AccountEntity) -> Unit = {},
    trashCount: Int = 0,
    onOpenTrash: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    var menuFor by remember { mutableStateOf<Long?>(null) }
    val cap = if (galaxyAccount != null) GalaxyRepository.capacity(config, accounts) else null
    val shape = RoundedCornerShape(26.dp)
    val pulse by rememberInfiniteTransition(label = "galaxy").animateFloat(
        initialValue = 0.20f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = GalaxyGlow, spotColor = GalaxyGlow)
            .clip(shape)
            .background(Brush.linearGradient(listOf(GalaxyA, GalaxyB, GalaxyC)))
            .drawBehind {
                // স্পন্দিত আলোর বৃত্ত (ডান-ওপরে) + তারা
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = pulse), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.12f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.88f, size.height * 0.12f),
                )
                Stars.forEach { (x, y, r) ->
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = r.dp.toPx(), center = Offset(size.width * x, size.height * y))
                }
            }
            .clickable(onClick = if (galaxyAccount != null) onOpen else onManage),
    ) {
        // কম্প্যাক্ট: আগে প্রায় অর্ধেক পর্দা নিত; এখন শিরোনাম+ছোট বিবরণ, জায়গার বার, প্ল্যাটফর্ম, আর এক সারিতে সব বোতাম
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.LuSparkles, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.galaxy_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (galaxyAccount != null && cap != null) {
                        val copies = if (cap.platforms >= 2) stringResource(R.string.galaxy_copies, cap.platforms) else stringResource(R.string.galaxy_one_platform)
                        val enc = stringResource(if (vaults.isEmpty()) R.string.galaxy_not_encrypted else R.string.galaxy_encrypted)
                        Text(
                            text = "$copies · $enc",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = if (vaults.isEmpty()) Color(0xFFFFE08A) else Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                        )
                    }
                }
            }
            if (galaxyAccount == null || cap == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.galaxy_pitch),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    Button(
                        onClick = onManage,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = GalaxyA),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                    ) { Text(stringResource(R.string.galaxy_setup), style = MaterialTheme.typography.labelLarge) }
                }
            } else {
                val used = (cap.total - cap.free).coerceAtLeast(0L)
                Text(
                    text = stringResource(R.string.galaxy_free_of, formatBytes(cap.free), formatBytes(cap.total)),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                LinearProgressIndicator(
                    progress = { if (cap.total > 0) (used.toFloat() / cap.total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                val platformText = config.on.sorted().mapNotNull { t ->
                    val n = config.membersOf(t).count { id -> accounts.any { it.id == id && it.backendType == t } }
                    if (n == 0) null else t to n
                }
                // প্ল্যাটফর্ম চিপ আর নিচের বোতাম: এক সারিতে, বেশি হলে পাশে স্ক্রল
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    platformText.forEach { (t, n) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ProviderIcon(type = t, modifier = Modifier.size(12.dp), tint = Color.White)
                            Text("${providerLabel(t)} ×$n", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    vaults.forEach { v ->
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .clickable { menuFor = v.id }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Lucide.LuLock, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(v.displayName, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
                            }
                            DropdownMenu(expanded = menuFor == v.id, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.galaxy_vault_open)) }, onClick = { menuFor = null; onOpenVault(v) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.galaxy_vault_rename)) }, onClick = { menuFor = null; onRenameVault(v) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.galaxy_vault_delete)) }, onClick = { menuFor = null; onDeleteVault(v) })
                            }
                        }
                    }
                    val tight = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    TextButton(onClick = onCreateVault, contentPadding = tight) {
                        Icon(Lucide.LuLock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(R.string.galaxy_create_vault),
                            color = if (vaults.isEmpty()) Color(0xFFFFE08A) else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(onClick = onManage, contentPadding = tight) {
                        Text(stringResource(R.string.galaxy_manage), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onImport, contentPadding = tight) {
                        Text(stringResource(R.string.galaxy_import), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onOpenTrash, contentPadding = tight) {
                        Text(
                            stringResource(R.string.galaxy_trash) + if (trashCount > 0) " · $trashCount" else "",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** প্ল্যাটফর্ম অন/অফ আর কোন কোন অ্যাকাউন্ট থাকবে তা বাছাইয়ের শিট */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxySheet(
    config: GalaxyConfig,
    accounts: List<AccountEntity>,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DriveMountApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // বাছাই-করা অবস্থা (সংরক্ষণ চাপার আগ পর্যন্ত শুধু এখানে)
    var on by remember { mutableStateOf(config.on) }
    var members by remember { mutableStateOf(config.members) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val byType = accounts.filter { Galaxy.eligible(it.backendType) && it.remoteName != Galaxy.REMOTE }.groupBy { it.backendType }
    val draft = GalaxyConfig(on, members)
    val cap = GalaxyRepository.capacity(draft, accounts)
    val configured = config.on.isNotEmpty()

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.galaxy_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.galaxy_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (byType.isEmpty()) {
                Text(stringResource(R.string.galaxy_no_accounts), style = MaterialTheme.typography.bodyMedium)
            }
            byType.forEach { (type, list) ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderIcon(type = type, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(providerLabel(type), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.galaxy_accounts_count, list.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = type in on,
                        onCheckedChange = { checked ->
                            if (checked) {
                                on = on + type
                                // প্রথমবার চালু করলে ওই ধরনের সব অ্যাকাউন্ট আগে থেকে টিক দেওয়া; পরে বাদ দেওয়া যায়
                                if (members[type].isNullOrEmpty()) members = members + (type to list.map { it.id })
                            } else {
                                on = on - type
                            }
                        },
                    )
                }
                if (type in on) {
                    list.forEach { acc ->
                        val checked = acc.id in members[type].orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cur = members[type].orEmpty()
                                    members = members + (type to if (checked) cur - acc.id else cur + acc.id)
                                }
                                .padding(start = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(acc.displayName, style = MaterialTheme.typography.bodyLarge)
                                val t = acc.totalBytes
                                if (t != null) {
                                    Text(
                                        formatBytes(t),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TextButton(onClick = { onDismiss(); onAddAccount() }) { Text(stringResource(R.string.galaxy_add_account)) }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (cap != null) {
                Text(
                    stringResource(R.string.galaxy_preview, formatBytes(cap.total), cap.platforms),
                    style = MaterialTheme.typography.titleSmall,
                )
                cap.limitingType?.let {
                    if (cap.platforms >= 2) {
                        Text(
                            stringResource(R.string.galaxy_limited_by, providerLabel(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (cap.platforms < 2) {
                    Text(stringResource(R.string.galaxy_one_platform_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (cap.partial) {
                    Text(stringResource(R.string.galaxy_partial), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && cap != null,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        app.galaxy.apply(draft).fold(
                            onSuccess = { saving = false; onDismiss() },
                            onFailure = { saving = false; error = it.message ?: it.toString() },
                        )
                    }
                },
            ) { Text(stringResource(if (saving) R.string.galaxy_saving else R.string.galaxy_save)) }

            if (configured && (cap?.platforms ?: 0) >= 2) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            val n = app.galaxy.syncCopies()
                            Toast.makeText(context, context.getString(R.string.galaxy_sync_queued, n), Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text(stringResource(R.string.galaxy_sync)) }
                Text(stringResource(R.string.galaxy_sync_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (configured) {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            app.galaxy.apply(GalaxyConfig()).onFailure { error = it.message }
                            saving = false
                            onDismiss()
                        }
                    },
                ) { Text(stringResource(R.string.galaxy_disable), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Galaxy ট্র্যাশ: ৩০ দিনের মধ্যে ফেরত আনা বা আসলেই মোছা */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyTrashSheet(accounts: List<AccountEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DriveMountApp
    val scope = rememberCoroutineScope()
    val items by app.galaxy.trash.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busy by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { app.galaxy.purgeExpired() } }

    fun run(block: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        scope.launch {
            block().onFailure { Toast.makeText(context, it.message ?: it.toString(), Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.galaxy_trash), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            if (!confirmEmpty) confirmEmpty = true
                            else {
                                confirmEmpty = false
                                run { app.galaxy.emptyTrash(); Result.success(Unit) }
                            }
                        },
                    ) { Text(stringResource(R.string.galaxy_trash_empty_all), color = MaterialTheme.colorScheme.error) }
                }
            }
            Text(stringResource(R.string.galaxy_trash_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (confirmEmpty) {
                Text(stringResource(R.string.galaxy_trash_confirm_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (items.isEmpty()) {
                Text(stringResource(R.string.galaxy_trash_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
            }
            items.sortedByDescending { it.at }.forEach { item ->
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                val daysLeft = (Galaxy.TRASH_DAYS - (now - item.at) / (24L * 3600 * 1000)).toInt().coerceAtLeast(0)
                val place = accounts.firstOrNull { it.id == item.accountId }?.displayName.orEmpty()
                Column(Modifier.fillMaxWidth()) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull<String>(
                            place.takeIf { it.isNotEmpty() },
                            stringResource(R.string.galaxy_trash_from, item.origPath.substringBeforeLast('/', "")),
                            item.size?.let { formatBytes(it) },
                            stringResource(R.string.galaxy_trash_days_left, daysLeft),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(enabled = !busy, onClick = { run { app.galaxy.restore(item) } }) { Text(stringResource(R.string.galaxy_trash_restore)) }
                        TextButton(enabled = !busy, onClick = { run { app.galaxy.deleteForever(item) } }) {
                            Text(stringResource(R.string.galaxy_trash_forever), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** পুরোনো ছড়ানো ডেটা Galaxy-তে আনার শিট: উৎস ফোল্ডার, গন্তব্য (ভল্ট বা সাধারণ), অনুমতি দিয়ে কপি শুরু */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyImportSheet(accounts: List<AccountEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DriveMountApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var src by remember { mutableStateOf<Pair<AccountEntity, String>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var destId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Galaxy নিজে আর তার ওপরের ভল্টগুলো; ভল্ট আগে (সুপারিশ)
    val dests by produceState(emptyList<AccountEntity>(), accounts) {
        val list = accounts.filter { it.remoteName == Galaxy.REMOTE || (it.backendType == "crypt" && runCatching { app.galaxy.isGalaxyBacked(it) }.getOrDefault(false)) }
        value = list.sortedBy { if (it.remoteName == Galaxy.REMOTE) 1 else 0 }
    }
    val dest = dests.firstOrNull { it.id == destId } ?: dests.firstOrNull()

    if (picking) {
        com.drivemount.app.ui.videos.FolderPickerDialog(
            onPick = { acc, path ->
                picking = false
                scope.launch {
                    if (runCatching { app.galaxy.isGalaxyBacked(acc) }.getOrDefault(false)) {
                        error = context.getString(R.string.galaxy_import_in_galaxy)
                    } else {
                        error = null
                        src = acc to path
                    }
                }
            },
            onDismiss = { picking = false },
        )
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.galaxy_import_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.galaxy_import_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.galaxy_import_source), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = src?.let { it.first.displayName + "/" + it.second } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                OutlinedButton(onClick = { picking = true }, enabled = !busy) {
                    Text(stringResource(if (src == null) R.string.galaxy_import_choose else R.string.galaxy_import_change))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.galaxy_import_dest), style = MaterialTheme.typography.titleSmall)
            dests.forEach { d ->
                val vault = d.remoteName != Galaxy.REMOTE
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { destId = d.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = d.id == dest?.id, onClick = null)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(d.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (vault) R.string.galaxy_import_encrypted else R.string.galaxy_import_plain),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.galaxy_import_consent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && src != null && dest != null,
                onClick = {
                    val s = src ?: return@Button
                    val d = dest ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        app.galaxy.importFolder(s.first, s.second, d).fold(
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.galaxy_import_queued), Toast.LENGTH_LONG).show()
                                busy = false
                                onDismiss()
                            },
                            onFailure = { busy = false; error = it.message ?: it.toString() },
                        )
                    }
                },
            ) { Text(stringResource(R.string.galaxy_import_start)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

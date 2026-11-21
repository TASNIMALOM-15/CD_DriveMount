package com.drivemount.app.ui.home

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles as LuSparkles
import com.composables.icons.lucide.UserPlus as LuUserPlus
import com.composables.icons.lucide.AppWindow as LuAppWindow
import com.composables.icons.lucide.ArrowUpDown as LuArrowUpDown
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.EllipsisVertical as LuEllipsisVertical
import com.composables.icons.lucide.Layers as LuLayers
import com.composables.icons.lucide.FileText as LuFileText
import com.composables.icons.lucide.FolderOpen as LuFolderOpen
import com.composables.icons.lucide.Image as LuImage
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.LockKeyhole as LuLockKeyhole
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.Pencil as LuPencil
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Settings as LuSettings
import com.composables.icons.lucide.Star as LuStar
import com.composables.icons.lucide.Trash2 as LuTrash2
import com.composables.icons.lucide.Video as LuVideo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.drivemount.app.CrashReporter
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.saf.openInFilesApp
import com.drivemount.app.ui.accounts.AccountsViewModel
import com.drivemount.app.ui.components.CrashReportDialog
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.ProviderIcon
import com.drivemount.app.ui.components.providerGlow
import com.drivemount.app.ui.components.GlowCard
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.components.providerIcon
import com.drivemount.app.ui.components.providerLabel
import com.drivemount.app.ui.theme.spacing
import com.drivemount.app.ui.settings.StorageDetailsDialog
import com.drivemount.app.ui.settings.StorageSummaryCard
import com.drivemount.app.ui.components.SectionTitle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSync: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAudios: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenApks: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenClouds: () -> Unit = {},
    cloudsOnly: Boolean = false,
    vm: AccountsViewModel = viewModel(),
) {
    val groups by vm.groups.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val activeTransfers by vm.activeTransfers.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var crashReport by remember { mutableStateOf(CrashReporter.readAndClear(context)) }
    val sp = MaterialTheme.spacing

    var showStorage by remember { mutableStateOf(false) }
    var providerFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val allAccounts by vm.accounts.collectAsState()
    val cloudAccounts = allAccounts.orEmpty().filter { it.backendType != "crypt" && it.remoteName != com.drivemount.app.data.galaxy.Galaxy.REMOTE }
    val galaxyRepo = (context.applicationContext as com.drivemount.app.DriveMountApp).galaxy
    val galaxyCfg by galaxyRepo.config.collectAsState()
    val galaxyAccount = allAccounts.orEmpty().firstOrNull { it.remoteName == com.drivemount.app.data.galaxy.Galaxy.REMOTE }
    var showGalaxy by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val trashItems by galaxyRepo.trash.collectAsState()
    var galaxyRefreshing by remember { mutableStateOf(false) }
    val soon = stringResource(R.string.qa_coming_soon)
    val onSoon: () -> Unit = { scope.launch { snackbar.showSnackbar(soon) } }

    var renameTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var unionTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var newName by remember(renameTarget) { mutableStateOf(renameTarget?.displayName.orEmpty()) }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fabActions = if (cloudsOnly) {
        listOf(
            com.drivemount.app.ui.components.FabAction("add_account", Lucide.LuUserPlus, stringResource(R.string.add_account), onAddAccount),
            com.drivemount.app.ui.components.FabAction("galaxy", Lucide.LuSparkles, stringResource(R.string.galaxy_manage)) { showGalaxy = true },
            com.drivemount.app.ui.components.FabAction("search", Lucide.LuSearch, stringResource(R.string.browser_search), onOpenSearch),
            com.drivemount.app.ui.components.FabAction("transfers", Lucide.LuArrowUpDown, stringResource(R.string.transfers), onOpenTransfers),
        )
    } else {
        listOf(
            com.drivemount.app.ui.components.FabAction("search", Lucide.LuSearch, stringResource(R.string.browser_search), onOpenSearch),
            com.drivemount.app.ui.components.FabAction("add_account", Lucide.LuUserPlus, stringResource(R.string.add_account), onAddAccount),
            com.drivemount.app.ui.components.FabAction("sync", Lucide.LuRefreshCcw, stringResource(R.string.sync_title), onOpenSync),
            com.drivemount.app.ui.components.FabAction("transfers", Lucide.LuArrowUpDown, stringResource(R.string.transfers), onOpenTransfers),
            com.drivemount.app.ui.components.FabAction("settings", Lucide.LuSettings, stringResource(R.string.settings), onOpenSettings),
        )
    }

    val fabOn = com.drivemount.app.ui.components.rememberFabVisibility()
    val fabPage = if (cloudsOnly) com.drivemount.app.ui.components.FabCatalog.CLOUDS else com.drivemount.app.ui.components.FabCatalog.HOME
    val homeFab = com.drivemount.app.ui.components.FabCatalog.HOME
    Scaffold(
        floatingActionButton = {
            com.drivemount.app.ui.components.ActionFab(
                page = if (cloudsOnly) com.drivemount.app.ui.components.FabCatalog.CLOUDS else com.drivemount.app.ui.components.FabCatalog.HOME,
                actions = fabActions,
                collapse = listState.isScrollInProgress,
            )
        },
        topBar = { com.drivemount.app.ui.components.TopBarIcons {
            CenterAlignedTopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                title = { Text(stringResource(if (cloudsOnly) R.string.tab_clouds else R.string.app_name)) },
                actions = {
                    if (!fabOn(fabPage, "search")) IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Lucide.LuSearch,
                            contentDescription = stringResource(R.string.browser_search),
                        )
                    }
                    if (cloudsOnly || !fabOn(homeFab, "sync")) IconButton(onClick = onOpenSync) {
                        Icon(
                            imageVector = Lucide.LuRefreshCcw,
                            contentDescription = stringResource(R.string.sync_title),
                        )
                    }
                    if (!fabOn(fabPage, "transfers")) IconButton(onClick = onOpenTransfers) {
                        BadgedBox(
                            badge = {
                                if (activeTransfers > 0) Badge { Text(activeTransfers.toString()) }
                            },
                        ) {
                            Icon(
                                imageVector = Lucide.LuArrowUpDown,
                                contentDescription = stringResource(R.string.transfers),
                            )
                        }
                    }
                    if (cloudsOnly || !fabOn(homeFab, "settings")) IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Lucide.LuSettings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        }},
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val cloudOrder = (context.applicationContext as com.drivemount.app.DriveMountApp).cloudOrder
        val savedOrder by cloudOrder.order.collectAsState()
        // ক্লাউড সবসময় ধরন অনুযায়ী সাজানো (সিরিয়াল Settings-এ বদলানো যায়)
        val list = groups?.let { g -> androidx.compose.runtime.remember(g, savedOrder) { cloudOrder.sort(g) { it.base.backendType } } }
        if (list == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            com.drivemount.app.ui.components.RefreshBox(
                refreshing = refreshing.isNotEmpty() || galaxyRefreshing,
                onRefresh = {
                    cloudAccounts.forEach { vm.refreshUsage(it) }
                    if (cloudsOnly) scope.launch {
                        galaxyRefreshing = true
                        runCatching { galaxyRepo.refreshAll() }
                        galaxyRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                topInset = padding.calculateTopPadding(),
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = sp.md,
                    end = sp.md,
                    top = padding.calculateTopPadding() + sp.sm,
                    bottom = padding.calculateBottomPadding() + LocalBottomBarInset.current + sp.md,
                ),
                verticalArrangement = Arrangement.spacedBy(sp.md),
            ) {
                if (!cloudsOnly) item(key = "storage") {
                    StorageSummaryCard(accounts = cloudAccounts, onClick = { showStorage = true })
                }
                if (!cloudsOnly) item(key = "quick") {
                    Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                        SectionTitle(stringResource(R.string.home_quick_access))
                        val items = listOf(
                            QuickItem(Lucide.LuVideo, R.string.qa_videos, onOpenVideos),
                            QuickItem(Lucide.LuMusic, R.string.qa_audios, onOpenAudios),
                            QuickItem(Lucide.LuImage, R.string.qa_photos, onOpenPhotos),
                            QuickItem(Lucide.LuFileText, R.string.qa_docs, onOpenDocs),
                            QuickItem(Lucide.LuAppWindow, R.string.qa_apks, onOpenApks),
                            QuickItem(Lucide.LuStar, R.string.qa_favorites, onOpenFavorites),
                            QuickItem(Lucide.LuLockKeyhole, R.string.qa_locked, onSoon),
                            QuickItem(Lucide.LuTrash2, R.string.qa_trash, onSoon),
                        )
                        items.chunked(4).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { q ->
                                    QuickAccessCell(q, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (!cloudsOnly) item(key = "recent") {
                    val app = context.applicationContext as com.drivemount.app.DriveMountApp
                    val recent by app.recent.items.collectAsState()
                    val shown = recent.take(6)
                    if (shown.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SectionTitle(stringResource(R.string.home_recent))
                            shown.forEach { r ->
                                val accName = allAccounts.orEmpty().firstOrNull { it.id == r.accountId }?.displayName
                                if (accName != null) {
                                    val kindText = stringResource(
                                        when (r.kind) {
                                            com.drivemount.app.data.RecentStore.Kind.PLAYED -> R.string.recent_played
                                            com.drivemount.app.data.RecentStore.Kind.DOWNLOADED -> R.string.recent_downloaded
                                            com.drivemount.app.data.RecentStore.Kind.UPLOADED -> R.string.recent_uploaded
                                            com.drivemount.app.data.RecentStore.Kind.OFFLINE -> R.string.recent_offline
                                        },
                                    )
                                    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
                                        r.at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                                    ).toString()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable {
                                                app.openTarget.value = com.drivemount.app.OpenTarget(
                                                    r.accountId, r.path.substringBeforeLast('/', ""), r.path,
                                                )
                                            }
                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = com.drivemount.app.ui.components.fileIcon(
                                                false, r.name, com.drivemount.app.engine.guessMime(r.name, ""),
                                            ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "$kindText · $accName · $ago",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val filterTypes = cloudAccounts.map { it.backendType }.distinct().let { t ->
                    val ord = cloudOrder.effective(t)
                    t.sortedBy { ord.indexOf(it) }
                }
                // বাছাই করা প্ল্যাটফর্মের কোনো ক্লাউড আর না থাকলে ফিল্টার আপনাআপনি সরে যায়
                val activeFilter = providerFilter?.takeIf { it in filterTypes }
                if (cloudsOnly && filterTypes.size > 1) item(key = "provider_filter") {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item(key = "all") {
                            ProviderFilterChip(stringResource(R.string.filter_all), activeFilter == null) { providerFilter = null }
                        }
                        items(filterTypes, key = { it }) { type ->
                            ProviderFilterChip(providerLabel(type), activeFilter == type) { providerFilter = type }
                        }
                    }
                }
                if (cloudsOnly && cloudAccounts.isNotEmpty() && activeFilter == null) item(key = "galaxy") {
                    com.drivemount.app.ui.galaxy.GalaxyCard(
                        config = galaxyCfg,
                        accounts = allAccounts.orEmpty(),
                        galaxyAccount = galaxyAccount,
                        onOpen = { galaxyAccount?.let { onOpenAccount(it.id) } },
                        onManage = { showGalaxy = true },
                        vaults = list.firstOrNull { it.base.remoteName == com.drivemount.app.data.galaxy.Galaxy.REMOTE }?.vaults.orEmpty(),
                        onCreateVault = { galaxyAccount?.let { (context.applicationContext as com.drivemount.app.DriveMountApp).vaultRequest.value = it.id } },
                        onOpenVault = { onOpenAccount(it.id) },
                        onRenameVault = { renameTarget = it },
                        onDeleteVault = { deleteTarget = it },
                        trashCount = trashItems.size,
                        onOpenTrash = { showTrash = true },
                        onImport = { showImport = true },
                    )
                }
                if (list.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(onAdd = onAddAccount, onImport = onOpenBackup)
                    }
                } else if (!cloudsOnly) {
                    item(key = "clouds_title") {
                        SectionTitle(stringResource(R.string.home_connected_clouds))
                    }
                    item(key = "clouds_summary") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.home_connected_count, cloudAccounts.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            TextButton(onClick = onOpenClouds) { Text(stringResource(R.string.home_manage_all)) }
                        }
                    }
                } else {
                    items(
                        list.filter {
                            it.base.remoteName != com.drivemount.app.data.galaxy.Galaxy.REMOTE &&
                                (activeFilter == null || it.base.backendType == activeFilter)
                        },
                        key = { it.base.id },
                    ) { group ->
                        val account = group.base
                        fun openInFiles(target: AccountEntity) {
                            if (!openInFilesApp(context, target.id)) {
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.files_app_missing)) }
                            }
                        }
                        AccountCard(
                            account = account,
                            onOpen = { onOpenAccount(account.id) },
                            onOpenInFiles = if (account.backendType == "local") {
                                null
                            } else {
                                { openInFiles(account) }
                            },
                            refreshing = account.id in refreshing,
                            onRename = { renameTarget = account },
                            onDelete = { deleteTarget = account },
                            onManageUnion = if (account.backendType == "union") ({ unionTarget = account }) else null,
                        )
                    }
                }
            }
            }
        }
    }

    if (showStorage) {
        StorageDetailsDialog(
            accounts = cloudAccounts,
            onDismiss = { showStorage = false },
        )
    }

    if (showImport) {
        com.drivemount.app.ui.galaxy.GalaxyImportSheet(accounts = allAccounts.orEmpty(), onDismiss = { showImport = false })
    }

    if (showTrash) {
        com.drivemount.app.ui.galaxy.GalaxyTrashSheet(accounts = allAccounts.orEmpty(), onDismiss = { showTrash = false })
    }

    if (showGalaxy) {
        com.drivemount.app.ui.galaxy.GalaxySheet(
            config = galaxyCfg,
            accounts = allAccounts.orEmpty(),
            onAddAccount = onAddAccount,
            onDismiss = { showGalaxy = false },
        )
    }

    crashReport?.let { report ->
        CrashReportDialog(report = report, onDismiss = { crashReport = null })
    }

    unionTarget?.let { com.drivemount.app.ui.accounts.UnionEditDialog(it, onDismiss = { unionTarget = null }) }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.rename(target.id, newName)
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                val inside = groups.orEmpty().firstOrNull { it.base.id == target.id }?.vaults.orEmpty()
                Text(
                    stringResource(R.string.delete_body, target.displayName) +
                        if (inside.isEmpty()) "" else "\n\n" + stringResource(R.string.delete_with_vaults, inside.size),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(target, groups.orEmpty().firstOrNull { it.base.id == target.id }?.vaults.orEmpty())
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** ক্লাউড-কার্ডের কম্প্যাক্ট শিরোনাম সারি: ছোট আইকন, কম ফাঁক */
@Composable
private fun CloudHeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    providerType: String?,
    trailing: @Composable () -> Unit,
) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = sp.md, end = sp.xs, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (providerType != null) {
                ProviderIcon(providerType, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        trailing()
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    onOpen: () -> Unit,
    onOpenInFiles: (() -> Unit)?,
    refreshing: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onManageUnion: (() -> Unit)? = null,
    grouped: Boolean = false,
) {
    val sp = MaterialTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }

    val glowOverrides by (LocalContext.current.applicationContext as com.drivemount.app.DriveMountApp)
        .cloudGlow.overrides.collectAsState()
    val used = account.usedBytes
    val total = account.totalBytes
    val percent = if (used != null && total != null && total > 0) {
        ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    } else null

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        CloudHeaderRow(
            icon = providerIcon(account.backendType),
            providerType = account.backendType,
            title = account.displayName,
            subtitle = providerLabel(account.backendType),
            trailing = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = sp.sm).height(38.dp)) { // সব কার্ডের উচ্চতা সমান রাখতে স্থির
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Lucide.LuEllipsisVertical,
                            contentDescription = stringResource(R.string.more_options),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (percent != null) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onOpenInFiles != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_open_files)) },
                                leadingIcon = { Icon(Lucide.LuFolderOpen, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenInFiles()
                                },
                            )
                        }
                        if (onManageUnion != null) {
                            DropdownMenuItem(
                                text = { Text("Manage drives") },
                                leadingIcon = { Icon(Lucide.LuLayers, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onManageUnion()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rename)) },
                            leadingIcon = { Icon(Lucide.LuPencil, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete)) },
                            leadingIcon = { Icon(Lucide.LuTrash2, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            },
        )

        // প্রোগ্রেস বার কার্ডের একদম নিচে, দুই প্রান্ত স্পর্শ করে (কোনো প্যাডিং ছাড়া)
        if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
        } else if (used != null) {
            Text(
                text = if (total != null && total > 0) {
                    stringResource(R.string.usage_of, formatBytes(used), formatBytes(total))
                } else {
                    stringResource(R.string.usage_used, formatBytes(used))
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = sp.md).padding(bottom = 4.dp),
            )
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
        }
    }
    if (grouped) {
        // একত্রিত কার্ডের ভেতরের সারি: আলাদা কার্ড/ছায়া নেই, শুধু ক্লিকযোগ্য
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) { body() }
    } else {
        GlowCard(glow = com.drivemount.app.data.CloudGlowSettings.resolve(glowOverrides, account.backendType), onClick = onOpen) { body() }
    }
}

/** প্ল্যাটফর্ম ফিল্টারের ছোট লেখা-বাটন */
@Composable
private fun ProviderFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    val sp = MaterialTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(sp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.LuCloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(sp.lg))
        Text(
            text = stringResource(R.string.no_accounts_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(sp.sm))
        Text(
            text = stringResource(R.string.no_accounts_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(sp.lg))
        FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.add_account)) }
        TextButton(onClick = onImport) { Text(stringResource(R.string.home_import)) }
    }
}

private class QuickItem(val icon: ImageVector, val label: Int, val onClick: () -> Unit)

@Composable
private fun QuickAccessCell(item: QuickItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = item.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(item.label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

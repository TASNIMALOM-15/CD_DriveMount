package com.drivemount.app.ui.browser

import com.drivemount.app.ui.components.seekPreviewGestures
import com.drivemount.app.engine.guessMime
import androidx.compose.runtime.mutableStateListOf
import com.composables.icons.lucide.EllipsisVertical as LuEllipsisVertical
import com.composables.icons.lucide.Info as LuInfo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin as LuPin
import com.drivemount.app.ui.components.FabCatalog
import com.drivemount.app.ui.components.FabAction
import com.drivemount.app.ui.components.ActionFab
import com.drivemount.app.ui.components.rememberFabVisibility
import com.composables.icons.lucide.ArrowDown as LuArrowDown
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.ArrowUp as LuArrowUp
import com.composables.icons.lucide.ArrowUpDown as LuArrowUpDown
import com.composables.icons.lucide.Check as LuCheck
import com.composables.icons.lucide.ChevronRight as LuChevronRight
import com.composables.icons.lucide.ClipboardPaste as LuClipboardPaste
import com.composables.icons.lucide.Copy as LuCopy
import com.composables.icons.lucide.Download as LuDownload
import com.composables.icons.lucide.FileUp as LuFileUp
import com.composables.icons.lucide.FolderPlus as LuFolderPlus
import com.composables.icons.lucide.LayoutGrid as LuLayoutGrid
import com.composables.icons.lucide.List as LuList
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.Pencil as LuPencil
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Scissors as LuScissors
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Trash2 as LuTrash2
import com.composables.icons.lucide.X as LuX
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import com.drivemount.app.ui.navigation.BandSwipeHost
import androidx.compose.ui.draw.clip
import com.drivemount.app.data.meta.ThumbMetaReader
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.ClipboardContent
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.LocalPaths
import com.drivemount.app.engine.browseRootFor
import com.drivemount.app.engine.joinPath
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.IconBadge
import com.drivemount.app.ui.components.fileIcon
import com.drivemount.app.ui.components.formatDateOnly
import com.drivemount.app.ui.components.isPlayable
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.components.formatDate
import com.drivemount.app.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onOpenTransfers: () -> Unit,
    onCreateVault: () -> Unit,
    onPlay: (accountId: Long, path: String) -> Unit,
    onOpenVault: (Long) -> Unit = {},
    onHome: () -> Unit = {},
    onOpenZip: (accountId: Long, path: String, size: Long) -> Unit = { _, _, _ -> },
    vm: BrowserViewModel = viewModel(),
) {
    val ui by vm.state.collectAsState()
    val highlight by vm.highlight.collectAsState()
    val replicas by vm.replicas.collectAsState()
    val openProgress by vm.openProgress.collectAsState()
    val ghosts by vm.uploadGhosts.collectAsState()
    val dmApp = LocalContext.current.applicationContext as com.drivemount.app.DriveMountApp
    val datesTick = dmApp.sidecar.version.intValue // তারিখ ক্যাশে নতুন কিছু এলে সারি আবার আঁকা হয়
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val trashMode by vm.trashMode.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val clip by vm.clipboard.collectAsState()
    val vaults by vm.vaults.collectAsState()
    val cloudVaults by vm.cloudVaults.collectAsState()
    var vaultMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val sp = MaterialTheme.spacing

    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var infoTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var addToLibrary by remember { mutableStateOf<List<RemoteEntry>?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var showLocalPicker by remember { mutableStateOf(false) }
    // আপলোড/পেস্টের আগে "এনক্রিপ্ট করবেন?" প্রশ্নের অপেক্ষায় থাকা কাজ
    var pendingAction by remember { mutableStateOf<((AccountEntity?) -> Unit)?>(null) }
    var vaultAction by remember { mutableStateOf<((AccountEntity?) -> Unit)?>(null) }
    var needVault by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var goToText by remember(showGoTo) { mutableStateOf("") }
    var goToError by remember(showGoTo) { mutableStateOf(false) }
    var folderName by remember(showNewFolder) { mutableStateOf("") }
    var newName by remember(renameTarget) { mutableStateOf(renameTarget?.name.orEmpty()) }

    val account = ui.account
    val themeRepo = (LocalContext.current.applicationContext as com.drivemount.app.DriveMountApp).themeRepository
    val viewSettings by themeRepo.settings.collectAsState()
    val gridView = viewSettings.gridView
    val showThumbs = viewSettings.thumbnails
    val root = account?.let { browseRootFor(it.backendType) } ?: ""
    val isLocal = account?.backendType == "local"
    val shortcutDefaults = remember(account?.id, isLocal) {
        if (isLocal) buildPlaces(context).map { Shortcut(it.label, it.relativePath) } +
            Shortcut(context.getString(R.string.loc_goto), "", goto = true)
        else emptyList()
    }
    var savedShortcuts by remember(account?.id) { mutableStateOf(account?.let { ShortcutStore.load(context, it.id) }) }
    fun saveShortcuts(list: List<Shortcut>?) {
        savedShortcuts = list
        account?.let { ShortcutStore.save(context, it.id, list) }
    }

    // ফোনের স্টোরেজ (Local অ্যাকাউন্ট): অনুমতি
    var accessGranted by remember { mutableStateOf(hasStorageAccess(context)) }
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        accessGranted = hasStorageAccess(context)
        if (accessGranted) vm.pullRefresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = hasStorageAccess(context)
        if (now && !accessGranted) vm.pullRefresh()
        accessGranted = now
    }
    val needsAccess = isLocal && !accessGranted

    // নোটিফিকেশনের অনুমতি (Android 13+): ট্রান্সফার শুরুর আগে একবার চাওয়া হয়
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // অনুমতির অনুরোধ ব্যর্থ হলেও আপলোড যেন না আটকায়
            try {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                // নোটিফিকেশন ছাড়াই চলবে
            }
        }
    }

    // ডাউনলোডের জন্য ফোনের স্টোরেজের অনুমতি লাগে
    fun guardedDownload(action: () -> Unit) {
        if (!hasStorageAccess(context)) {
            requestStorageAccess(context, legacyLauncher)
        } else {
            askNotifications()
            action()
        }
    }

    // সাধারণ ক্লাউড অ্যাকাউন্টে আপলোড হলে প্রতিবার প্রশ্ন; ভল্ট বা ফোনের স্টোরেজে নয়
    fun requestEncrypt(action: (AccountEntity?) -> Unit) {
        val type = account?.backendType
        if (account != null && type != "crypt" && type != "local") {
            pendingAction = action
        } else {
            action(null)
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(PickManyFiles()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            requestEncrypt { vault ->
                askNotifications()
                vm.upload(uris, vault)
            }
        }
    }

    // সিস্টেমের file picker না থাকলে (কিছু কাজের প্রোফাইল/ফোনে) আগে অ্যাপ ক্র্যাশ করত; এখন নিজস্ব ডায়ালগ খোলে
    fun openUploadPicker() {
        try {
            uploadLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            if (hasStorageAccess(context)) {
                showLocalPicker = true
            } else {
                requestStorageAccess(context, legacyLauncher)
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is BrowserEvent.Message -> snackbar.showSnackbar(event.text)

                is BrowserEvent.Queued -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.browser_queued, event.count),
                        actionLabel = context.getString(R.string.browser_view_transfers),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenTransfers()
                }

                is BrowserEvent.OpenFile -> {
                    if (!openDownloadedFile(context, event.path, event.mime)) {
                        snackbar.showSnackbar(context.getString(R.string.browser_no_app))
                    }
                }
            }
        }
    }

    val visible = remember(ui.entries, ui.sort, ui.query) {
        visibleEntries(ui.entries, ui.sort, ui.query)
    }

    // ফোল্ডারে ঢুকে ফিরলে আগের স্ক্রল-অবস্থান হুবহু ফেরত: প্রতিটি পথের (সূচক, অফসেট) মনে রাখা
    class ScrollMemo {
        val saved = HashMap<String, IntArray>()
        var lastPath: String = ""
        var pending: IntArray? = null
    }
    val memo = remember { ScrollMemo().also { it.lastPath = ui.path } }
    if (ui.path != memo.lastPath) { // পথ বদলের মুহূর্তেই (রচনার সময়) ঠিক হয়, যাতে রেকর্ডার ভুল মান না লেখে
        val prev = memo.lastPath
        val forward = ui.path.startsWith(prev.trimEnd('/') + "/")
        memo.lastPath = ui.path
        if (forward) { memo.saved.remove(ui.path); memo.pending = null }
        else memo.pending = memo.saved[ui.path]
    }
    LaunchedEffect(gridView) {
        androidx.compose.runtime.snapshotFlow {
            if (gridView) Triple(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, ui.path)
            else Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, ui.path)
        }.collect { (i, o, path) ->
            if (memo.pending == null && !ui.loading && path == memo.lastPath) memo.saved[path] = intArrayOf(i, o)
        }
    }
    LaunchedEffect(ui.path, visible.size, ui.loading, gridView) {
        val p = memo.pending ?: return@LaunchedEffect
        if (ui.loading) return@LaunchedEffect
        if (visible.isNotEmpty()) {
            val idx = p[0].coerceAtMost(visible.lastIndex)
            if (gridView) gridState.scrollToItem(idx, p[1]) else listState.scrollToItem(idx, p[1])
        }
        memo.pending = null
    }
    // আপলোড চলা ফাইল (এখনো ক্লাউডের তালিকায় আসেনি): শুধু দেখানোর জন্য তালিকায় মেশানো। নির্বাচন/ভিউয়ার/খোলা ইত্যাদি
    // সবকিছু আগের মতো [visible] (আসল ফাইল) থেকেই চলে, তাই ধূসর আইটেম কখনো ভুল করে কাজে ধরা পড়ে না।
    val ghostOnly = remember(ghosts, ui.entries) {
        if (ghosts.isEmpty()) emptySet<String>() else {
            val have = ui.entries.mapTo(HashSet()) { it.path }
            ghosts.keys.filterTo(HashSet()) { it !in have }
        }
    }
    val shown = remember(visible, ghostOnly, ghosts, ui.entries, ui.sort, ui.query) {
        if (ghostOnly.isEmpty()) visible
        else visibleEntries(ui.entries + ghostOnly.mapNotNull { ghosts[it]?.entry }, ui.sort, ui.query)
    }
    val selecting = ui.selected.isNotEmpty()
    var askSelectAll by remember { mutableStateOf(false) }
    if (askSelectAll) {
        AlertDialog(
            onDismissRequest = { askSelectAll = false },
            title = { Text(stringResource(R.string.browser_select_all_title)) },
            text = { Text(stringResource(R.string.browser_select_all_body, visible.size)) },
            confirmButton = {
                TextButton(onClick = { askSelectAll = false; vm.selectAll(visible.map { it.path }) }) {
                    Text(stringResource(R.string.browser_select_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { askSelectAll = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    BackHandler {
        when {
            selecting -> vm.clearSelection()
            ui.query != null -> vm.setQuery(null)
            !vm.up() -> onBack()
        }
    }

    val offlineStore = (LocalContext.current.applicationContext as com.drivemount.app.DriveMountApp).offlineStore
    val offlineSnap by offlineStore.ui.collectAsState()
    // ফ্লোটিং বাটনে যে কাজগুলো আছে সেগুলো উপরের মেনুতে রাখা হয় না
    val fabOn = rememberFabVisibility()
    val fabShown = ui.query == null && !needsAccess && account != null
    val topBr = { id: String -> !(fabShown && fabOn(FabCatalog.BROWSER, id)) }
    val topSel = { id: String -> !(fabShown && fabOn(FabCatalog.BROWSER_SEL, id)) }
    // Library-র ভিডিও→ছবি→গান পেজারের মতোই আসল পেজার: বামে = ভল্ট, ডানে = Home; আঙুল ছাড়ার পর পেজ থিতু হলে কাজ হয়
    val swipePager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 3 }
    val latestVaultCount by androidx.compose.runtime.rememberUpdatedState(cloudVaults.size)
    LaunchedEffect(swipePager) {
        androidx.compose.runtime.snapshotFlow { swipePager.settledPage }.collect { pg ->
            when (pg) {
                0 -> { onHome(); swipePager.scrollToPage(1) }
                2 -> {
                    when (latestVaultCount) {
                        0 -> {}
                        1 -> onOpenVault(cloudVaults.first().id)
                        else -> vaultMenu = true
                    }
                    swipePager.animateScrollToPage(1)
                }
            }
        }
    }
    androidx.compose.foundation.pager.HorizontalPager(
        state = swipePager,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !selecting,
    ) { swipePage ->
    if (swipePage != 1) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(
                com.drivemount.app.ui.components.AppIcons.byKey.getValue(if (swipePage == 0) "House" else "Lock"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
        }
    } else {
    Scaffold(
        topBar = { Box(Modifier.clipToBounds().layout { m, c ->
            // টপ-বার সামান্য উপরে: ওপরে ৬dp ও নিচে ১০dp ফাঁকা ছাঁটা, যাতে কনটেন্টের জায়গা বাড়ে
            val p = m.measure(c)
            val top = 6.dp.roundToPx()
            val bottom = 10.dp.roundToPx()
            layout(p.width, p.height - top - bottom) { p.placeRelative(0, -top) }
        }) { com.drivemount.app.ui.components.TopBarIcons {
            when {
                selecting -> TopAppBar(
                    title = {
                        val selSize = remember(ui.selected, visible) {
                            visible.filter { it.path in ui.selected && !it.isDir }.sumOf { it.size ?: 0L }
                        }
                        Column(Modifier.clickable { askSelectAll = true }) {
                            Text("${ui.selected.size}/${visible.size}")
                            if (selSize > 0L) Text(
                                formatBytes(selSize),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Lucide.LuX, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        // ঘন ব্যবহারের ৪টি সরাসরি; বাকিগুলো (অফলাইন, তথ্য, নাম বদল) ⋮ মেনুতে — সরু পর্দায় টপ-বার ঠাসা না হতে
                        if (!isLocal && topSel("download")) {
                            IconButton(onClick = { guardedDownload { vm.downloadSelected() } }) {
                                Icon(Lucide.LuDownload, contentDescription = stringResource(R.string.browser_download))
                            }
                        }
                        if (topSel("copy")) IconButton(onClick = { vm.copySelected(move = false) }) {
                            Icon(Lucide.LuCopy, contentDescription = stringResource(R.string.browser_copy))
                        }
                        if (topSel("cut")) IconButton(onClick = { vm.copySelected(move = true) }) {
                            Icon(Lucide.LuScissors, contentDescription = stringResource(R.string.browser_cut))
                        }
                        if (topSel("delete")) IconButton(onClick = { showDelete = true }) {
                            Icon(Lucide.LuTrash2, contentDescription = stringResource(R.string.browser_delete))
                        }
                        val showOffline = !isLocal && topSel("offline")
                        val showInfo = ui.selected.size == 1 && topSel("info")
                        val showRename = ui.selected.size == 1 && topSel("rename")
                        if (showOffline || showInfo || showRename || true) { // প্রস্তুত-অপশন সবসময় থাকে
                            var selMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { selMenu = true }) {
                                    Icon(Lucide.LuEllipsisVertical, contentDescription = null)
                                }
                                DropdownMenu(expanded = selMenu, onDismissRequest = { selMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.selprep_action)) },
                                        leadingIcon = { Icon(Lucide.LuInfo, contentDescription = null) },
                                        onClick = { selMenu = false; vm.prepareSelected() },
                                    )
                                    if (showOffline) {
                                        val allKept = ui.selected.isNotEmpty() && ui.selected.all { "${account?.id}|$it" in offlineSnap.pinned }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (allKept) R.string.offline_remove_action else R.string.offline_keep_action)) },
                                            leadingIcon = { Icon(Lucide.LuPin, contentDescription = null) },
                                            onClick = { selMenu = false; vm.toggleOffline() },
                                        )
                                    }
                                    if (showInfo) DropdownMenuItem(
                                        text = { Text(stringResource(R.string.info_title)) },
                                        leadingIcon = { Icon(Lucide.LuInfo, contentDescription = null) },
                                        onClick = { selMenu = false; infoTarget = ui.entries.firstOrNull { it.path in ui.selected } },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add to Library") },
                                        leadingIcon = { Icon(Lucide.LuFolderPlus, contentDescription = null) },
                                        onClick = {
                                            selMenu = false
                                            val picked = ui.entries.filter { it.path in ui.selected }
                                            val acc = account
                                            if (acc != null && picked.isNotEmpty()) {
                                                if (picked.any { it.isDir }) addToLibrary = picked
                                                else {
                                                    val n = addEntriesToLibrary(context, acc.id, picked, emptySet(), true)
                                                    android.widget.Toast.makeText(context, if (n > 0) "Added to Library" else "No supported files", android.widget.Toast.LENGTH_SHORT).show()
                                                    vm.clearSelection()
                                                }
                                            }
                                        },
                                    )
                                    val pinDir = if (ui.selected.size == 1) ui.entries.firstOrNull { it.path in ui.selected && it.isDir } else null
                                    if (pinDir != null) DropdownMenuItem(
                                        text = { Text("Pin as Tab") },
                                        leadingIcon = { Icon(Lucide.LuPin, contentDescription = null) },
                                        onClick = {
                                            selMenu = false
                                            saveShortcuts((savedShortcuts ?: shortcutDefaults) + Shortcut(pinDir.name, pinDir.path))
                                            vm.clearSelection()
                                        },
                                    )
                                    if (showRename) DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_rename)) },
                                        leadingIcon = { Icon(Lucide.LuPencil, contentDescription = null) },
                                        onClick = { selMenu = false; renameTarget = ui.entries.firstOrNull { it.path in ui.selected } },
                                    )
                                }
                            }
                        }
                    },
                )

                ui.query != null -> TopAppBar(
                    title = { SearchField(ui.query ?: "", onChange = { vm.setQuery(it) }) },
                    navigationIcon = {
                        IconButton(onClick = { vm.setQuery(null) }) {
                            Icon(Lucide.LuArrowLeft, contentDescription = stringResource(R.string.back))
                        }
                    },
                )

                else -> TopAppBar(
                    title = {
                        Text(
                            text = account?.displayName.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!vm.up()) onBack() }) {
                            Icon(Lucide.LuArrowLeft, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (cloudVaults.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { vaultMenu = true }) {
                                    Icon(Lucide.LuLock, contentDescription = "My vault")
                                }
                                DropdownMenu(expanded = vaultMenu, onDismissRequest = { vaultMenu = false }) {
                                    cloudVaults.forEach { v ->
                                        DropdownMenuItem(
                                            text = { Text(v.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingIcon = { Icon(Lucide.LuLock, contentDescription = null) },
                                            onClick = { vaultMenu = false; onOpenVault(v.id) },
                                        )
                                    }
                                }
                            }
                        }
                        if (topBr("search")) IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Lucide.LuSearch, contentDescription = stringResource(R.string.browser_search))
                        }
                        if (topBr("view")) IconButton(onClick = { themeRepo.update { it.copy(gridView = !it.gridView) } }) {
                            Icon(
                                imageVector = if (gridView) Lucide.LuList else Lucide.LuLayoutGrid,
                                contentDescription = stringResource(if (gridView) R.string.view_list else R.string.view_grid),
                            )
                        }
                        Box {
                            if (topBr("sort")) IconButton(onClick = { sortMenu = true }) {
                                Icon(Lucide.LuArrowUpDown, contentDescription = stringResource(R.string.browser_sort))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortKey.values().forEach { key ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    when (key) {
                                                        SortKey.NAME -> R.string.sort_name
                                                        SortKey.DATE -> R.string.sort_date
                                                        SortKey.SIZE -> R.string.sort_size
                                                        SortKey.TYPE -> R.string.sort_type
                                                    },
                                                ),
                                            )
                                        },
                                        trailingIcon = {
                                            if (ui.sort.key == key) {
                                                Icon(
                                                    imageVector = if (ui.sort.ascending) Lucide.LuArrowUp else Lucide.LuArrowDown,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        onClick = {
                                            vm.setSort(key)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }}},
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // ডান-নিচের chevrons বাটন: এই পেজে এখন যা যা করা সম্ভব সেই ছোট আইকনগুলো; স্ক্রল করলে গুটিয়ে যায়
            if (ui.query == null && !needsAccess && account != null) {
                val allKept = ui.selected.isNotEmpty() && ui.selected.all { "${account.id}|$it" in offlineSnap.pinned }
                val single = if (ui.selected.size == 1) ui.entries.firstOrNull { it.path in ui.selected } else null
                val actions = if (selecting) {
                    buildList {
                        if (!isLocal) {
                            add(FabAction("download", Lucide.LuDownload, stringResource(R.string.browser_download)) { guardedDownload { vm.downloadSelected() } })
                            add(
                                FabAction(
                                    "offline", Lucide.LuPin,
                                    stringResource(if (allKept) R.string.offline_remove_action else R.string.offline_keep_action),
                                ) { vm.toggleOffline() },
                            )
                        }
                        add(FabAction("copy", Lucide.LuCopy, stringResource(R.string.browser_copy)) { vm.copySelected(move = false) })
                        add(FabAction("cut", Lucide.LuScissors, stringResource(R.string.browser_cut)) { vm.copySelected(move = true) })
                        if (single != null) {
                            add(FabAction("info", Lucide.LuInfo, stringResource(R.string.info_title)) { infoTarget = single })
                            add(FabAction("rename", Lucide.LuPencil, stringResource(R.string.menu_rename)) { renameTarget = single })
                        }
                        add(FabAction("delete", Lucide.LuTrash2, stringResource(R.string.browser_delete)) { showDelete = true })
                        add(FabAction("clear", Lucide.LuX, stringResource(R.string.cancel)) { vm.clearSelection() })
                    }
                } else {
                    buildList {
                        if (!isLocal) add(FabAction("upload", Lucide.LuFileUp, stringResource(R.string.browser_upload)) { openUploadPicker() })
                        add(FabAction("new_folder", Lucide.LuFolderPlus, stringResource(R.string.browser_new_folder)) { showNewFolder = true })
                        add(FabAction("search", Lucide.LuSearch, stringResource(R.string.browser_search)) { vm.setQuery("") })
                        add(
                            FabAction(
                                "view",
                                if (gridView) Lucide.LuList else Lucide.LuLayoutGrid,
                                stringResource(if (gridView) R.string.view_list else R.string.view_grid),
                            ) { themeRepo.update { it.copy(gridView = !it.gridView) } },
                        )
                        add(FabAction("sort", Lucide.LuArrowUpDown, stringResource(R.string.browser_sort)) { sortMenu = true })
                        if (clip != null) {
                            add(
                                FabAction("paste", Lucide.LuClipboardPaste, stringResource(R.string.browser_paste_here)) {
                                    requestEncrypt { vault ->
                                        askNotifications()
                                        vm.paste(vault)
                                    }
                                },
                            )
                        }
                    }
                }
                ActionFab(
                    page = if (selecting) FabCatalog.BROWSER_SEL else FabCatalog.BROWSER,
                    actions = actions,
                    collapse = listState.isScrollInProgress || gridState.isScrollInProgress,
                )
            }
        },
        bottomBar = {
            val c = clip
            if (c != null && !selecting) {
                PasteBar(
                    clip = c,
                    onPaste = {
                        requestEncrypt { vault ->
                            askNotifications()
                            vm.paste(vault)
                        }
                    },
                    onCancel = { vm.cancelClipboard() },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Breadcrumbs(
                rootLabel = account?.displayName.orEmpty(),
                root = root,
                path = ui.path,
                onNavigate = { vm.navigateTo(it) },
            )

            if (!needsAccess && account != null) {
                ShortcutRow(
                    items = savedShortcuts ?: shortcutDefaults,
                    isLocal = isLocal,
                    onGo = { vm.navigateTo(it) },
                    onGoToPath = { showGoTo = true },
                    onChange = { saveShortcuts(it) },
                    onReset = { saveShortcuts(null) },
                )
            }

            com.drivemount.app.ui.components.RefreshBox(
                refreshing = ui.refreshing,
                onRefresh = { vm.pullRefresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val error = ui.error
                when {
                    needsAccess -> AccessCard(
                        onGrant = { requestStorageAccess(context, legacyLauncher) },
                    )

                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    error != null -> MessageState(
                        text = if (ui.path.startsWith("data")) {
                            error + "\n\n" + stringResource(R.string.loc_data_hint)
                        } else {
                            error
                        },
                        isError = true,
                    )

                    shown.isEmpty() -> MessageState(
                        text = stringResource(
                            if (ui.query.isNullOrBlank()) R.string.browser_empty else R.string.browser_no_match,
                        ),
                    )

                    else -> {
                        val onItemClick: (RemoteEntry) -> Unit = { entry ->
                            when {
                                entry.path in ghostOnly -> Unit // আপলোড চলছে: এখনো খোলা/নির্বাচন করা যায় না
                                selecting -> vm.toggleSelect(entry)
                                entry.isDir -> vm.open(entry)
                                // ছবি: সরাসরি ফুল-স্ক্রিন ভিউয়ারে (এই ফোল্ডারের সব ছবি সোয়াইপ করে দেখা যায়)
                                account != null && ThumbMetaReader.isImage(entry.name) -> {
                                    val idx = visible.filter { !it.isDir && ThumbMetaReader.isImage(it.name) }.indexOfFirst { it.path == entry.path }
                                    if (idx >= 0) viewerIndex = idx
                                }
                                // ভিডিও/অডিও: সরাসরি প্লেয়ারে
                                account != null && isPlayable(entry.name, entry.mimeType) -> {
                                    com.drivemount.app.ui.player.PlaybackQueue.set(
                                        com.drivemount.app.ui.player.PlaybackQueue.Queue(
                                            accountId = account.id,
                                            items = visible.filter { isPlayable(it.name, it.mimeType) && !it.isDir },
                                            siblings = ui.entries,
                                        ),
                                    )
                                    onPlay(account.id, entry.path)
                                }
                                // জিপ: এক্সট্রাক্ট/ডাউনলোড ছাড়াই ভেতরের ফোল্ডার-ট্রী ও সাইজ দেখা
                                account != null && entry.name.endsWith(".zip", ignoreCase = true) && (entry.size ?: 0L) > 0L ->
                                    onOpenZip(account.id, entry.path, entry.size ?: 0L)
                                // বাকি: অফলাইন কপি থাকলে সঙ্গে সঙ্গে; নইলে ফাইলের ওপরেই % দেখিয়ে নামিয়ে খোলে
                                else -> {
                                    askNotifications()
                                    vm.openFile(entry)
                                }
                            }
                        }
                        // স্ক্রল থামলে (≈১ সেকেন্ড) পর্দায় দেখা ভিডিওগুলোর শুরু/শেষ আগেভাগে ক্যাশে আনা: ট্যাপ করলে প্রায় সঙ্গে সঙ্গে চলে
                        // (মোবাইল ডেটা/ব্যাটারি-সেভারে চলে না)
                        val warmCtx = LocalContext.current
                        LaunchedEffect(shown, account, gridView) {
                            val acc = account ?: return@LaunchedEffect
                            if (acc.backendType == "local" || acc.backendType == "terabox") return@LaunchedEffect
                            val app = warmCtx.applicationContext as com.drivemount.app.DriveMountApp
                            val fs = com.drivemount.app.engine.fsRootFor(acc.remoteName, acc.backendType)
                            androidx.compose.runtime.snapshotFlow {
                                if (gridView) gridState.layoutInfo.visibleItemsInfo.map { it.index }
                                else listState.layoutInfo.visibleItemsInfo.map { it.index }
                            }.collectLatest { indices ->
                                kotlinx.coroutines.delay(900)
                                var n = 0
                                for (i in indices) {
                                    val e = shown.getOrNull(i) ?: continue
                                    if (e.path in ghostOnly) continue
                                    // শুধু ভিডিও (ছবি নয়, গানও নয়)
                                    if (e.isDir || !com.drivemount.app.data.meta.ThumbMetaReader.isMedia(e.name) ||
                                        com.drivemount.app.data.meta.ThumbMetaReader.isImage(e.name)
                                    ) continue
                                    com.drivemount.app.engine.StreamWarmer.warmFile(warmCtx, app.rcloneApi, fs, e.path, e.size ?: -1L, false)
                                    if (++n >= 4) break
                                }
                            }
                        }
                        // সার্চ থেকে এলে: ফাইলটি পর্দায় এনে কয়েক সেকেন্ড হাইলাইট রাখে, তারপর নিজে মিলিয়ে যায়
                        LaunchedEffect(highlight, shown) {
                            if (highlight.isEmpty()) return@LaunchedEffect
                            val idx = shown.indexOfFirst { it.path == highlight }
                            if (idx < 0) return@LaunchedEffect
                            if (gridView) gridState.scrollToItem(idx) else listState.scrollToItem(idx)
                            kotlinx.coroutines.delay(3500)
                            vm.clearHighlight()
                        }
                        if (gridView) {
                            // ফাইল = ছোট ঘর (cols টি); ফোল্ডার = লিস্টের মতো সারি, তবে এক সারিতে ২টি
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val cols = ((maxWidth - sp.sm * 2) / 104.dp).toInt().coerceAtLeast(2)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(cols * 2),
                                    state = gridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = sp.sm, end = sp.sm, top = sp.xs, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                                    verticalArrangement = Arrangement.spacedBy(sp.sm),
                                ) {
                                    val dirCount = shown.count { it.isDir }
                                    itemsIndexed(
                                        shown,
                                        key = { i, e -> "${e.path}#$i" },
                                        // বিজোড় ফোল্ডার হলে শেষটি পুরো সারি নেয়, ফলে ফাইল সবসময় নতুন সারি থেকে শুরু
                                        span = { i, e ->
                                            GridItemSpan(
                                                if (!e.isDir) 2
                                                else if (dirCount % 2 == 1 && i == dirCount - 1) cols * 2
                                                else cols,
                                            )
                                        },
                                    ) { idx, entry ->
                                        if (entry.isDir) {
                                            FileRow(
                                                offline = account?.let { offlineStore.stateOf(offlineSnap, it.id, entry.path, entry.isDir) } ?: 0,
                                                entry = entry,
                                                account = account,
                                                showThumbs = showThumbs,
                                                selected = entry.path in ui.selected,
                                                highlighted = highlight.isNotEmpty() && entry.path == highlight,
                                                onClick = { onItemClick(entry) },
                                                onLongClick = { vm.selectRangeTo(entry, visible) },
                                                childCount = vm::childCount,
                                                countTick = vm.countTick,
                                                compact = true,
                                                gridCol = if (dirCount % 2 == 1 && idx == dirCount - 1) 2 else idx % 2,
                                            )
                                        } else {
                                            val g = if (entry.path in ghostOnly) ghosts[entry.path] else null
                                            FileTile(
                                                offline = account?.let { offlineStore.stateOf(offlineSnap, it.id, entry.path, entry.isDir) } ?: 0,
                                                entry = entry,
                                                account = account,
                                                showThumbs = showThumbs,
                                                selected = entry.path in ui.selected,
                                                highlighted = highlight.isNotEmpty() && entry.path == highlight,
                                                onClick = { onItemClick(entry) },
                                                onLongClick = { if (g == null) vm.selectRangeTo(entry, visible) },
                                                replica = replicas[entry.path],
                                                openPercent = openProgress[entry.path],
                                                ghost = g,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = sp.xs, bottom = 96.dp),
                            ) {
                                itemsIndexed(shown, key = { i, e -> "${e.path}#$i" }) { _, entry ->
                                    val g = if (entry.path in ghostOnly) ghosts[entry.path] else null
                                    FileRow(
                                        offline = account?.let { offlineStore.stateOf(offlineSnap, it.id, entry.path, entry.isDir) } ?: 0,
                                        entry = entry,
                                        account = account,
                                        showThumbs = showThumbs,
                                        selected = entry.path in ui.selected,
                                        highlighted = highlight.isNotEmpty() && entry.path == highlight,
                                        onClick = { onItemClick(entry) },
                                        onLongClick = { if (g == null) vm.selectRangeTo(entry, visible) },
                                        childCount = vm::childCount,
                                        countTick = vm.countTick,
                                        replica = replicas[entry.path],
                                        openPercent = openProgress[entry.path] ?: g?.let { (it.fraction * 100).toInt().coerceIn(0, 99) },
                                        takenMillis = if (datesTick >= 0) account?.let { a ->
                                            dmApp.fileDates.peek(a.remoteName, entry.path, entry.size ?: -1L)?.taken?.takeIf { it > 0 }
                                        } else null,
                                        ghost = g,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }

    // ---------------- ডায়ালগ ----------------

    if (showLocalPicker) {
        LocalFilePickerDialog(
            onDismiss = { showLocalPicker = false },
            onPicked = { picked ->
                showLocalPicker = false
                requestEncrypt { vault ->
                    askNotifications()
                    vm.uploadLocal(picked, vault)
                }
            },
        )
    }

    pendingAction?.let { action ->
        EncryptPromptDialog(
            onYes = {
                pendingAction = null
                when {
                    vaults.isEmpty() -> needVault = true
                    vaults.size == 1 -> action(vaults.first())
                    else -> vaultAction = action
                }
            },
            onNo = {
                pendingAction = null
                action(null)
            },
            onDismiss = { pendingAction = null },
        )
    }

    vaultAction?.let { action ->
        VaultPickerDialog(
            vaults = vaults,
            onPick = { vault ->
                vaultAction = null
                action(vault)
            },
            onDismiss = { vaultAction = null },
        )
    }

    if (needVault) {
        NoVaultDialog(
            onCreate = {
                needVault = false
                onCreateVault()
            },
            onDismiss = { needVault = false },
        )
    }

    if (showGoTo) {
        AlertDialog(
            onDismissRequest = { showGoTo = false },
            title = { Text(stringResource(R.string.goto_title)) },
            text = {
                OutlinedTextField(
                    value = goToText,
                    onValueChange = {
                        goToText = it
                        goToError = false
                    },
                    label = { Text(stringResource(R.string.goto_label)) },
                    singleLine = true,
                    isError = goToError,
                    supportingText = if (goToError) {
                        { Text(stringResource(R.string.goto_error)) }
                    } else {
                        null
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = goToText.isNotBlank(),
                    onClick = {
                        val target = LocalPaths.resolveToRelative(goToText)
                        if (target == null) {
                            goToError = true
                        } else {
                            showGoTo = false
                            vm.navigateTo(target)
                        }
                    },
                ) { Text(stringResource(R.string.goto_go)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoTo = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text(stringResource(R.string.browser_new_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.browser_folder_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = folderName.isNotBlank(),
                    onClick = {
                        vm.createFolder(folderName)
                        showNewFolder = false
                    },
                ) { Text(stringResource(R.string.browser_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.menu_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.rename(target, newName)
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(if (trashMode) R.string.galaxy_trash_move_title else R.string.browser_delete_title)) },
            text = {
                Text(
                    if (trashMode) stringResource(R.string.galaxy_trash_body, ui.selected.size)
                    else stringResource(R.string.browser_delete_body, ui.selected.size),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSelected()
                        showDelete = false
                    },
                ) { Text(stringResource(R.string.browser_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    viewerIndex?.let { idx ->
        if (account != null) {
            val photos = visible.filter { !it.isDir && ThumbMetaReader.isImage(it.name) }
                .map { com.drivemount.app.ui.videos.VideoItem(it, ui.path, "${account.id}|${ui.path}", account.id) }
            com.drivemount.app.ui.videos.PhotoViewerDialog(
                items = photos,
                startIndex = idx,
                accounts = mapOf(account.id to account),
                onDismiss = { viewerIndex = null },
                onDeleted = { vm.pullRefresh() },
            )
        }
    }

    addToLibrary?.let { picked ->
        val acc = account
        var sub by remember { mutableStateOf(true) }
        val tabs = listOf("videos_source" to "Videos", "music_source" to "Music", "photos_source" to "Photos", "docs_source" to "Docs", "apks_source" to "APKs")
        val checked = remember { mutableStateListOf<String>().also { l -> tabs.forEach { l.add(it.first) } } }
        AlertDialog(
            onDismissRequest = { addToLibrary = null },
            title = { Text("Add to Library") },
            text = {
                Column {
                    Text("Folders go into the tabs you tick; files go to their own tab.", style = MaterialTheme.typography.bodySmall)
                    tabs.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth().clickable { if (key in checked) checked.remove(key) else checked.add(key) }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = key in checked, onCheckedChange = { if (it) checked.add(key) else checked.remove(key) })
                            Text(label)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { sub = !sub }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = sub, onCheckedChange = { sub = it })
                        Text("also sub-folders")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (acc != null) addEntriesToLibrary(context, acc.id, picked, checked.toSet(), sub)
                    addToLibrary = null
                    vm.clearSelection()
                    android.widget.Toast.makeText(context, "Added to Library", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addToLibrary = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    infoTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
                    InfoLine(R.string.info_type, entry.mimeType.ifBlank { "—" })
                    InfoLine(R.string.info_size, entry.size?.let { formatBytes(it) } ?: "—")
                    val both = account?.let { dmApp.fileDates.peek(it.remoteName, entry.path, entry.size ?: -1L) }
                    if (both != null && both.taken > 0) InfoLine(R.string.info_taken, formatDate(both.taken))
                    if (both != null && both.uploaded > 0) InfoLine(R.string.info_uploaded, formatDate(both.uploaded))
                    InfoLine(R.string.info_modified, entry.modTimeMillis?.let { formatDate(it) } ?: "—")
                    InfoLine(R.string.info_path, "/" + entry.path)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askNotifications()
                        vm.openFile(entry)
                        infoTarget = null
                    },
                ) { Text(stringResource(R.string.browser_open)) }
            },
            dismissButton = {
                Row {
                    if (isPlayable(entry.name, entry.mimeType) && account != null) {
                        TextButton(
                            onClick = {
                                infoTarget = null
                                // এই ফোল্ডারের সব ভিডিও/অডিও প্লেলিস্ট হয়ে যায় (এখন দেখানো ক্রমে)
                                com.drivemount.app.ui.player.PlaybackQueue.set(
                                    com.drivemount.app.ui.player.PlaybackQueue.Queue(
                                        accountId = account.id,
                                        items = visible.filter { isPlayable(it.name, it.mimeType) && !it.isDir },
                                        siblings = ui.entries,
                                    ),
                                )
                                onPlay(account.id, entry.path)
                            },
                        ) { Text(stringResource(R.string.browser_play)) }
                    }
                    if (!isLocal) {
                        TextButton(
                            onClick = {
                                infoTarget = null
                                guardedDownload { vm.download(listOf(entry)) }
                            },
                        ) { Text(stringResource(R.string.browser_download)) }
                    }
                    TextButton(onClick = { infoTarget = null }) { Text(stringResource(R.string.close)) }
                }
            },
        )
    }

    ui.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.browser_working)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${p.done} / ${p.total}", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
        )
    }
}

// ---------------------------------------------------------------------------
// অংশগুলো
// ---------------------------------------------------------------------------

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.browser_search_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

@Composable
internal fun Breadcrumbs(
    rootLabel: String,
    root: String,
    path: String,
    onNavigate: (String) -> Unit,
) {
    val sp = MaterialTheme.spacing
    val segments = path.removePrefix(root).trim('/').split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    // গভীর পথে সবচেয়ে ভেতরের ফোল্ডারটি যেন দেখা যায়
    LaunchedEffect(path) {
        kotlinx.coroutines.delay(60)
        scroll.animateScrollTo(scroll.maxValue)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(rootLabel, last = segments.isEmpty()) { onNavigate(root) }
        segments.forEachIndexed { i, segment ->
            Icon(
                imageVector = Lucide.LuChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            val target = joinPath(root, segments.take(i + 1).joinToString("/"))
            Crumb(segment, last = i == segments.lastIndex) { onNavigate(target) }
        }
    }
}

@Composable
private fun Crumb(text: String, last: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = if (last) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileRow(
    entry: RemoteEntry,
    account: AccountEntity?,
    showThumbs: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    offline: Int = 0,
    highlighted: Boolean = false,
    childCount: (suspend (String) -> Int?)? = null,
    countTick: kotlinx.coroutines.flow.StateFlow<Int>? = null,
    /** grid-এ ফোল্ডারের ছোট সারি: শুধু সংখ্যা, তারিখ/আকার নয় */
    compact: Boolean = false,
    /** grid-ফোল্ডার: ০ = বাম ঘর, ১ = ডান ঘর, ২ = পুরো সারি; -১ = grid নয় */
    gridCol: Int = -1,
    replica: ReplicaStatus? = null,
    /** খোলার জন্য নামছে: ০–৯৯ (সারির ওপর হালকা ভরাট ও শতাংশ) */
    openPercent: Int? = null,
    /** তোলার/বানানোর তারিখ জানা থাকলে সারিতে সেটি; নইলে ক্লাউডের পরিবর্তনের তারিখ */
    takenMillis: Long? = null,
    /** আপলোড চলছে (তালিকায় এখনো আসল ফাইল নেই): ধূসর থেকে বাম→ডানে রঙিন */
    ghost: UploadGhost? = null,
) {
    val sp = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val count by produceState<Int?>(null, entry.path) {
        if (entry.isDir && childCount != null) {
            value = childCount(entry.path)
            countTick?.collect { childCount(entry.path)?.let { n -> value = n } }
        }
    }
    // ফোল্ডারের আনুমানিক আকার (আপলোডের সময় জমানো); জানা না থাকলে কিছু দেখানো হয় না
    val sizesStore = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.drivemount.app.DriveMountApp).folderSizes
    val folderSize by produceState<Long?>(null, entry.path, account?.remoteName) {
        if (!entry.isDir || account == null) return@produceState
        sizesStore.version.collect { value = sizesStore.get(account.remoteName, entry.path)?.takeIf { it > 0 } }
    }
    val countText = count?.let {
        when (it) {
            0 -> stringResource(R.string.folder_items_empty)
            1 -> stringResource(R.string.folder_items_one)
            else -> stringResource(R.string.folder_items_count, it)
        }
    }
    val hl by androidx.compose.animation.animateColorAsState(
        if (highlighted) colors.tertiaryContainer else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "hl",
    )
    val ghostText = ghost?.let { ghostSubtitle(it) }
    val subtitle = ghostText ?: listOfNotNull(
        countText,
        if (compact) null else (entry.size?.takeIf { !entry.isDir } ?: folderSize)?.let { formatBytes(it) },
    ).joinToString(" · ")
    val primaryFill = colors.primary.copy(alpha = 0.16f)
    val dividerColor = colors.outlineVariant.copy(alpha = 0.45f)
    val offlineText = offlineLabel(offline)
    val dateText = if (compact) null else (takenMillis ?: entry.modTimeMillis)?.let { formatDateOnly(it) }

    val kind = if (ghost == null && showThumbs && account != null && !selected) thumbKindOf(entry) else null
    val ghostKind = if (ghost != null && showThumbs) thumbKindOf(entry) else null
    val isVideoRow = (kind == ThumbKind.VIDEO && account != null) || ghostKind == ThumbKind.VIDEO
    // Library-র মতো: থাম্বনেলে চেপে ধরলে বা আঙুল রেখে স্ক্রল করলে সিক-শিটের ফ্রেম চলে (সিক-শিট থাকলে)
    val seekSp = if (kind == ThumbKind.VIDEO && account != null) com.drivemount.app.ui.components.rememberSeekPreview("b:${account.id}:${entry.path}", account, entry.path, entry.size) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.secondaryContainer else hl) // সিলেক্ট হলে পুরো সারি (গোল কোণ ছাড়া) রঙিন
            .drawBehind {
                if (openPercent != null) {
                    drawRect(
                        color = primaryFill,
                        size = androidx.compose.ui.geometry.Size(size.width * openPercent / 100f, size.height),
                    )
                }
                // সারির নিচে অতি সূক্ষ্ম রেখা (০.৫dp, খুব হালকা): চোখে লাগে না, তবে সারিগুলো আলাদা বোঝা যায়
                if (compact && gridCol >= 0) {
                    // grid-ফোল্ডার: মাঝে উপর-নিচ লম্ব রেখা, আর প্রতি সারির নিচে আড়াআড়ি রেখা (লিস্টের মতো)
                    val half = sp.sm.toPx() / 2f
                    val startInset = sp.sm.toPx()
                    val y = size.height + half
                    val x0 = if (gridCol == 1) -half else startInset
                    val x1 = if (gridCol == 0) size.width + half else size.width - startInset
                    drawLine(dividerColor, androidx.compose.ui.geometry.Offset(x0, y), androidx.compose.ui.geometry.Offset(x1, y), 0.5.dp.toPx())
                    if (gridCol == 0) {
                        val vx = size.width + half
                        drawLine(dividerColor, androidx.compose.ui.geometry.Offset(vx, -half), androidx.compose.ui.geometry.Offset(vx, y), 0.5.dp.toPx())
                    }
                } else if (!compact) {
                    val inset = 12.dp.toPx()
                    val y = size.height - 0.25.dp.toPx()
                    drawLine(
                        color = dividerColor,
                        start = androidx.compose.ui.geometry.Offset(inset, y),
                        end = androidx.compose.ui.geometry.Offset(size.width - inset, y),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // কম্প্যাক্ট সারি; ভিডিওর সারিতে থাম্বনেলগুলো প্রায় গায়ে গায়ে (মাঝে মাত্র ২dp)
            // ভিডিওর সারিতে থাম্বনেইল আরও বাম ঘেঁষে (শুরুর ফাঁক ৮dp কম)
            .padding(
                start = if (compact && gridCol >= 0) sp.sm else if (isVideoRow) sp.md else sp.md + sp.sm,
                end = sp.md + sp.sm,
                top = if (isVideoRow) 1.dp else 3.dp,
                bottom = if (isVideoRow) 1.dp else 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        if (ghost != null) {
            if (ghostKind == ThumbKind.VIDEO) {
                Box(Modifier.width(71.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceContainerHigh)) {
                    GhostThumbnail(ghost, ghostKind, 240, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        FileIconBadge(entry, selected)
                    }
                }
            } else {
                GhostThumbnail(ghost, ghostKind, 96, Modifier.size(36.dp).clip(MaterialTheme.shapes.small)) {
                    FileIconBadge(entry, selected)
                }
            }
        } else if (isVideoRow && account != null && kind != null) {
            // ভিডিও: ১৬:৯ আকার, কোণ প্রায় চৌকো (গোলাকার কম)
            Box(
                Modifier.width(71.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceContainerHigh)
                    .then(if (seekSp != null) Modifier.seekPreviewGestures(seekSp, onClick) else Modifier),
            ) {
                FileThumbnail(
                    account = account,
                    entry = entry,
                    kind = kind,
                    size = 71.dp,
                    aspect = 16f / 9f,
                    thumbPx = 240,
                ) { FileIconBadge(entry, selected) }
                if (seekSp != null) com.drivemount.app.ui.components.SeekPreviewOverlay(seekSp, Modifier.matchParentSize())
            }
        } else if (kind != null && account != null) {
            FileThumbnail(
                account = account,
                entry = entry,
                kind = kind,
                size = 36.dp,
                modifier = Modifier.clip(MaterialTheme.shapes.small),
            ) { FileIconBadge(entry, selected) }
        } else {
            FileIconBadge(entry, selected)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty() || offlineText != null) {
                // বাম পাশে শুধু আকার (ও ফোল্ডারের সংখ্যা); অফলাইন লেখা সবুজ
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (offlineText != null) {
                        if (subtitle.isNotEmpty()) Spacer(Modifier.width(8.dp))
                        Text(
                            text = offlineText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = okGreen(),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (replica != null || dateText != null || openPercent != null) {
            // ডান পাশে: উপরে ব্যাজ (⚠ 1/2 · ✓ 2/2), তার নিচে তারিখ
            Column(horizontalAlignment = Alignment.End) {
                if (openPercent != null) {
                    Text(
                        text = "$openPercent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        maxLines = 1,
                    )
                } else if (replica != null) ReplicaBadge(replica)
                if (dateText != null) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** "সব ঠিক আছে" সবুজ: অফলাইন লেখা ও ✓ 2/2 ব্যাজে */
@Composable
private fun okGreen(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF5FD38D) else Color(0xFF1E8E4A)

/** Galaxy: কপি-অবস্থা। পূর্ণ (✓ 2/2) নাকি অসম্পূর্ণ (⚠ 1/2, ঠিক করতে Sync copies) */
@Composable
private fun ReplicaBadge(status: ReplicaStatus) {
    val ok = status.have >= status.total
    val colors = MaterialTheme.colorScheme
    Text(
        text = (if (ok) "✓ " else "⚠ ") + "${status.have}/${status.total}",
        style = MaterialTheme.typography.labelSmall,
        color = if (ok) okGreen() else colors.error,
        maxLines = 1,
    )
}

/** ব্রাউজের চিহ্ন: ফোনে আছে / নামছে (পিন করা) */
@Composable
private fun offlineLabel(state: Int): String? = when (state) {
    2 -> stringResource(R.string.offline_badge_done)
    1 -> stringResource(R.string.offline_badge_saving)
    else -> null
}

@Composable
private fun PasteBar(clip: ClipboardContent, onPaste: () -> Unit, onCancel: () -> Unit) {
    val sp = MaterialTheme.spacing
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = sp.md, vertical = sp.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            Text(
                text = stringResource(
                    if (clip.move) R.string.browser_clip_move else R.string.browser_clip_copy,
                    clip.items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Button(onClick = onPaste) {
                Icon(Lucide.LuClipboardPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(sp.sm))
                Text(stringResource(R.string.browser_paste_here))
            }
        }
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit) {
    val sp = MaterialTheme.spacing
    Column(Modifier.padding(sp.md)) {
        DmCard {
            IconBadge(Lucide.LuLock)
            Spacer(Modifier.height(sp.md))
            Text(
                text = stringResource(R.string.access_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(sp.xs))
            Text(
                text = stringResource(R.string.access_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(sp.md))
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.access_grant)) }
        }
    }
}

@Composable
private fun MessageState(
    text: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val sp = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // স্ক্রলযোগ্য: খালি/ত্রুটির অবস্থাতেও টেনে রিফ্রেশ করা যায়
            .padding(sp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(sp.md))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun InfoLine(labelRes: Int, value: String) {
    Column {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** ডাউনলোড হওয়া ফাইল অন্য অ্যাপে খোলে; খোলার মতো অ্যাপ না থাকলে false */
internal fun openDownloadedFile(context: android.content.Context, path: String, mime: String): Boolean {
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

/** ফোনের স্টোরেজের দ্রুত যাওয়ার জায়গাগুলো; পথ কখনো স্থির ধরা হয় না */
private fun buildPlaces(context: android.content.Context): List<LocalPaths.Place> = buildList {
    add(LocalPaths.Place(context.getString(R.string.loc_internal), LocalPaths.internalRelative()))
    add(LocalPaths.Place(context.getString(R.string.loc_downloads), LocalPaths.internalRelative() + "/Download"))
    addAll(LocalPaths.removableVolumes(context))
    add(LocalPaths.Place(context.getString(R.string.loc_app_data), context.dataDir.absolutePath.trim('/')))
    add(LocalPaths.Place(context.getString(R.string.loc_root), ""))
}

/** ফাইলের ধরন অনুযায়ী আইকন: গোল ব্যাকগ্রাউন্ড ছাড়া, শুধু রঙিন আইকন (নির্বাচিত হলে টিক) */
@Composable
private fun FileIconBadge(entry: RemoteEntry, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (selected) Lucide.LuCheck else fileIcon(entry.isDir, entry.name, entry.mimeType),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** grid দৃশ্যের একটি ঘর: ওপরে থাম্বনেল বা বড় আইকন, নিচে নাম */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(
    entry: RemoteEntry,
    account: AccountEntity?,
    showThumbs: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    offline: Int = 0,
    highlighted: Boolean = false,
    replica: ReplicaStatus? = null,
    openPercent: Int? = null,
    /** আপলোড চলছে (তালিকায় এখনো আসল ফাইল নেই): ধূসর থেকে বাম→ডানে রঙিন */
    ghost: UploadGhost? = null,
) {
    val colors = MaterialTheme.colorScheme
    val sp = MaterialTheme.spacing
    val hl by androidx.compose.animation.animateColorAsState(
        if (highlighted) colors.tertiaryContainer else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "hl",
    )
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.secondaryContainer else hl)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(sp.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val kind = if (showThumbs && account != null) thumbKindOf(entry) else null
            val seekSp = if (kind == ThumbKind.VIDEO && account != null) com.drivemount.app.ui.components.rememberSeekPreview("b:${account.id}:${entry.path}", account, entry.path, entry.size) else null
            if (openPercent != null) {
                // নামার সময় ঘরের ওপর হালকা পর্দা ও শতাংশ
                Box(
                    Modifier.matchParentSize().zIndex(1f).background(colors.surface.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$openPercent%", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                }
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().then(if (seekSp != null) Modifier.seekPreviewGestures(seekSp, onClick) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (ghost != null) {
                    GhostThumbnail(ghost, if (showThumbs) thumbKindOf(entry) else null, 360, Modifier.fillMaxSize()) {
                        TileIcon(entry)
                    }
                } else if (kind != null && account != null) {
                    FileThumbnail(
                        account = account,
                        entry = entry,
                        kind = kind,
                        size = maxWidth,
                        modifier = Modifier.fillMaxSize(),
                    ) { TileIcon(entry) }
                    if (seekSp != null) com.drivemount.app.ui.components.SeekPreviewOverlay(seekSp, Modifier.fillMaxSize())
                } else {
                    TileIcon(entry)
                }
            }
            if (ghost != null) GhostBadge(ghost, Modifier.align(Alignment.BottomStart))
            offlineLabel(offline)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = okGreen(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(colors.surfaceContainerHighest, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (replica != null) {
                val ok = replica.have >= replica.total
                Text(
                    text = (if (ok) "✓ " else "⚠ ") + "${replica.have}/${replica.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ok) okGreen() else colors.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(if (ok) colors.surfaceContainerHighest else colors.errorContainer, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.LuCheck, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = sp.xs),
        )
    }
}

@Composable
private fun TileIcon(entry: RemoteEntry) {
    Icon(
        imageVector = fileIcon(entry.isDir, entry.name, entry.mimeType),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
    )
}


/** ফাইলের ধরন অনুযায়ী লাইব্রেরি-ট্যাবের prefs-নাম (না মিললে null) */
private fun libraryTabFor(e: RemoteEntry): String? {
    val ext = e.name.substringAfterLast('.', "").lowercase()
    return when {
        thumbKindOf(e) == ThumbKind.VIDEO -> "videos_source"
        thumbKindOf(e) == ThumbKind.IMAGE -> "photos_source"
        guessMime(e.name, e.mimeType).startsWith("audio/") -> "music_source"
        ext in com.drivemount.app.ui.videos.DOC_EXT -> "docs_source"
        ext in com.drivemount.app.ui.videos.APK_EXT -> "apks_source"
        else -> null
    }
}

/** ফাইল নিজ ট্যাবে, ফোল্ডার [folderTabs]-এ যোগ হয় (ট্যাব না খুলেও; পরে খুললে/খোলা থাকলে নেয়)। যোগ হওয়া আইটেম সংখ্যা ফেরত */
private fun addEntriesToLibrary(context: android.content.Context, accountId: Long, picked: List<RemoteEntry>, folderTabs: Set<String>, includeSub: Boolean): Int {
    val byTab = HashMap<String, MutableList<com.drivemount.app.ui.videos.LibraryInbox.Pending>>()
    for (e in picked) {
        if (e.isDir) {
            for (tab in folderTabs) byTab.getOrPut(tab) { ArrayList() }.add(com.drivemount.app.ui.videos.LibraryInbox.Pending(accountId, e.path, includeSub, false))
        } else {
            val tab = libraryTabFor(e) ?: continue
            byTab.getOrPut(tab) { ArrayList() }.add(com.drivemount.app.ui.videos.LibraryInbox.Pending(accountId, e.path, false, true))
        }
    }
    byTab.forEach { (tab, list) -> com.drivemount.app.ui.videos.LibraryInbox.post(tab, list) }
    return byTab.values.sumOf { it.size }
}

package com.drivemount.app.ui.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    vm: BrowserViewModel = viewModel(),
) {
    val ui by vm.state.collectAsState()
    val clip by vm.clipboard.collectAsState()
    val vaults by vm.vaults.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val sp = MaterialTheme.spacing

    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var infoTarget by remember { mutableStateOf<RemoteEntry?>(null) }
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

    // ফোনের স্টোরেজ (Local অ্যাকাউন্ট): অনুমতি
    var accessGranted by remember { mutableStateOf(hasStorageAccess(context)) }
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        accessGranted = hasStorageAccess(context)
        if (accessGranted) vm.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = hasStorageAccess(context)
        if (now && !accessGranted) vm.refresh()
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
    val selecting = ui.selected.isNotEmpty()

    BackHandler {
        when {
            selecting -> vm.clearSelection()
            ui.query != null -> vm.setQuery(null)
            !vm.up() -> onBack()
        }
    }

    Scaffold(
        topBar = {
            when {
                selecting -> TopAppBar(
                    title = { Text(stringResource(R.string.browser_selected, ui.selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        if (!isLocal) {
                            IconButton(onClick = { guardedDownload { vm.downloadSelected() } }) {
                                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.browser_download))
                            }
                        }
                        IconButton(onClick = { vm.copySelected(move = false) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.browser_copy))
                        }
                        IconButton(onClick = { vm.copySelected(move = true) }) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = stringResource(R.string.browser_cut))
                        }
                        if (ui.selected.size == 1) {
                            IconButton(
                                onClick = { renameTarget = ui.entries.firstOrNull { it.path in ui.selected } },
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.menu_rename))
                            }
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.browser_delete))
                        }
                    },
                )

                ui.query != null -> TopAppBar(
                    title = { SearchField(ui.query ?: "", onChange = { vm.setQuery(it) }) },
                    navigationIcon = {
                        IconButton(onClick = { vm.setQuery(null) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.browser_search))
                        }
                        IconButton(onClick = { themeRepo.update { it.copy(gridView = !it.gridView) } }) {
                            Icon(
                                imageVector = if (gridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = stringResource(if (gridView) R.string.view_list else R.string.view_grid),
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Rounded.SortByAlpha, contentDescription = stringResource(R.string.browser_sort))
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
                                                    },
                                                ),
                                            )
                                        },
                                        trailingIcon = {
                                            if (ui.sort.key == key) {
                                                Icon(
                                                    imageVector = if (ui.sort.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
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
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selecting && ui.query == null && !needsAccess && account != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(sp.md),
                ) {
                    if (!isLocal) {
                        SmallFloatingActionButton(onClick = { openUploadPicker() }) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = stringResource(R.string.browser_upload))
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showNewFolder = true },
                        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                        text = { Text(stringResource(R.string.browser_new_folder)) },
                    )
                }
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

            if (isLocal && !needsAccess) {
                val places = remember { buildPlaces(context) }
                LocalShortcuts(
                    places = places,
                    onGo = { vm.navigateTo(it) },
                    onGoToPath = { showGoTo = true },
                )
            }

            Box(
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
                        actionLabel = stringResource(R.string.browser_retry),
                        onAction = { vm.refresh() },
                    )

                    visible.isEmpty() -> MessageState(
                        text = stringResource(
                            if (ui.query.isNullOrBlank()) R.string.browser_empty else R.string.browser_no_match,
                        ),
                    )

                    else -> {
                        val onItemClick: (RemoteEntry) -> Unit = { entry ->
                            when {
                                selecting -> vm.toggleSelect(entry)
                                entry.isDir -> vm.open(entry)
                                else -> infoTarget = entry
                            }
                        }
                        if (gridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(104.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = sp.sm, end = sp.sm, top = sp.xs, bottom = 96.dp),
                                horizontalArrangement = Arrangement.spacedBy(sp.sm),
                                verticalArrangement = Arrangement.spacedBy(sp.sm),
                            ) {
                                items(visible, key = { it.path }) { entry ->
                                    FileTile(
                                        entry = entry,
                                        account = account,
                                        showThumbs = showThumbs,
                                        selected = entry.path in ui.selected,
                                        onClick = { onItemClick(entry) },
                                        onLongClick = { vm.toggleSelect(entry) },
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = sp.sm, end = sp.sm, top = sp.xs, bottom = 96.dp),
                            ) {
                                items(visible, key = { it.path }) { entry ->
                                    FileRow(
                                        entry = entry,
                                        account = account,
                                        showThumbs = showThumbs,
                                        selected = entry.path in ui.selected,
                                        onClick = { onItemClick(entry) },
                                        onLongClick = { vm.toggleSelect(entry) },
                                    )
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
            title = { Text(stringResource(R.string.browser_delete_title)) },
            text = { Text(stringResource(R.string.browser_delete_body, ui.selected.size)) },
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

    infoTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
                    InfoLine(R.string.info_type, entry.mimeType.ifBlank { "—" })
                    InfoLine(R.string.info_size, entry.size?.let { formatBytes(it) } ?: "—")
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
private fun Breadcrumbs(
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
                imageVector = Icons.Rounded.ChevronRight,
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: RemoteEntry,
    account: AccountEntity?,
    showThumbs: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val subtitle = listOfNotNull(
        entry.size?.let { formatBytes(it) },
        entry.modTimeMillis?.let { formatDate(it) },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = sp.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        val kind = if (showThumbs && account != null && !selected) thumbKindOf(entry) else null
        if (kind != null && account != null) {
            FileThumbnail(
                account = account,
                entry = entry,
                kind = kind,
                size = 48.dp,
                modifier = Modifier.clip(MaterialTheme.shapes.medium),
            ) { FileIconBadge(entry, selected) }
        } else {
            FileIconBadge(entry, selected)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
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
            IconBadge(Icons.Rounded.Lock)
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
private fun openDownloadedFile(context: android.content.Context, path: String, mime: String): Boolean {
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

@Composable
private fun LocalShortcuts(
    places: List<LocalPaths.Place>,
    onGo: (String) -> Unit,
    onGoToPath: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = sp.sm),
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        places.forEach { place ->
            AssistChip(onClick = { onGo(place.relativePath) }, label = { Text(place.label) })
        }
        AssistChip(onClick = onGoToPath, label = { Text(stringResource(R.string.loc_goto)) })
    }
}

/** ফাইলের ধরন অনুযায়ী গোল আইকন (নির্বাচিত হলে টিক) */
@Composable
private fun FileIconBadge(entry: RemoteEntry, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.Check else fileIcon(entry.isDir, entry.name, entry.mimeType),
            contentDescription = null,
            tint = if (selected) colors.onPrimary else colors.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
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
) {
    val colors = MaterialTheme.colorScheme
    val sp = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
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
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (kind != null && account != null) {
                    FileThumbnail(
                        account = account,
                        entry = entry,
                        kind = kind,
                        size = maxWidth,
                        modifier = Modifier.fillMaxSize(),
                    ) { TileIcon(entry) }
                } else {
                    TileIcon(entry)
                }
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
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
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

package com.drivemount.app.ui.home

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.House as LuHouse
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.LayoutGrid as LuLayoutGrid
import com.composables.icons.lucide.Puzzle as LuPuzzle
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.UserPlus as LuUserPlus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.ui.glass.LiquidBottomTab
import com.drivemount.app.ui.glass.LiquidBottomTabs
import com.drivemount.app.ui.glass.backdrop.backdrops.layerBackdrop
import com.drivemount.app.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.drivemount.app.ui.glass.isGlassSupported
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import com.drivemount.app.ui.theme.spacing
import com.drivemount.app.ui.videos.ApksScreen
import com.drivemount.app.ui.videos.DocsScreen
import com.drivemount.app.ui.videos.FavoritesScreen
import com.drivemount.app.ui.videos.MusicScreen
import com.drivemount.app.ui.videos.PhotosScreen
import com.drivemount.app.ui.videos.VideosScreen

private const val TAB_HOME = 0
private const val TAB_CLOUDS = 1
private const val TAB_ADD = 2 // ট্যাব নয়, শুধু মেনু খোলে
private const val TAB_ADDON = 3
private const val TAB_OTHERS = 4

// Home-এর Quick Access থেকে খোলা পূর্ণ-পর্দা মিডিয়া
private const val MEDIA_NONE = 0
private const val MEDIA_VIDEOS = 1
private const val MEDIA_MUSIC = 2
private const val MEDIA_PHOTOS = 3
private const val MEDIA_DOCS = 4
private const val MEDIA_APKS = 5
private const val MEDIA_FAVORITES = 6

/** নিচের ৫ ট্যাব: Home | Clouds | + | Addon | Others */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSync: () -> Unit,
    onOpenBackup: () -> Unit,
    onNewSync: () -> Unit,
    onPlay: (accountId: Long, path: String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var addMenu by remember { mutableStateOf(false) }

    var media by rememberSaveable { mutableIntStateOf(MEDIA_NONE) }

    BackHandler(enabled = tab != TAB_HOME || media != MEDIA_NONE) {
        if (media != MEDIA_NONE) media = MEDIA_NONE else tab = TAB_HOME
    }

    val playback = (LocalContext.current.applicationContext as DriveMountApp).playback
    DisposableEffect(Unit) { onDispose { playback.bottomBarPx.value = 0 } }

    val settings by (LocalContext.current.applicationContext as DriveMountApp)
        .themeRepository.settings.collectAsState()
    // Liquid glass: সেটিংস চালু এবং ডিভাইস সাপোর্ট করলে (Android 12+)
    val glass = settings.liquidGlass && isGlassSupported()

    val tabContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .fillMaxWidth()
                // নিচের বার নেভিগেশন-বারের জায়গা নিজেই নেয়; ভেতরের পর্দা যেন আবার না নেয়
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            when {
                tab == TAB_HOME && media == MEDIA_VIDEOS -> VideosScreen(onPlay = onPlay)
                tab == TAB_HOME && media == MEDIA_MUSIC -> MusicScreen(onPlay = onPlay)
                tab == TAB_HOME && media == MEDIA_PHOTOS -> PhotosScreen()
                tab == TAB_HOME && media == MEDIA_DOCS -> DocsScreen(onPlay = onPlay)
                tab == TAB_HOME && media == MEDIA_APKS -> ApksScreen(onPlay = onPlay)
                tab == TAB_HOME && media == MEDIA_FAVORITES -> FavoritesScreen(onPlay = onPlay)
                tab == TAB_HOME -> HomeScreen(
                    onOpenSettings = onOpenSettings,
                    onAddAccount = onAddAccount,
                    onOpenAccount = onOpenAccount,
                    onOpenTransfers = onOpenTransfers,
                    onOpenSearch = onOpenSearch,
                    onOpenSync = onOpenSync,
                    onOpenBackup = onOpenBackup,
                    onOpenVideos = { media = MEDIA_VIDEOS },
                    onOpenAudios = { media = MEDIA_MUSIC },
                    onOpenPhotos = { media = MEDIA_PHOTOS },
                    onOpenDocs = { media = MEDIA_DOCS },
                    onOpenApks = { media = MEDIA_APKS },
                    onOpenFavorites = { media = MEDIA_FAVORITES },
                    onOpenClouds = { tab = TAB_CLOUDS },
                )
                tab == TAB_CLOUDS -> HomeScreen(
                    onOpenSettings = onOpenSettings,
                    onAddAccount = onAddAccount,
                    onOpenAccount = onOpenAccount,
                    onOpenTransfers = onOpenTransfers,
                    onOpenSearch = onOpenSearch,
                    onOpenSync = onOpenSync,
                    onOpenBackup = onOpenBackup,
                    onOpenVideos = {},
                    onOpenAudios = {},
                    onOpenPhotos = {},
                    onOpenDocs = {},
                    onOpenApks = {},
                    onOpenFavorites = {},
                    cloudsOnly = true,
                )
                tab == TAB_ADDON -> AddonScreen()
                else -> OthersScreen()
            }
        }

    }

    if (glass) {
        val backdrop = rememberLayerBackdrop()
        val barHeight = 64.dp
        val barVertical = 10.dp
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val reserved = barHeight + barVertical * 2 + navInset
        val density = LocalDensity.current
        DisposableEffect(reserved) {
            playback.bottomBarPx.value = with(density) { reserved.roundToPx() }
            onDispose { }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // আসল কনটেন্ট — গ্লাস বার এটাকেই sample করে
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    tabContent(Modifier.weight(1f))
                    Spacer(Modifier.height(reserved))
                }
            }
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { i ->
                    when (i) {
                        TAB_ADD -> addMenu = true
                        else -> { tab = i; media = MEDIA_NONE }
                    }
                },
                backdrop = backdrop,
                tabsCount = 5,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                height = barHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = barVertical + navInset),
            ) {
                @Composable
                fun GlassTab(index: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int?, onClick: () -> Unit) {
                    val tint = if (tab == index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    LiquidBottomTab(onClick = onClick) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(if (label == null) 28.dp else 24.dp),
                        )
                        if (label != null) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                        }
                    }
                }
                GlassTab(TAB_HOME, Lucide.LuHouse, R.string.tab_home) { tab = TAB_HOME; media = MEDIA_NONE }
                GlassTab(TAB_CLOUDS, Lucide.LuCloud, R.string.tab_clouds) { tab = TAB_CLOUDS; media = MEDIA_NONE }
                GlassTab(TAB_ADD, Lucide.LuPlus, null) { addMenu = true }
                GlassTab(TAB_ADDON, Lucide.LuPuzzle, R.string.tab_addon) { tab = TAB_ADDON; media = MEDIA_NONE }
                GlassTab(TAB_OTHERS, Lucide.LuLayoutGrid, R.string.tab_others) { tab = TAB_OTHERS; media = MEDIA_NONE }
            }
        }
    } else {
        // আগের (Classic) UI — অপরিবর্তিত
        Column(modifier = Modifier.fillMaxSize()) {
            tabContent(Modifier.weight(1f))

        NavigationBar(modifier = Modifier.onGloballyPositioned { playback.bottomBarPx.value = it.size.height }) {
            NavigationBarItem(
                selected = tab == TAB_HOME,
                onClick = { tab = TAB_HOME; media = MEDIA_NONE },
                icon = { Icon(Lucide.LuHouse, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_home)) },
            )
            NavigationBarItem(
                selected = tab == TAB_CLOUDS,
                onClick = { tab = TAB_CLOUDS; media = MEDIA_NONE },
                icon = { Icon(Lucide.LuCloud, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_clouds)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = { addMenu = true },
                icon = { Icon(Lucide.LuPlus, contentDescription = stringResource(R.string.tab_add), modifier = Modifier.size(30.dp)) },
                label = null,
            )
            NavigationBarItem(
                selected = tab == TAB_ADDON,
                onClick = { tab = TAB_ADDON; media = MEDIA_NONE },
                icon = { Icon(Lucide.LuPuzzle, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_addon)) },
            )
            NavigationBarItem(
                selected = tab == TAB_OTHERS,
                onClick = { tab = TAB_OTHERS; media = MEDIA_NONE },
                icon = { Icon(Lucide.LuLayoutGrid, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_others)) },
            )
        }
        }
    }

    if (addMenu) {
        ModalBottomSheet(onDismissRequest = { addMenu = false }) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_account)) },
                leadingContent = { Icon(Lucide.LuUserPlus, contentDescription = null) },
                modifier = Modifier.clickable {
                    addMenu = false
                    onAddAccount()
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.tab_new_sync)) },
                leadingContent = { Icon(Lucide.LuRefreshCcw, contentDescription = null) },
                modifier = Modifier
                    .clickable {
                        addMenu = false
                        onNewSync()
                    }
                    .padding(bottom = 16.dp),
            )
        }
    }
}

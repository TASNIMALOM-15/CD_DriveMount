package com.drivemount.app.ui.home

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.House as LuHouse
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.LayoutGrid as LuLayoutGrid
import com.composables.icons.lucide.Puzzle as LuPuzzle
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.UserPlus as LuUserPlus
import com.composables.icons.lucide.Images as LuImages
import com.composables.icons.lucide.ArrowUpDown as LuArrowUpDown
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import com.drivemount.app.ui.components.Icon
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.drivemount.app.ui.smartliquid.LocalAppBackdrop
import com.drivemount.app.ui.smartliquid.SmartLiquidBar
import com.drivemount.app.ui.smartliquid.SmartLiquidGuard
import com.drivemount.app.ui.smartliquid.SmartLiquidBarHeight
import com.drivemount.app.ui.smartliquid.SmartLiquidTab
import com.drivemount.app.ui.smartliquid.rememberFloatingTabBarScrollConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import com.drivemount.app.ui.theme.spacing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp

/** নিচের বার কনটেন্টের ওপরে ভাসে; স্ক্রলের শেষে কনটেন্ট যেন বারের নিচে চাপা না পড়ে তার জন্য এই উচ্চতা */
val LocalBottomBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * ভাসমান বাটনের (FAB) নিচের ফাঁক: নিচের বার দেখা গেলে বারের উচ্চতা, স্ক্রলে বার লুকালে শুধু সিস্টেম-বারের ফাঁক।
 * আলাদা রাখা হয়েছে যাতে অ্যানিমেশনের সময় শুধু FAB রিকম্পোজ হয়, তালিকাগুলো নয়। শেলের বাইরে (Browser) ০।
 */
val LocalFabInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

private const val TAB_HOME = 0
private const val TAB_CLOUDS = 1
private const val TAB_LIBRARY = 2
private const val TAB_TRANSFERS = 3 // Transfers: নিচের বারসহ ট্যাব
private const val TAB_MORE = 4

private class NavItem(val index: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int)

private val navItems = listOf(
    NavItem(TAB_HOME, Lucide.LuHouse, R.string.tab_home),
    NavItem(TAB_CLOUDS, Lucide.LuCloud, R.string.tab_clouds),
    NavItem(TAB_LIBRARY, Lucide.LuImages, R.string.tab_library),
    NavItem(TAB_TRANSFERS, Lucide.LuArrowUpDown, R.string.transfers),
    NavItem(TAB_MORE, Lucide.LuLayoutGrid, R.string.tab_more),
)

/**
 * নিচের ৫ বাটন (সবসময় থাকে): Home | Clouds | Library | Transfers | More
 *  - Library: Videos/Photos/Audios/Docs/APKs/Favorites ওপরের chip-এ
 *  - More: Addon, Sync, Backup, Settings, Search, অ্যাকাউন্ট যোগ
 * ফাইল-তালিকায় নিচে স্ক্রল করলে বার সরে যায় (বেশি ফাইল দেখা যায়), ওপরে স্ক্রল করলে ফিরে আসে।
 */
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
    var lib by rememberSaveable { mutableIntStateOf(LibKind.VIDEOS) }
    var moreAddon by rememberSaveable { mutableStateOf(false) }

    fun goTab(id: Int) {
        when (id) {
            else -> { tab = id; moreAddon = false }
        }
    }
    // অন্য পর্দা/সোয়াইপ থেকে আসা "এই ট্যাবে যাও" অনুরোধ
    val app0 = LocalContext.current.applicationContext as DriveMountApp
    val tabReq by app0.tabRequest.collectAsState()
    var reqSeen by rememberSaveable { mutableIntStateOf(tabReq.n) }
    androidx.compose.runtime.LaunchedEffect(tabReq.n) {
        if (tabReq.n != reqSeen) {
            reqSeen = tabReq.n
            goTab(tabReq.tab)
        }
    }

    // স্ক্রলের দিক দেখে নিচের বার লুকানো/দেখানো। নিচে স্ক্রল (কনটেন্ট ওপরে ওঠে) = লুকাও, ওপরে স্ক্রল = দেখাও
    var barVisible by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(tab, moreAddon) { barVisible = true }
    val scrollConn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) barVisible = false else if (available.y > 14f) barVisible = true
                return Offset.Zero
            }
        }
    }
    val barShift by animateFloatAsState(if (barVisible) 0f else 1f, label = "barShift")

    // Home-এ প্রথম ব্যাকে অ্যাপ বন্ধ হয় না: "আবার চাপলে বের হবে" দেখায়, ২ সেকেন্ডের মধ্যে আবার চাপলে বের হয়
    val backCtx = LocalContext.current
    var lastBack by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            tab == TAB_MORE && moreAddon -> moreAddon = false
            tab != TAB_HOME -> tab = TAB_HOME
            else -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastBack < 2000L) {
                    var c: android.content.Context = backCtx
                    while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
                    (c as? android.app.Activity)?.finish()
                } else {
                    lastBack = now
                    android.widget.Toast.makeText(backCtx, R.string.press_back_again, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val playback = (LocalContext.current.applicationContext as DriveMountApp).playback
    DisposableEffect(Unit) { onDispose { playback.bottomBarPx.value = 0 } }

    val settings by (LocalContext.current.applicationContext as DriveMountApp)
        .themeRepository.settings.collectAsState()
    // Liquid glass: সেটিংস চালু এবং ডিভাইস সাপোর্ট করলে (Android 12+)
    NavStylePrefs.load(LocalContext.current)
    val navStyle = NavStylePrefs.effective(settings.liquidGlass)
    val glass = navStyle == NavStyle.LIQUID_GLASS && isGlassSupported()
    val pill = navStyle == NavStyle.PILL
    val smart = navStyle == NavStyle.SMART_LIQUID && isGlassSupported()

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current

    val tabContent: @Composable (Modifier, Dp, Dp) -> Unit = { modifier, inset, fabInset ->
        Box(
            modifier = modifier
                .fillMaxWidth()
                // নিচের বার নেভিগেশন-বারের জায়গা নিজেই নেয়; ভেতরের পর্দা যেন আবার না নেয়
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            val bgColor = MaterialTheme.colorScheme.background
            val topFade = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
            androidx.compose.runtime.CompositionLocalProvider(
                LocalBottomBarInset provides inset,
                LocalFabInset provides fabInset,
                com.drivemount.app.ui.components.LocalTabNav provides com.drivemount.app.ui.components.TabNav(tab) { goTab(it) },
            ) {
                androidx.compose.animation.Crossfade(targetState = tab to moreAddon, animationSpec = androidx.compose.animation.core.tween(90), label = "tabSwitch") { (t, addon) ->
                    when (t) {
                        TAB_HOME -> HomeScreen(
                            onOpenSettings = onOpenSettings,
                            onAddAccount = onAddAccount,
                            onOpenAccount = onOpenAccount,
                            onOpenTransfers = { goTab(TAB_TRANSFERS) },
                            onOpenSearch = onOpenSearch,
                            onOpenSync = onOpenSync,
                            onOpenBackup = onOpenBackup,
                            onOpenVideos = { lib = LibKind.VIDEOS; tab = TAB_LIBRARY },
                            onOpenAudios = { lib = LibKind.MUSIC; tab = TAB_LIBRARY },
                            onOpenPhotos = { lib = LibKind.PHOTOS; tab = TAB_LIBRARY },
                            onOpenDocs = { lib = LibKind.DOCS; tab = TAB_LIBRARY },
                            onOpenApks = { lib = LibKind.APKS; tab = TAB_LIBRARY },
                            onOpenFavorites = { lib = LibKind.FAVORITES; tab = TAB_LIBRARY },
                            onOpenClouds = { tab = TAB_CLOUDS },
                        )
                        TAB_CLOUDS -> HomeScreen(
                            onOpenSettings = onOpenSettings,
                            onAddAccount = onAddAccount,
                            onOpenAccount = onOpenAccount,
                            onOpenTransfers = { goTab(TAB_TRANSFERS) },
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
                        TAB_LIBRARY -> LibraryScreen(kind = lib, onKind = { lib = it }, onPlay = onPlay)
                        TAB_TRANSFERS -> com.drivemount.app.ui.transfers.TransferScreen(onBack = { tab = TAB_HOME }, embedded = true)
                        else -> if (addon) AddonScreen() else MoreScreen(
                            onOpenAddon = { moreAddon = true },
                            onOpenSettings = onOpenSettings,
                            onOpenSearch = onOpenSearch,
                            onOpenSync = onOpenSync,
                            onNewSync = onNewSync,
                            onOpenBackup = onOpenBackup,
                            onAddAccount = onAddAccount,
                        )
                    }
                }
            }
            // একদম উপরে (স্ট্যাটাস-বার সীমায়) কনটেন্ট ঝাপসা হয়ে মিশে যায়; টাচ আটকায় না
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topFade)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bgColor, bgColor.copy(alpha = 0f)))),
            )
        }
    }

    if (smart) {
        val backdrop = rememberLayerBackdrop()
        val smartScroll = rememberFloatingTabBarScrollConnection()
        val smartTabs = navItems.map { SmartLiquidTab(stringResource(it.label), it.icon) }
        val smartCtx = LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2500)
            NavStylePrefs.confirmSmart(smartCtx)
        }
        val barInset = maxOf(navInset, 15.dp)
        val reserved = SmartLiquidBarHeight + barInset
        val reservedPx = with(density) { reserved.roundToPx() }
        DisposableEffect(reservedPx) {
            playback.bottomBarPx.value = reservedPx
            onDispose { }
        }
        Box(modifier = Modifier.fillMaxSize().nestedScroll(smartScroll)) {
            // The real content; every SmartLiquid glass surface samples it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                tabContent(Modifier.fillMaxSize(), reserved, reserved)
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
                SmartLiquidGuard(
                    onFailure = { e ->
                        com.drivemount.app.CrashReporter.log(smartCtx, e)
                        NavStylePrefs.set(smartCtx, NavStyle.ORIGINAL)
                        android.widget.Toast.makeText(smartCtx, "SmartLiquid is not supported on this device. Switched back to Original.", android.widget.Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    SmartLiquidBar(
                        tabs = smartTabs,
                        selectedIndex = tab,
                        onTabSelected = { goTab(it) },
                        scrollConnection = smartScroll,
                    )
                }
            }
        }
    } else if (glass) {
        val backdrop = rememberLayerBackdrop()
        val barHeight = 64.dp
        val barVertical = 10.dp
        val reserved = barHeight + barVertical * 2 + navInset
        val fabInset by animateDpAsState(if (barVisible) reserved else navInset, label = "fabInset")
        val shownPx = with(density) { reserved.roundToPx() }
        val hiddenPx = with(density) { navInset.roundToPx() }
        DisposableEffect(barVisible, shownPx, hiddenPx) {
            playback.bottomBarPx.value = if (barVisible) shownPx else hiddenPx
            onDispose { }
        }
        val shiftPx = with(density) { (barHeight + barVertical + navInset).toPx() }
        Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollConn)) {
            // আসল কনটেন্ট — গ্লাস বার এটাকেই sample করে
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // নিচের জায়গা ফাঁকা ব্লক রাখা হয় না; কনটেন্ট বারের পেছনেও দেখা যায়
                tabContent(Modifier.fillMaxSize(), reserved, fabInset)
            }
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { i -> goTab(i) },
                backdrop = backdrop,
                tabsCount = navItems.size,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                height = barHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = barShift * shiftPx }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = barVertical + navInset),
            ) {
                navItems.forEach { item ->
                    val tint = if (tab == item.index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    LiquidBottomTab(onClick = { goTab(item.index) }) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(stringResource(item.label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                    }
                }
            }
        }
    } else if (pill) {
        val barHeight = 60.dp
        val barVertical = 10.dp
        val reserved = barHeight + barVertical * 2 + navInset
        val fabInset by animateDpAsState(if (barVisible) reserved else navInset, label = "fabInset")
        val shownPx = with(density) { reserved.roundToPx() }
        val hiddenPx = with(density) { navInset.roundToPx() }
        DisposableEffect(barVisible, shownPx, hiddenPx) {
            playback.bottomBarPx.value = if (barVisible) shownPx else hiddenPx
            onDispose { }
        }
        val shiftPx = with(density) { (barHeight + barVertical + navInset).toPx() }
        Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollConn)) {
            tabContent(Modifier.fillMaxSize(), reserved, fabInset)
            PillNavBar(
                selected = tab,
                onSelect = { goTab(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = barShift * shiftPx }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = barVertical + navInset),
            )
        }
    } else {
        // আগের (Classic) UI
        var barPx by remember { mutableIntStateOf(0) }
        val barDp = with(density) { barPx.toDp() }
        val fabInset by animateDpAsState(if (barVisible) barDp else navInset, label = "fabInset")
        androidx.compose.runtime.LaunchedEffect(barVisible, barPx, navInset) {
            playback.bottomBarPx.value = if (barVisible) barPx else with(density) { navInset.roundToPx() }
        }
        Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollConn)) {
            tabContent(Modifier.fillMaxSize(), barDp, fabInset)

            NavigationBar(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { barPx = it.size.height }
                    .graphicsLayer { translationY = barShift * size.height },
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.index,
                        onClick = { goTab(item.index) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label), maxLines = 1) },
                    )
                }
            }
        }
    }
}


/**
 * "Glass Pill" নেভিগেশন — ভিডিওর মাঝের স্টাইল:
 * কাচের রেখা-ক্যাপসুলে প্রথম ৪টা ট্যাব; একটাই সবুজ কাচের পিল spring-এ গড়িয়ে নির্বাচিত ট্যাবে যায় (আইকন + নাম নিয়ে),
 * বাকিগুলো শুধু আইকন; চাপ ধরলে আইকন mint-সবুজ। শেষ ট্যাব (More) ডানে আলাদা গোল কাচের বোতাম।
 */
@Composable
private fun PillNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val white = androidx.compose.ui.graphics.Color.White
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capsule = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
    val circle = androidx.compose.foundation.shape.CircleShape
    val glassFill = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(white.copy(alpha = 0.10f), white.copy(alpha = 0.03f)))
    val rim = androidx.compose.ui.graphics.Brush.linearGradient(listOf(white.copy(alpha = 0.60f), white.copy(alpha = 0.14f), white.copy(alpha = 0.34f)))
    val tealFill = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(GlassColors.BlueTop, GlassColors.BlueBottom))
    val tealRim = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(GlassColors.BlueRim.copy(alpha = 0.85f), GlassColors.BlueRim.copy(alpha = 0.35f)))
    val tabs = navItems.dropLast(1)
    val more = navItems.last()
    // নির্বাচিত ট্যাবের জায়গা (px, ক্যাপসুলের ভেতরের Row-এর সাপেক্ষে) — সবুজ পিল সেখানে spring-এ যায়
    val xs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    val ws = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    val spec = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.74f, stiffness = 380f)
    val tx = xs[selected] ?: 0f
    val tw = ws[selected] ?: 0f
    val ix by androidx.compose.animation.core.animateFloatAsState(tx, spec, label = "pillX")
    val iw by androidx.compose.animation.core.animateFloatAsState(tw, spec, label = "pillW")

    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .androidxClip(capsule)
                .background(glassFill)
                .androidxBorder(rim, capsule)
                .padding(3.dp)
                .androidxBorder(white.copy(alpha = 0.08f), capsule),
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)) {
                if (iw > 1f) {
                    val pill = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .androidxOffsetX(ix)
                            .width(with(density) { iw.toDp() })
                            .height(48.dp)
                            .androidxShadow(pill, GlassColors.BlueTop.copy(alpha = 0.55f), 10.dp)
                            .androidxClip(pill)
                            .background(tealFill)
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(white.copy(alpha = 0.14f), androidx.compose.ui.graphics.Color.Transparent), endY = 70f))
                            .androidxBorder(tealRim, pill),
                    )
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { item ->
                        val sel = selected == item.index
                        val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressed by src.collectIsPressedAsState()
                        val tint by androidx.compose.animation.animateColorAsState(
                            when {
                                sel -> androidx.compose.ui.graphics.Color.White
                                pressed -> GlassColors.Mint
                                else -> GlassColors.Idle
                            },
                            label = "pillTint",
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .height(48.dp)
                                .androidxMeasured { x, w -> xs[item.index] = x; ws[item.index] = w }
                                .androidxAnimateSize()
                                .clickable(interactionSource = src, indication = null) { onSelect(item.index) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            androidx.compose.animation.AnimatedVisibility(
                                visible = sel,
                                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 120)) +
                                    androidx.compose.animation.expandHorizontally(),
                                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) +
                                    androidx.compose.animation.shrinkHorizontally(),
                            ) {
                                androidx.compose.foundation.layout.Row {
                                    Spacer(Modifier.androidxWidth(8.dp))
                                    Text(stringResource(item.label), style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
        val moreSel = selected == more.index
        val moreSrc = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val morePressed by moreSrc.collectIsPressedAsState()
        val moreAlpha by androidx.compose.animation.core.animateFloatAsState(if (moreSel) 1f else 0f, spec, label = "moreFill")
        val moreTint by androidx.compose.animation.animateColorAsState(
            when {
                moreSel -> androidx.compose.ui.graphics.Color.White
                morePressed -> GlassColors.Mint
                else -> GlassColors.Idle
            },
            label = "moreTint",
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .androidxClip(circle)
                .background(glassFill)
                .background(tealFill, alpha = moreAlpha)
                .androidxBorder(if (moreSel) tealRim else rim, circle)
                .clickable(interactionSource = moreSrc, indication = null) { onSelect(more.index) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(more.icon, contentDescription = stringResource(more.label), tint = moreTint, modifier = Modifier.size(24.dp))
        }
    }
}

private fun Modifier.androidxOffsetX(px: Float) = this.then(Modifier.offset { androidx.compose.ui.unit.IntOffset(px.toInt(), 0) })
private fun Modifier.androidxMeasured(onPos: (Float, Float) -> Unit) =
    this.then(Modifier.onGloballyPositioned { c -> onPos(c.positionInParent().x, c.size.width.toFloat()) })
private fun Modifier.androidxShadow(shape: androidx.compose.ui.graphics.Shape, color: androidx.compose.ui.graphics.Color, elevation: Dp = 12.dp) =
    this.then(Modifier.shadow(elevation, shape, ambientColor = color, spotColor = color))
private fun Modifier.androidxClip(shape: androidx.compose.ui.graphics.Shape) = this.then(Modifier.clip(shape))
private fun Modifier.androidxBorder(brush: androidx.compose.ui.graphics.Brush, shape: androidx.compose.ui.graphics.Shape) =
    this.then(Modifier.border(1.dp, brush, shape))
private fun Modifier.androidxBorder(color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape) =
    this.then(Modifier.border(1.dp, color, shape))
private fun Modifier.androidxAnimateSize() = this.then(Modifier.animateContentSize())
private fun Modifier.androidxWidth(w: Dp) = this.then(Modifier.width(w))

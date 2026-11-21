package com.drivemount.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.drivemount.app.ui.accounts.AddAccountScreen
import com.drivemount.app.ui.backup.BackupScreen
import com.drivemount.app.ui.browser.BrowserScreen
import com.drivemount.app.ui.home.MainShell
import com.drivemount.app.DriveMountApp
import com.drivemount.app.ui.player.PlayerHost
import com.drivemount.app.ui.search.SearchScreen
import com.drivemount.app.ui.settings.SettingsScreen
import com.drivemount.app.ui.sync.EditSyncScreen
import com.drivemount.app.ui.sync.SyncScreen
import com.drivemount.app.ui.transfers.TransferScreen
import com.drivemount.app.ui.vault.AddVaultScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ADD_ACCOUNT = "add_account"
    const val TRANSFERS = "transfers"
    const val SYNC = "sync"
    const val BACKUP = "backup"
    const val ADD_VAULT = "add_vault"
    const val SYNC_EDIT = "sync/edit/{id}"

    fun syncEdit(id: Long) = "sync/edit/$id"
    const val BROWSER = "browser/{accountId}?path={path}&hl={hl}"
    const val SEARCH = "search"
    const val ZIP = "zip/{accountId}?path={path}&size={size}"
    fun zip(accountId: Long, path: String, size: Long) = "zip/$accountId?path=${Uri.encode(path)}&size=$size"

    /** path খালি হলে অ্যাকাউন্টের শুরুর ফোল্ডার; নইলে সেই ফোল্ডারে সরাসরি */
    fun browser(accountId: Long, path: String = "", highlight: String = ""): String {
        if (path.isEmpty() && highlight.isEmpty()) return "browser/$accountId"
        return "browser/$accountId?path=${Uri.encode(path)}&hl=${Uri.encode(highlight)}"
    }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val appCtx = LocalContext.current.applicationContext as DriveMountApp
    val playback = appCtx.playback
    // নোটিফিকেশন থেকে আসা "এই ফাইলে যাও" অনুরোধ: ফাইলের ফোল্ডারে খুলে ফাইলটি হাইলাইট করে
    val target by appCtx.openTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        appCtx.openTarget.value = null
        nav.navigate(Routes.browser(t.accountId, t.path, t.highlight))
    }
    val vaultReq by appCtx.vaultRequest.collectAsState()
    LaunchedEffect(vaultReq) {
        val id = vaultReq ?: return@LaunchedEffect
        appCtx.vaultRequest.value = null
        appCtx.vaultPreset = id
        nav.navigate(Routes.ADD_VAULT)
    }
    val tabNav = androidx.compose.runtime.remember(nav) {
        com.drivemount.app.ui.components.TabNav(null) { id ->
            // ব্রাউজার ইত্যাদি থেকে ট্যাবে যেতে: Home-এ ফিরে সেই ট্যাব খোলা
            if (nav.currentBackStackEntry?.destination?.route != Routes.HOME) nav.popBackStack(Routes.HOME, false)
            appCtx.tabRequest.value = com.drivemount.app.ui.components.TabRequest(id, appCtx.tabRequest.value.n + 1)
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(com.drivemount.app.ui.components.LocalTabNav provides tabNav) {
    // ক্লাউড ব্রাউজারে: ডানে সোয়াইপ = Home (পুরো উচ্চতায়), বামে = ভল্ট (BrowserScreen নিজে সামলায়); অন্য পর্দায় আগের মতো
    val navEntry by nav.currentBackStackEntryAsState()
    val inBrowser = navEntry?.destination?.route == Routes.BROWSER
    BandSwipeHost(
        left = !inBrowser,
        fullHeight = inBrowser,
        icon = com.drivemount.app.ui.components.AppIcons.byKey.getValue("House"),
        modifier = Modifier.fillMaxSize(),
        onSwipe = {
            // মাঝখানে বামে সোয়াইপ: Home ট্যাবে ফেরা (অন্য পর্দায় থাকলে পেছনে সরে, Home-এর ভেতরের ট্যাব/মিডিয়াও রিসেট)
            if (nav.currentBackStackEntry?.destination?.route != Routes.HOME) nav.popBackStack(Routes.HOME, false)
            appCtx.tabRequest.value = com.drivemount.app.ui.components.TabRequest(com.drivemount.app.ui.components.TabIds.HOME, appCtx.tabRequest.value.n + 1)
        },
    ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                MainShell(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onAddAccount = { nav.navigate(Routes.ADD_ACCOUNT) },
                    onOpenAccount = { nav.navigate(Routes.browser(it)) },
                    onOpenTransfers = { nav.navigate(Routes.TRANSFERS) },
                    onOpenSearch = { nav.navigate(Routes.SEARCH) },
                    onOpenSync = { nav.navigate(Routes.SYNC) },
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                    onNewSync = { nav.navigate(Routes.syncEdit(0L)) },
                    onPlay = { id, path -> playback.open(id, path) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.ADD_ACCOUNT) {
                AddAccountScreen(
                    onBack = { nav.popBackStack() },
                    onOpenVault = { nav.navigate(Routes.ADD_VAULT) },
                )
            }
            composable(
                route = Routes.BROWSER,
                enterTransition = { if (isVaultEntry(targetState)) slideInHorizontally { -it } else null },
                exitTransition = { if (isVaultEntry(targetState)) slideOutHorizontally { it / 3 } else null },
                popEnterTransition = { if (isVaultEntry(initialState)) slideInHorizontally { it / 3 } else null },
                popExitTransition = { if (isVaultEntry(initialState)) slideOutHorizontally { -it } else null },
                arguments = listOf(
                    navArgument("accountId") { type = NavType.LongType },
                    navArgument("path") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("hl") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                EdgeDeadZones {
                BrowserScreen(
                    onBack = { nav.popBackStack() },
                    onOpenTransfers = { nav.navigate(Routes.TRANSFERS) },
                    onCreateVault = { nav.navigate(Routes.ADD_VAULT) },
                    onPlay = { id, path -> playback.open(id, path) },
                    onOpenVault = { vaultSlideIds.add(it); nav.navigate(Routes.browser(it)) },
                    onOpenZip = { id, path, size -> nav.navigate(Routes.zip(id, path, size)) },
                )
                }
            }
            composable(
                route = Routes.ZIP,
                arguments = listOf(
                    navArgument("accountId") { type = NavType.LongType },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    navArgument("size") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                com.drivemount.app.ui.zip.ZipViewerScreen(
                    accountId = e.arguments?.getLong("accountId") ?: 0L,
                    path = e.arguments?.getString("path").orEmpty(),
                    size = e.arguments?.getLong("size") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { id, path, hl -> nav.navigate(Routes.browser(id, path, hl)) },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ADD_VAULT) {
                AddVaultScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.TRANSFERS) {
                TransferScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SYNC) {
                SyncScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.syncEdit(it)) },
                )
            }
            composable(
                route = Routes.SYNC_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                EditSyncScreen(onBack = { nav.popBackStack() })
            }
        }
        PlayerHost(playback)
    }
    }
}

/** ভল্ট-অ্যাকাউন্টের আইডি: এগুলো খুলতে/বন্ধ করতে পাশ থেকে স্লাইড অ্যানিমেশন চলে */
internal val vaultSlideIds = mutableSetOf<Long>()

private fun isVaultEntry(e: NavBackStackEntry): Boolean =
    e.arguments?.getLong("accountId")?.let { it in vaultSlideIds } == true

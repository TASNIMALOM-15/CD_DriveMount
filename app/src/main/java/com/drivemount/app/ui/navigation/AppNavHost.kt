package com.drivemount.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.drivemount.app.ui.accounts.AddAccountScreen
import com.drivemount.app.ui.backup.BackupScreen
import com.drivemount.app.ui.browser.BrowserScreen
import com.drivemount.app.ui.home.MainShell
import com.drivemount.app.DriveMountApp
import com.drivemount.app.ui.player.PlayerHost
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
    const val BROWSER = "browser/{accountId}"

    fun browser(accountId: Long) = "browser/$accountId"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val playback = (LocalContext.current.applicationContext as DriveMountApp).playback
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                MainShell(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onAddAccount = { nav.navigate(Routes.ADD_ACCOUNT) },
                    onOpenAccount = { nav.navigate(Routes.browser(it)) },
                    onOpenTransfers = { nav.navigate(Routes.TRANSFERS) },
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
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) {
                BrowserScreen(
                    onBack = { nav.popBackStack() },
                    onOpenTransfers = { nav.navigate(Routes.TRANSFERS) },
                    onCreateVault = { nav.navigate(Routes.ADD_VAULT) },
                    onPlay = { id, path -> playback.open(id, path) },
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

package com.drivemount.app.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VideoLibrary
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
import com.drivemount.app.ui.theme.spacing
import com.drivemount.app.ui.videos.MusicScreen
import com.drivemount.app.ui.videos.PhotosScreen
import com.drivemount.app.ui.videos.VideosScreen

private const val TAB_HOME = 0
private const val TAB_VIDEOS = 1
private const val TAB_ADD = 2 // ট্যাব নয়, শুধু মেনু খোলে
private const val TAB_MUSIC = 3
private const val TAB_PHOTOS = 4

/** নিচের ৫ ট্যাব: Home | Videos | + | Music | Others */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenBackup: () -> Unit,
    onNewSync: () -> Unit,
    onPlay: (accountId: Long, path: String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var addMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = tab != TAB_HOME) { tab = TAB_HOME }

    val playback = (LocalContext.current.applicationContext as DriveMountApp).playback
    DisposableEffect(Unit) { onDispose { playback.bottomBarPx.value = 0 } }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // নিচের বার নেভিগেশন-বারের জায়গা নিজেই নেয়; ভেতরের পর্দা যেন আবার না নেয়
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            when (tab) {
                TAB_HOME -> HomeScreen(
                    onOpenSettings = onOpenSettings,
                    onAddAccount = onAddAccount,
                    onOpenAccount = onOpenAccount,
                    onOpenTransfers = onOpenTransfers,
                    onOpenSync = onOpenSync,
                    onOpenBackup = onOpenBackup,
                )
                TAB_VIDEOS -> VideosScreen(onPlay = onPlay)
                TAB_MUSIC -> MusicScreen(onPlay = onPlay)
                else -> PhotosScreen()
            }
        }

        NavigationBar(modifier = Modifier.onGloballyPositioned { playback.bottomBarPx.value = it.size.height }) {
            NavigationBarItem(
                selected = tab == TAB_HOME,
                onClick = { tab = TAB_HOME },
                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_home)) },
            )
            NavigationBarItem(
                selected = tab == TAB_VIDEOS,
                onClick = { tab = TAB_VIDEOS },
                icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_videos)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = { addMenu = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tab_add), modifier = Modifier.size(30.dp)) },
                label = null,
            )
            NavigationBarItem(
                selected = tab == TAB_MUSIC,
                onClick = { tab = TAB_MUSIC },
                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_music)) },
            )
            NavigationBarItem(
                selected = tab == TAB_PHOTOS,
                onClick = { tab = TAB_PHOTOS },
                icon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_photos)) },
            )
        }
    }

    if (addMenu) {
        ModalBottomSheet(onDismissRequest = { addMenu = false }) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_account)) },
                leadingContent = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                modifier = Modifier.clickable {
                    addMenu = false
                    onAddAccount()
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.tab_new_sync)) },
                leadingContent = { Icon(Icons.Rounded.Sync, contentDescription = null) },
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

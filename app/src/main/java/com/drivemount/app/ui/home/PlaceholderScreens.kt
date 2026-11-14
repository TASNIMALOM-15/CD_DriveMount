package com.drivemount.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LayoutGrid as LuLayoutGrid
import com.composables.icons.lucide.Puzzle as LuPuzzle
import com.composables.icons.lucide.Play as LuPlay
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.R
import kotlinx.coroutines.launch

/** Addon ট্যাব: পরে এখানে plugin/extension যোগ করা যাবে (এখন শুধু কাঠামো) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonScreen() {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val soon = stringResource(R.string.qa_coming_soon)
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.addon_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(Lucide.LuPuzzle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.addon_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.addon_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = { scope.launch { snackbar.showSnackbar(soon) } }) {
                    Text(stringResource(R.string.addon_add))
                }
            }
        }
    }
}

/** Others ট্যাব: আপাতত খালি */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OthersScreen() {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.others_title)) }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Lucide.LuLayoutGrid, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.others_empty), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

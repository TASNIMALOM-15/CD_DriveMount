package com.drivemount.app.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.browseRootFor
import com.drivemount.app.engine.parentPath
import com.drivemount.app.engine.startPathFor
import com.drivemount.app.ui.components.providerIcon
import com.drivemount.app.ui.theme.spacing

/** অ্যাকাউন্ট বেছে তার ভেতরের একটি ফোল্ডার নির্বাচনের ডায়ালগ */
@Composable
fun FolderPickerDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onPicked: (accountId: Long, path: String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as DriveMountApp
    val sp = MaterialTheme.spacing

    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var path by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(account, path) {
        val a = account ?: return@LaunchedEffect
        loading = true
        error = null
        app.fileRepository.list(a, path).fold(
            onSuccess = { list -> folders = list.filter { it.isDir }.sortedBy { it.name.lowercase() } },
            onFailure = { e ->
                folders = emptyList()
                error = e.message ?: e.toString()
            },
        )
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(sp.md)) {
                val current = account
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current != null) {
                        IconButton(
                            onClick = {
                                val root = browseRootFor(current.backendType)
                                if (path == root) {
                                    account = null
                                    path = ""
                                } else {
                                    path = parentPath(path).let { if (it.length < root.length) root else it }
                                }
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                    Text(
                        text = if (current == null) {
                            stringResource(R.string.picker_title_account)
                        } else {
                            current.displayName + "/" + path.removePrefix(browseRootFor(current.backendType)).trim('/')
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = if (current == null) sp.sm else 0.dp),
                    )
                }
                Spacer(Modifier.height(sp.sm))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val message = error
                    when {
                        current == null -> LazyColumn(Modifier.fillMaxSize()) {
                            items(accounts, key = { it.id }) { a ->
                                PickerRow(providerIcon(a.backendType), a.displayName) {
                                    account = a
                                    path = startPathFor(a.backendType)
                                }
                            }
                        }

                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                        message != null -> Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(sp.sm),
                        )

                        folders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }

                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(folders, key = { it.path }) { f ->
                                PickerRow(Icons.Rounded.Folder, f.name) { path = f.path }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.size(sp.sm))
                    Button(
                        enabled = current != null && !loading,
                        onClick = { if (current != null) onPicked(current.id, path) },
                    ) { Text(stringResource(R.string.picker_select)) }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = sp.sm, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

package com.drivemount.app.ui.videos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.parentPath
import com.drivemount.app.engine.startPathFor

/**
 * ক্লাউড অ্যাকাউন্ট বেছে ফোল্ডারে ঢুকে একটি ফোল্ডার নির্বাচনের পূর্ণ-পর্দা ডায়ালগ।
 * Videos এবং পরে Music ট্যাবে একই ডায়ালগ ব্যবহার হবে।
 */
@Composable
fun FolderPickerDialog(
    onPick: (AccountEntity, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DriveMountApp
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var path by remember { mutableStateOf("") }

    val accounts by produceState<List<AccountEntity>?>(null) { value = app.accountRepository.getAll() }

    val folders by produceState<Result<List<RemoteEntry>>?>(null, account, path) {
        val acc = account
        value = null
        if (acc != null) value = app.fileRepository.list(acc, path)
    }

    fun goBack() {
        val acc = account
        when {
            acc == null -> onDismiss()
            path != startPathFor(acc.backendType) && path.isNotEmpty() -> path = parentPath(path)
            else -> account = null
        }
    }

    Dialog(
        onDismissRequest = { goBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(
                        text = account?.let { it.displayName + if (path.isEmpty()) "" else " / $path" }
                            ?: stringResource(R.string.videos_pick_account),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                HorizontalDivider()

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val acc = account
                    if (acc == null) {
                        val list = accounts
                        if (list == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(list, key = { it.id }) { a ->
                                    ListItem(
                                        headlineContent = { Text(a.displayName) },
                                        leadingContent = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
                                        modifier = Modifier.clickable {
                                            account = a
                                            path = startPathFor(a.backendType)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        val res = folders
                        when {
                            res == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            res.isFailure -> Text(
                                text = res.exceptionOrNull()?.message ?: "Error",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                            else -> {
                                val dirs = res.getOrNull().orEmpty().filter { it.isDir }
                                    .sortedBy { it.name.lowercase() }
                                if (dirs.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.videos_no_subfolders),
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(dirs, key = { it.path }) { d ->
                                            ListItem(
                                                headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                                modifier = Modifier.clickable { path = d.path },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val acc = account
                if (acc != null) {
                    HorizontalDivider()
                    Button(
                        onClick = { onPick(acc, path) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) { Text(stringResource(R.string.videos_select_folder)) }
                }
            }
        }
    }
}

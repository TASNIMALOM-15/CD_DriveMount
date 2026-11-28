package com.drivemount.app.ui.videos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X as LuX
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.LocalPaths
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.parentPath
import com.drivemount.app.engine.startPathFor
import com.drivemount.app.ui.browser.Breadcrumbs
import com.drivemount.app.ui.browser.FileRow
import com.drivemount.app.ui.components.Icon
import com.drivemount.app.ui.components.ProviderIcon
import com.drivemount.app.ui.components.providerLabel

/**
 * Cloud ট্যাবের মতো ব্রাউজ-ইন্টারফেসের পূর্ণ-পর্দা বাছাই (ফোল্ডারের সঙ্গে ফাইলও দেখা যায়)।
 *  - [multi] = true: দীর্ঘ চাপে একাধিক ফোল্ডার/ফাইল বাছা যায়; নিচে "Add N selected"
 *  - [subOption] = true: "Also sub-folders" টিক (ডিফল্ট চালু) — বন্ধ থাকলে শুধু ওই ফোল্ডারের সরাসরি ফাইল
 *  - [multi] = false (Sync): ফাইল দেখা যায় কিন্তু বাছা যায় না; শুধু বর্তমান ফোল্ডার বাছা হয়
 */
@Composable
fun CloudPickerDialog(
    accounts: List<AccountEntity>?,
    multi: Boolean,
    subOption: Boolean,
    onPick: (AccountEntity, List<RemoteEntry>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DriveMountApp
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var path by remember { mutableStateOf("") }
    var includeSub by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<String>() }

    val accountList by produceState<List<AccountEntity>?>(accounts) { if (accounts == null) value = app.accountRepository.getAll() }

    val listing by produceState<Result<List<RemoteEntry>>?>(null, account, path) {
        val acc = account
        value = null
        if (acc == null) return@produceState
        val quick = app.fileRepository.cached(acc, path) ?: app.fileRepository.persisted(acc, path)
        if (quick != null) value = Result.success(quick)
        val fresh = app.fileRepository.list(acc, path)
        if (fresh.isSuccess || quick == null) value = fresh
    }

    fun goBack() {
        val acc = account
        when {
            selected.isNotEmpty() -> selected.clear()
            acc == null -> onDismiss()
            path != startPathFor(acc.backendType) && path.isNotEmpty() -> { path = parentPath(path); selected.clear() }
            else -> { account = null; selected.clear() }
        }
    }

    Dialog(
        onDismissRequest = { goBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { goBack() }) {
                        Icon(Lucide.LuArrowLeft, contentDescription = stringResource(R.string.back))
                    }
                    Text(
                        text = account?.displayName ?: stringResource(R.string.videos_pick_account),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Lucide.LuX, contentDescription = stringResource(R.string.close))
                    }
                }
                val acc = account
                if (acc != null) {
                    Breadcrumbs(
                        rootLabel = acc.displayName,
                        root = "",
                        path = path,
                        onNavigate = { path = it; selected.clear() },
                    )
                }
                HorizontalDivider()

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (acc == null) {
                        val list = accountList
                        if (list == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(list, key = { i, e -> "${e.id}#$i" }) { _, a ->
                                    ListItem(
                                        headlineContent = { Text(a.displayName) },
                                        leadingContent = { ProviderIcon(a.backendType, modifier = Modifier.size(28.dp)) },
                                        supportingContent = { Text(providerLabel(a.backendType)) },
                                        modifier = Modifier.clickable {
                                            account = a
                                            path = startPathFor(a.backendType)
                                            selected.clear()
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        val res = listing
                        when {
                            res == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            res.isFailure -> Text(
                                text = res.exceptionOrNull()?.message ?: "Error",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                            else -> {
                                val entries = res.getOrNull().orEmpty()
                                    .sortedWith(compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() })
                                if (entries.isEmpty()) {
                                    Text(
                                        text = "Empty folder",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        itemsIndexed(entries, key = { i, e -> "${e.path}#$i" }) { _, e ->
                                            val on = e.path in selected
                                            fun toggle() { if (on) selected.remove(e.path) else selected.add(e.path) }
                                            Box(Modifier.alpha(if (!multi && !e.isDir) 0.45f else 1f)) {
                                                FileRow(
                                                    entry = e,
                                                    account = acc,
                                                    showThumbs = true,
                                                    selected = on,
                                                    onClick = {
                                                        when {
                                                            !multi -> if (e.isDir) path = e.path
                                                            selected.isNotEmpty() -> toggle()
                                                            e.isDir -> { path = e.path; selected.clear() }
                                                            else -> toggle()
                                                        }
                                                    },
                                                    onLongClick = { if (multi) toggle() },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (acc != null) {
                    HorizontalDivider()
                    // ফোনের পুরো স্টোরেজ বাছা যায় না (স্ক্যান অসম্ভব ধীর): ভেতরের ফোল্ডার খুলতে বলা হয়
                    val broad = selected.isEmpty() && acc.backendType == "local" && path.trim('/').let {
                        val root = LocalPaths.internalRelative()
                        it.isEmpty() || it == root || root.startsWith("$it/")
                    }
                    if (broad) {
                        Text(
                            text = stringResource(R.string.videos_select_folder_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (subOption) {
                            Column(
                                modifier = Modifier.clickable { includeSub = !includeSub },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Checkbox(checked = includeSub, onCheckedChange = { includeSub = it })
                                Text(
                                    "also sub-folders",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val chosen = if (selected.isEmpty()) listOf(
                                    RemoteEntry(path.substringAfterLast('/').ifEmpty { acc.displayName }, path, true, null, null, ""),
                                ) else (listing?.getOrNull().orEmpty()).filter { it.path in selected }
                                onPick(acc, chosen, includeSub)
                            },
                            enabled = !broad,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (selected.isEmpty()) stringResource(R.string.videos_select_folder)
                                else "Add ${selected.size} selected",
                            )
                        }
                    }
                }
            }
        }
    }
}

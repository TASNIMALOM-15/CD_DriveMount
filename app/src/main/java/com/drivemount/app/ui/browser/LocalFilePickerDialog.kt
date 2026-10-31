package com.drivemount.app.ui.browser

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemount.app.R
import com.drivemount.app.ui.components.fileIcon
import com.drivemount.app.ui.components.formatBytes
import com.drivemount.app.ui.theme.spacing
import java.io.File

/**
 * ফোনের ফাইল বাছাইয়ের নিজস্ব ডায়ালগ। সিস্টেমের file picker না থাকলে (যেমন কিছু কাজের প্রোফাইলে)
 * এটি ব্যবহার হয়। বাছাই করা ফাইল সরাসরি পড়ে আপলোড হয়, অস্থায়ী কপি লাগে না।
 */
@Composable
fun LocalFilePickerDialog(onDismiss: () -> Unit, onPicked: (List<File>) -> Unit) {
    val sp = MaterialTheme.spacing
    var dir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val children: List<File>? = remember(dir) {
        dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(sp.md)) {
                Text(
                    text = stringResource(R.string.upload_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { dir.parentFile?.let { dir = it } },
                        enabled = dir.parentFile != null,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(
                        text = dir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        children == null -> CenterText(stringResource(R.string.upload_picker_unreadable))
                        children.isEmpty() -> CenterText(stringResource(R.string.upload_picker_empty))
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(children, key = { it.absolutePath }) { file ->
                                val isSelected = file.absolutePath in selected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (file.isDirectory) {
                                                dir = file
                                            } else {
                                                selected = if (isSelected) selected - file.absolutePath else selected + file.absolutePath
                                            }
                                        }
                                        .padding(horizontal = sp.xs, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(sp.md),
                                ) {
                                    Icon(
                                        imageVector = fileIcon(file.isDirectory, file.name, ""),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!file.isDirectory) {
                                            Text(
                                                text = formatBytes(file.length()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (!file.isDirectory) {
                                        Checkbox(checked = isSelected, onCheckedChange = null)
                                    }
                                }
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
                        enabled = selected.isNotEmpty(),
                        onClick = { onPicked(selected.map { File(it) }) },
                    ) { Text(stringResource(R.string.upload_picker_upload, selected.size)) }
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

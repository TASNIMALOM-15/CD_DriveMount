package com.drivemount.app.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drivemount.app.R
import com.drivemount.app.ui.components.AppIcons
import com.drivemount.app.ui.components.FabIcon
import com.drivemount.app.ui.theme.spacing

private val presetColors = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B, 0xFF1E88E5,
    0xFF8E24AA, 0xFFD81B60, 0xFFFFFFFF, 0xFF757575, 0xFF000000,
).map { Color(it) }

/**
 * যেকোনো আইকনের জন্য: তালিকা থেকে অন্য আইকন বাছা, ডিভাইস থেকে ছবি/SVG import, আর রং বদলানো।
 * [image] = এখনকার বদলানো আইকন (কী বা "file:..."), null মানে ডিফল্ট; [color] null মানে ডিফল্ট রং।
 */
@Composable
fun IconEditorDialog(
    title: String,
    vector: ImageVector,
    image: String?,
    color: Int?,
    onPick: (String?) -> Unit,
    onImport: (Uri) -> Boolean,
    onColor: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val sp = MaterialTheme.spacing
    var customColor by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !onImport(uri)) {
            Toast.makeText(ctx, R.string.fab_import_failed, Toast.LENGTH_LONG).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                    FabIcon(image, vector, null, Modifier.size(48.dp), color = color)
                    Column {
                        TextButton(onClick = { launcher.launch("image/*") }) { Text(stringResource(R.string.fab_icon_import)) }
                        TextButton(onClick = { onPick(null); onColor(null) }) { Text(stringResource(R.string.icon_reset)) }
                    }
                }

                Text(stringResource(R.string.icon_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onColor(null); customColor = false }) { Text(stringResource(R.string.icon_color_default)) }
                    presetColors.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    BorderStroke(if (color == c.toArgb()) 3.dp else 1.dp, if (color == c.toArgb()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                    CircleShape,
                                )
                                .clickable { onColor(c.toArgb()) },
                        )
                    }
                }
                TextButton(onClick = { customColor = !customColor }) { Text(stringResource(R.string.icon_color_custom)) }

                Text(stringResource(R.string.icon_choose), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.height(220.dp)) {
                    items(AppIcons.all) { (key, icon) ->
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (image == key) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable { onPick(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            // এখানে সরাসরি material3 Icon: ঘরে নিজের আসল আইকনটাই দেখাতে হবে (সাজ ছাড়া)
                            androidx.compose.material3.Icon(icon, contentDescription = AppIcons.label(key), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.icon_done)) } },
    )

    if (customColor) {
        AlertDialog(
            onDismissRequest = { customColor = false },
            text = {
                CustomColorPicker(
                    initialArgb = color ?: 0xFF6750A4.toInt(),
                    onColorChange = { onColor(it) },
                    modifier = Modifier.heightIn(max = 480.dp),
                )
            },
            confirmButton = { TextButton(onClick = { customColor = false }) { Text(stringResource(R.string.icon_done)) } },
        )
    }
}

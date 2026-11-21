package com.drivemount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.ui.components.AppIcons
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.Icon
import com.drivemount.app.ui.theme.spacing

/** অ্যাপের সব আইকনের তালিকা: যেকোনোটা চাপলে অন্য আইকন/import করা ছবি/রং দেওয়া যায় */
@Composable
fun IconStyleCard() {
    val settings = (LocalContext.current.applicationContext as DriveMountApp).iconSettings
    val styles by settings.styles.collectAsState()
    val sp = MaterialTheme.spacing
    var open by remember { mutableStateOf(false) }

    DmCard {
        Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            Text(stringResource(R.string.icons_help), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { open = true }) { Text(stringResource(R.string.icons_customize)) }
                if (styles.isNotEmpty()) {
                    TextButton(onClick = { settings.resetAll() }) { Text(stringResource(R.string.icons_reset_all)) }
                }
            }
        }
    }

    if (open) {
        var query by remember { mutableStateOf("") }
        var editing by remember { mutableStateOf<Pair<String, ImageVector>?>(null) }
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.systemBarsPadding().padding(horizontal = sp.md)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.icons_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { open = false }) { Text(stringResource(R.string.icon_done)) }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.icons_search)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val list = remember(query) {
                        AppIcons.all.filter { query.isBlank() || AppIcons.label(it.first).contains(query.trim(), ignoreCase = true) }
                    }
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(list, key = { it.first }) { (key, vector) ->
                            val st = styles[key]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = key to vector }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(sp.md),
                            ) {
                                // নিজস্ব Icon: বর্তমান সাজ সমেত দেখায়
                                Icon(vector, contentDescription = null, modifier = Modifier.size(28.dp))
                                Text(AppIcons.label(key), modifier = Modifier.weight(1f))
                                if (st?.color != null) {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(st.color)))
                                }
                                if (st?.image != null) {
                                    Text("•", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
        editing?.let { (key, vector) ->
            IconEditorDialog(
                title = AppIcons.label(key),
                vector = vector,
                image = styles[key]?.image,
                color = styles[key]?.color,
                onPick = { settings.setImage(key, it) },
                onImport = { settings.importImage(key, it) },
                onColor = { settings.setColor(key, it) },
                onDismiss = { editing = null },
            )
        }
    }
}

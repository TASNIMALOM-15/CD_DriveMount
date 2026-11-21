package com.drivemount.app.ui.browser

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** ব্রাউজারের উপরের শর্টকাট-ট্যাব। [goto] = true হলে "Go to path" ডায়ালগ খোলে। */
data class Shortcut(val label: String, val path: String, val goto: Boolean = false)

/** প্রতিটি ক্লাউডের শর্টকাট-তালিকা (ক্রম সহ) SharedPreferences-এ রাখে; কিছু না বদলালে null (মানে ডিফল্ট)। */
object ShortcutStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("browser_shortcuts", Context.MODE_PRIVATE)

    fun load(ctx: Context, accountId: Long): List<Shortcut>? {
        val raw = prefs(ctx).getString("acc_$accountId", null) ?: return null
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Shortcut(o.getString("l"), o.optString("p"), o.optBoolean("g"))
            }
        }.getOrNull()
    }

    fun save(ctx: Context, accountId: Long, list: List<Shortcut>?) {
        val e = prefs(ctx).edit()
        if (list == null) e.remove("acc_$accountId") else {
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("l", it.label).put("p", it.path).put("g", it.goto)) }
            e.putString("acc_$accountId", a.toString())
        }
        e.apply()
    }
}

/** শর্টকাট সারি: ট্যাপে সেই ফোল্ডারে যায়; চেপে ধরলে মেনু (সরাও, নাম বদল, মোছা)। নতুন ট্যাব: ফোল্ডার সিলেক্ট → ⋮ → Pin as Tab। */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutRow(
    items: List<Shortcut>,
    isLocal: Boolean,
    onGo: (String) -> Unit,
    onGoToPath: () -> Unit,
    onChange: (List<Shortcut>) -> Unit,
    onReset: () -> Unit,
) {
    var renameIdx by remember { mutableStateOf(-1) }
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, sc ->
            var menu by remember { mutableStateOf(false) }
            Box {
                // আগের মতোই AssistChip; চেপে ধরা ধরতে উপরে অদৃশ্য স্তর
                AssistChip(onClick = {}, label = { Text(sc.label) })
                Box(
                    Modifier.matchParentSize().combinedClickable(
                        onClick = { if (sc.goto) onGoToPath() else onGo(sc.path) },
                        onLongClick = { menu = true },
                    ),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (i > 0) DropdownMenuItem(text = { Text("Move left") }, onClick = {
                        menu = false
                        onChange(items.toMutableList().also { val x = it.removeAt(i); it.add(i - 1, x) })
                    })
                    if (i < items.lastIndex) DropdownMenuItem(text = { Text("Move right") }, onClick = {
                        menu = false
                        onChange(items.toMutableList().also { val x = it.removeAt(i); it.add(i + 1, x) })
                    })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; text = sc.label; renameIdx = i })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = {
                        menu = false
                        onChange(items.toMutableList().also { it.removeAt(i) })
                    })
                    DropdownMenuItem(text = { Text("Reset to default") }, onClick = { menu = false; onReset() })
                }
            }
        }
    }
    if (renameIdx >= 0) {
        AlertDialog(
            onDismissRequest = { renameIdx = -1 },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    onChange(items.toMutableList().also { it[renameIdx] = it[renameIdx].copy(label = text.trim()) })
                    renameIdx = -1
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renameIdx = -1 }) { Text("Cancel") } },
        )
    }
}

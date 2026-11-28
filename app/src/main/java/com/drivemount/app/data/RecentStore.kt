package com.drivemount.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Home-এর "Recent": সম্প্রতি চালানো / নামানো / আপলোড / অফলাইনে রাখা ফাইল।
 * ছোট (সর্বোচ্চ ২০টি), SharedPreferences-এ JSON; লেখা সামান্য, তাই ল্যাগ নেই। একই ফাইল আবার হলে ওপরে উঠে আসে।
 */
class RecentStore(context: Context) {

    enum class Kind { PLAYED, DOWNLOADED, UPLOADED, OFFLINE }

    data class Item(val kind: Kind, val accountId: Long, val path: String, val name: String, val at: Long)

    private val prefs = context.getSharedPreferences("recent_ui", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items

    private fun load(): List<Item> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val kind = runCatching { Kind.valueOf(o.getString("k")) }.getOrNull() ?: return@mapNotNull null
            Item(kind, o.getLong("a"), o.getString("p"), o.getString("n"), o.getLong("t"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(kind: Kind, accountId: Long, path: String, name: String = path.substringAfterLast('/')) {
        if (accountId < 0 || path.isBlank()) return
        val next = (listOf(Item(kind, accountId, path, name, System.currentTimeMillis())) +
            _items.value.filterNot { it.accountId == accountId && it.path == path })
            .take(MAX)
        _items.value = next
        val arr = JSONArray()
        next.forEach {
            arr.put(JSONObject().put("k", it.kind.name).put("a", it.accountId).put("p", it.path).put("n", it.name).put("t", it.at))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        prefs.edit().remove("list").apply()
    }

    private companion object { const val MAX = 20 }
}

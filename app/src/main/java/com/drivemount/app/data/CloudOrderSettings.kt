package com.drivemount.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ক্লাউডগুলো সবসময় প্ল্যাটফর্মের ধরন (type) অনুযায়ী সাজানো থাকে: যেমন local, google drive, mega...
 * এই সিরিয়াল Settings থেকে বদলানো যায়। একই ধরনের ক্লাউডের নিজেদের মধ্যে আগের ক্রম থাকে।
 */
class CloudOrderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud_order_prefs", Context.MODE_PRIVATE)

    private val _order = MutableStateFlow(
        prefs.getString(KEY, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
    )
    /** ব্যবহারকারীর সেভ করা সিরিয়াল (খালি = ডিফল্ট) */
    val order: StateFlow<List<String>> = _order

    /** সেভ করা + ডিফল্ট + [extra] (এখন আছে এমন অন্য ধরন) মিলিয়ে পূর্ণ সিরিয়াল */
    fun effective(extra: Collection<String> = emptyList()): List<String> {
        val saved = _order.value
        return (saved + DEFAULT.filter { it !in saved } + extra.filter { it !in saved && it !in DEFAULT }.sorted()).distinct()
    }

    fun move(type: String, delta: Int, extra: Collection<String> = emptyList()) {
        val list = effective(extra).toMutableList()
        val i = list.indexOf(type)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        save(list)
    }

    fun reset() = save(emptyList())

    private fun save(list: List<String>) {
        _order.value = list
        prefs.edit().putString(KEY, list.joinToString(",")).apply()
    }

    /** [items]-কে ধরন অনুযায়ী সাজায় (stable: একই ধরনের আইটেমের ক্রম বদলায় না) */
    fun <T> sort(items: List<T>, typeOf: (T) -> String): List<T> {
        val order = effective(items.map(typeOf))
        return items.sortedBy { order.indexOf(typeOf(it)).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    companion object {
        private const val KEY = "order"
        val DEFAULT = listOf(
            "local", "drive", "mega", "onedrive", "dropbox", "pcloud", "protondrive", "filen",
            "terabox", "teraplay", "gphotos", "gcs", "sia", "gofile", "ftp", "webdav", "union",
        )
    }
}

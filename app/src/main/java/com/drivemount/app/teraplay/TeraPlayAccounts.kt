package com.drivemount.app.teraplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** TeraPlay প্লাগইনের একটি Terabox অ্যাকাউন্ট (DriveMount-এর সাধারণ Terabox থেকে আলাদা) */
data class TpAccount(
    val id: String,
    val name: String,
    val cookie: String,
    val total: Long = 0,
    val used: Long = 0,
    val checked: Long = 0,
) {
    val free: Long get() = (total - used).coerceAtLeast(0)
}

/** সংরক্ষিত অ্যাকাউন্ট (ndus কুকি + সর্বশেষ জানা স্টোরেজ): filesDir/teraplay/accounts.json */
class TpAccountStore(ctx: Context) {
    private val file = File(File(ctx.filesDir, "teraplay").also { it.mkdirs() }, "accounts.json")

    companion object {
        private val lock = Any()
    }

    fun load(): List<TpAccount> = synchronized(lock) { loadUnlocked() }

    private fun loadUnlocked(): List<TpAccount> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TpAccount(
                    id = o.getString("id"),
                    name = o.optString("name", "Account"),
                    cookie = o.getString("cookie"),
                    total = o.optLong("total", 0),
                    used = o.optLong("used", 0),
                    checked = o.optLong("checked", 0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveUnlocked(list: List<TpAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("cookie", it.cookie)
                    .put("total", it.total).put("used", it.used).put("checked", it.checked),
            )
        }
        val tmp = File(file.parentFile, "accounts.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    fun add(name: String, cookie: String, total: Long = 0, used: Long = 0, id: String? = null): TpAccount = synchronized(lock) {
        val list = loadUnlocked().toMutableList()
        val a = TpAccount(
            id = id ?: System.currentTimeMillis().toString(36),
            name = name,
            cookie = cookie,
            total = total,
            used = used,
            checked = if (total > 0) System.currentTimeMillis() else 0,
        )
        list.removeAll { it.id == a.id }
        list.add(a)
        saveUnlocked(list)
        a
    }

    fun update(a: TpAccount) = synchronized(lock) {
        saveUnlocked(loadUnlocked().map { if (it.id == a.id) a else it })
    }

    fun remove(id: String) = synchronized(lock) {
        saveUnlocked(loadUnlocked().filter { it.id != id })
    }
}

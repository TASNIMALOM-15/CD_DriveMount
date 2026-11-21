package com.drivemount.app.teraplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * "Terabox (TeraPlay)" অ্যাকাউন্টের থাম্বনেল: TeraPlay-র মতো, ফোল্ডারের তালিকার সঙ্গেই Terabox সার্ভার
 * প্রতিটি ফাইলের থাম্বনেল-লিংক দেয়। তাই একটি ফোল্ডারের শত শত ভিডিওর জন্য মাত্র একবার তালিকা আনলেই হয়,
 * ভিডিও নামানো লাগে না।
 */
object TeraThumbs {
    private class Dir(val at: Long, val thumbs: Map<String, String>?)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val clients = ConcurrentHashMap<String, TeraBoxClient>()
    private val accountLocks = ConcurrentHashMap<String, Mutex>()
    private val dirs = ConcurrentHashMap<String, Dir>()
    private val dirLocks = ConcurrentHashMap<String, Mutex>()

    private const val FAIL_RETRY_MS = 20_000L
    private const val DIR_TTL_MS = 10 * 60_000L

    /** এই অ্যাকাউন্ট TeraPlay দিয়ে যোগ করা হলে তার কুকি */
    private fun cookieOf(context: Context, account: AccountEntity): String? =
        TpAccountStore(context).load().firstOrNull { it.id == account.id.toString() }?.cookie

    /** থাম্বনেল না থাকলে বা TeraPlay অ্যাকাউন্ট না হলে null (তখন অ্যাপ আগের পদ্ধতিতে বানায়) */
    suspend fun fetch(context: Context, account: AccountEntity, entry: RemoteEntry): Bitmap? {
        val cookie = cookieOf(context, account) ?: return null
        val clean = entry.path.trim('/')
        val dir = "/" + clean.substringBeforeLast('/', "")
        val name = clean.substringAfterLast('/')
        val thumbs = thumbsOf(account.id.toString(), cookie, dir.trimEnd('/').ifEmpty { "/" }) ?: return null
        val url = thumbs[name]?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", TeraBoxClient.UA).build()).execute().use {
                if (!it.isSuccessful) return@use null
                val bytes = it.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private suspend fun thumbsOf(accountId: String, cookie: String, dir: String): Map<String, String>? {
        val key = "$accountId|$dir"
        val now = System.currentTimeMillis()
        dirs[key]?.let { d ->
            val ttl = if (d.thumbs == null) FAIL_RETRY_MS else DIR_TTL_MS
            if (now - d.at < ttl) return d.thumbs
        }
        return dirLocks.getOrPut(key) { Mutex() }.withLock {
            dirs[key]?.let { d ->
                val ttl = if (d.thumbs == null) FAIL_RETRY_MS else DIR_TTL_MS
                if (System.currentTimeMillis() - d.at < ttl) return@withLock d.thumbs
            }
            val map = runCatching {
                accountLocks.getOrPut(accountId) { Mutex() }.withLock {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val c = clients.getOrPut(accountId) { TeraBoxClient(cookie) { }.also { it.prepare() } }
                        c.listRemote(dir).filter { !it.isDir && it.thumb.isNotEmpty() }.associate { it.name to it.thumb }
                    }
                }
            }.onFailure { clients.remove(accountId) }.getOrNull()
            dirs[key] = Dir(System.currentTimeMillis(), map)
            map
        }
    }
}

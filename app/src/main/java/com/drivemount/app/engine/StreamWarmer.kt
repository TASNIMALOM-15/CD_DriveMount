package com.drivemount.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * চালানোর আগেই ফাইলের শুরু ও শেষের ছোট অংশ আগেভাগে লোকাল স্ট্রিম-সার্ভারের ক্যাশে আনা।
 * প্লেয়ার একই ফাইল চাইলে (আলাদা URL/token হলেও) Go-র ফাইল-ভিত্তিক ক্যাশ থেকে সঙ্গে সঙ্গে পায়।
 *  - শুরু: ভিডিও/গান শুরুর ডেটা  - শেষ: MP4/M4A-র `moov` তথ্য শেষে থাকলে সিকে আটকায় না
 * কম খরচের নিয়ম: মিটারড (মোবাইল ডেটা) নেটওয়ার্কে বা ব্যাটারি-সেভারে চলে না; একসঙ্গে ২টির বেশি নয়;
 * একই ফাইলের জন্য সেশনে একবারই।
 */
object StreamWarmer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val done: MutableSet<String> = Collections.synchronizedSet(
        Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 300
        }),
    )

    private const val VIDEO_HEAD = 3 * 1024 * 1024
    private const val VIDEO_TAIL = 768 * 1024
    private const val AUDIO_HEAD = 1024 * 1024
    private const val AUDIO_TAIL = 256 * 1024

    private fun allowed(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (cm.isActiveNetworkMetered) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isPowerSaveMode
    }

    /** ফাইলের স্ট্রিম URL আগেই জানা থাকলে (যেমন প্লেয়ারের কিউয়ের পরের আইটেম) */
    fun warmUrl(context: Context, key: String, url: String, audio: Boolean) {
        if (!allowed(context) || !done.add(key)) return
        val bgUrl = if (url.contains("?")) url else "$url?bg=1"
        scope.launch { gate.withPermit { run(bgUrl, audio) } }
    }

    /** URL এখনো নেই: বানিয়ে নিয়ে আগেভাগে আনা (ভিডিও-তালিকায় যে কার্ডগুলো দেখা যাচ্ছে) */
    fun warmFile(context: Context, api: RcloneApi, fs: String, path: String, size: Long, audio: Boolean) {
        val key = "$fs|$path|$size"
        if (!allowed(context) || !done.add(key)) return
        scope.launch {
            gate.withPermit {
                val url = runCatching { api.streamUrl(fs, path, guessMime(path.substringAfterLast('/')), background = true) }.getOrNull()
                    ?: return@withPermit
                run(url, audio)
            }
        }
    }

    private fun run(url: String, audio: Boolean) {
        val head = if (audio) AUDIO_HEAD else VIDEO_HEAD
        val tail = if (audio) AUDIO_TAIL else VIDEO_TAIL
        runCatching { fetch(url, "bytes=0-${head - 1}", head) }
        runCatching { fetch(url, "bytes=-$tail", tail) }
    }

    private fun fetch(url: String, range: String, limit: Int) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 3_000
            c.readTimeout = 20_000
            c.setRequestProperty("Range", range)
            if (c.responseCode !in 200..299) return
            c.inputStream.use { ins ->
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (total < limit) {
                    val n = ins.read(buf, 0, minOf(buf.size, limit - total))
                    if (n < 0) break
                    total += n
                }
            }
        } finally {
            c.disconnect()
        }
    }
}

package com.drivemount.app.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** যেসব ক্লাউড নিজের সার্ভারে তৈরি থাম্বনেল দেয় (ভিডিও/ছবি নামানো ছাড়াই) */
fun hasServerThumbs(type: String): Boolean = type == "mega" || type == "drive" || type == "pcloud" || type == "terabox"

/**
 * ক্লাউড-সার্ভারের তৈরি ছোট থাম্বনেল আনে। না পেলে null; তখন অ্যাপ আগের পদ্ধতিতে
 * (ফাইল থেকে ফ্রেম) থাম্বনেল বানায়।
 */
object ServerThumbs {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gate = Semaphore(8)
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun thumbnail(context: Context, account: AccountEntity, entry: RemoteEntry, key: String): Bitmap? =
        when (account.backendType) {
            "mega" -> MegaThumbs.thumbnail(context, account, entry, key)
            "drive", "pcloud" -> cached(context, key) { fetch(context, account, entry) }
            "terabox" -> cached(context, key) { com.drivemount.app.teraplay.TeraThumbs.fetch(context, account, entry) }
            else -> null
        }

    private suspend fun cached(context: Context, key: String, load: suspend () -> Bitmap?): Bitmap? {
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val hash = MessageDigest.getInstance("SHA-1").digest(("srv|$key").toByteArray())
                .joinToString("") { "%02x".format(it) }
            val disk = File(File(context.filesDir, "thumbs_srv").apply { mkdirs() }, "$hash.jpg")
            if (disk.exists()) {
                BitmapFactory.decodeFile(disk.path)?.let { memory.put(key, it); return@withContext it }
            }
            val bmp = runCatching { gate.withPermit { load() } }.getOrNull()
            if (bmp != null) {
                memory.put(key, bmp)
                runCatching { FileOutputStream(disk).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            }
            bmp
        }
    }

    private suspend fun fetch(context: Context, account: AccountEntity, entry: RemoteEntry): Bitmap? {
        val app = context.applicationContext as DriveMountApp
        return when (account.backendType) {
            "drive" -> driveThumb(app, account, entry)
            "pcloud" -> pcloudThumb(app, account, entry)
            else -> null
        }
    }

    private fun download(url: String, bearer: String? = null): Bitmap? {
        val b = Request.Builder().url(url)
        if (bearer != null) b.header("Authorization", "Bearer $bearer")
        return http.newCall(b.build()).execute().use {
            if (!it.isSuccessful) return@use null
            val bytes = it.body?.bytes() ?: return@use null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun getJson(url: String, bearer: String? = null): JSONObject? {
        val b = Request.Builder().url(url)
        if (bearer != null) b.header("Authorization", "Bearer $bearer")
        return http.newCall(b.build()).execute().use {
            if (it.code == 401) throw UnauthorizedException()
            if (!it.isSuccessful) return@use null
            JSONObject(it.body?.string().orEmpty())
        }
    }

    private class UnauthorizedException : Exception()

    private suspend fun accessToken(app: DriveMountApp, account: AccountEntity, conf: JSONObject? = null): String? {
        val c = conf ?: app.rcloneApi.dumpConfig()[account.remoteName] ?: return null
        return runCatching { JSONObject(c.optString("token")).optString("access_token") }.getOrNull()?.ifEmpty { null }
    }

    /** টোকেন মেয়াদোত্তীর্ণ হলে rclone-কে একটি হালকা কাজ দিয়ে নতুন টোকেন আনানো */
    private suspend fun refreshToken(app: DriveMountApp, account: AccountEntity) {
        runCatching { app.rcloneApi.about(account.remoteName) }
    }

    private suspend fun <T> withToken(app: DriveMountApp, account: AccountEntity, block: (String) -> T?): T? {
        var token = accessToken(app, account) ?: return null
        return try {
            block(token)
        } catch (e: UnauthorizedException) {
            refreshToken(app, account)
            token = accessToken(app, account) ?: return null
            runCatching { block(token) }.getOrNull()
        }
    }

    // ------------------------------------------------------------------ Google Drive

    private class DriveFile(val modMs: Long, val link: String)

    private val driveIndex = ConcurrentHashMap<String, Map<String, List<DriveFile>>>()
    private val driveLocks = ConcurrentHashMap<String, Mutex>()
    private val driveFailed = ConcurrentHashMap<String, Long>()

    private suspend fun driveThumb(app: DriveMountApp, account: AccountEntity, entry: RemoteEntry): Bitmap? {
        val name = account.remoteName
        var index = driveIndex[name]
        if (index == null) {
            val failed = driveFailed[name]
            if (failed != null && System.currentTimeMillis() - failed < 10 * 60_000L) return null
            val lock = driveLocks.getOrPut(name) { Mutex() }
            index = lock.withLock {
                driveIndex[name] ?: run {
                    val built = withToken(app, account) { token -> buildDriveIndex(token) }
                    if (built == null) driveFailed[name] = System.currentTimeMillis() else driveIndex[name] = built
                    built
                }
            }
        }
        val candidates = index?.get("${entry.name}|${entry.size ?: -1L}") ?: return null
        val file = candidates.minByOrNull { kotlin.math.abs(it.modMs - (entry.modTimeMillis ?: it.modMs)) } ?: return null
        val link = file.link.replace(Regex("=s\\d+$"), "=s480")
        return withContext(Dispatchers.IO) { download(link) }
    }

    private fun buildDriveIndex(token: String): Map<String, List<DriveFile>> {
        val out = HashMap<String, MutableList<DriveFile>>()
        var page: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "(mimeType contains 'video/' or mimeType contains 'image/') and trashed=false")
                .addQueryParameter("fields", "nextPageToken,files(name,size,modifiedTime,thumbnailLink)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("spaces", "drive")
                .apply { if (page != null) addQueryParameter("pageToken", page) }
                .build().toString()
            val j = getJson(url, token) ?: error("drive list failed")
            val arr = j.optJSONArray("files")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val link = o.optString("thumbnailLink")
                    if (link.isEmpty()) continue
                    val mod = runCatching { Instant.parse(o.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0L)
                    val size = o.optString("size").toLongOrNull() ?: -1L
                    out.getOrPut("${o.optString("name")}|$size") { ArrayList() } += DriveFile(mod, link)
                }
            }
            page = j.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    // ------------------------------------------------------------------ pCloud

    private val pcloudDirs = ConcurrentHashMap<String, Map<String, Long>>()

    private suspend fun pcloudThumb(app: DriveMountApp, account: AccountEntity, entry: RemoteEntry): Bitmap? {
        val conf = app.rcloneApi.dumpConfig()[account.remoteName] ?: return null
        val host = conf.optString("hostname").ifEmpty { "api.pcloud.com" }
        val dir = entry.path.trim('/').substringBeforeLast('/', "")
        val dirKey = "${account.remoteName}|$dir"
        return withToken(app, account) { token ->
            var files = pcloudDirs[dirKey]
            if (files == null) {
                val url = "https://$host/listfolder".toHttpUrl().newBuilder()
                    .addQueryParameter("path", "/$dir")
                    .addQueryParameter("nobreadcrumbs", "1")
                    .build().toString()
                val j = getJson(url, token) ?: return@withToken null
                val contents = j.optJSONObject("metadata")?.optJSONArray("contents") ?: return@withToken null
                val m = HashMap<String, Long>()
                for (i in 0 until contents.length()) {
                    val o = contents.getJSONObject(i)
                    if (!o.optBoolean("isfolder") && o.optBoolean("thumb", true)) m[o.optString("name")] = o.optLong("fileid")
                }
                files = m
                pcloudDirs[dirKey] = m
            }
            val id = files[entry.name] ?: return@withToken null
            val tl = "https://$host/getthumblink".toHttpUrl().newBuilder()
                .addQueryParameter("fileid", id.toString())
                .addQueryParameter("size", "320x240")
                .addQueryParameter("type", "jpg")
                .build().toString()
            val r = getJson(tl, token) ?: return@withToken null
            val path = r.optString("path")
            val hosts = r.optJSONArray("hosts")
            if (path.isEmpty() || hosts == null || hosts.length() == 0) return@withToken null
            download("https://${hosts.getString(0)}$path")
        }
    }
}

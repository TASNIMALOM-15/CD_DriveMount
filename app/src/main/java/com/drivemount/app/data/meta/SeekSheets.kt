package com.drivemount.app.data.meta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ভিডিওর সিকবার-প্রিভিউ: প্রতি কয়েক সেকেন্ডের একটি ছোট ফ্রেম (১৬০×৯০) এক ছবিতে (sprite sheet, ≈৫০–১২০KB)।
 * আপলোডের সময় ফোনে বানানো হয়; ফোনের এনক্রিপ্টেড ক্যাশে থাকে এবং লুকানো `.dmmeta/vseek/`-এ ক্লাউডে যায়।
 * প্লেয়ারে সিকবার টানলে লক্ষ্য-সময়ের ফ্রেম দেখায়। ফাইল = ২৪ বাইটের হেডার + WebP ছবি।
 */
class SeekSheets(
    private val context: Context,
    private val api: RcloneApi,
    private val cache: VaultCache,
) {
    class Sheet(val durationMs: Long, val count: Int, val cols: Int, val tileW: Int, val tileH: Int, val bitmap: Bitmap) {
        /** [positionMs]-এর ফ্রেমটি শিটের কোথায়: (বাম, ওপর) পিক্সেল */
        fun tileOrigin(positionMs: Long): Pair<Int, Int> {
            val interval = (durationMs / count).coerceAtLeast(1L)
            val i = (positionMs / interval).toInt().coerceIn(0, count - 1)
            return (i % cols) * tileW to (i / cols) * tileH
        }
    }

    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val decoded = LruCache<String, Sheet>(2)

    private fun key(remote: String, path: String, size: Long) = "$remote|$path|$size"

    fun remotePath(path: String, size: Long): String {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val id = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
        return joinPath(joinPath(joinPath(dir, META_DIR), "vseek"), "$id.dms")
    }

    fun store(remote: String, path: String, size: Long, bytes: ByteArray) {
        cache.put(NS, key(remote, path, size), bytes)
    }

    fun has(remote: String, path: String, size: Long) = cache.has(NS, key(remote, path, size))

    fun removeLocal(remote: String, path: String, size: Long) {
        val k = key(remote, path, size)
        cache.remove(NS, k); cache.remove(NS_NONE, k)
        decoded.remove(k); missing.remove(k)
    }

    /** "এই ভিডিওর শিট লাগবে না" (ছোট ভিডিও): Backfill আবার চেষ্টা করে না */
    fun markNone(remote: String, path: String, size: Long) = cache.put(NS_NONE, key(remote, path, size), byteArrayOf(1))

    fun isNone(remote: String, path: String, size: Long) = cache.has(NS_NONE, key(remote, path, size))

    private fun parse(bytes: ByteArray): Sheet? = runCatching {
        if (bytes.size <= HEADER) return null
        val b = ByteBuffer.wrap(bytes)
        if (b.int != MAGIC) return null
        val dur = b.long
        val count = b.int
        val cols = b.int
        val tw = b.short.toInt()
        val th = b.short.toInt()
        val bmp = BitmapFactory.decodeByteArray(bytes, HEADER, bytes.size - HEADER) ?: return null
        if (count <= 0 || cols <= 0 || tw <= 0 || th <= 0) return null
        Sheet(dur, count, cols, tw, th, bmp)
    }.getOrNull()

    /** ফোনের ক্যাশ থেকে; না থাকলে ক্লাউডের ছোট ফাইলটি এনে। নেই/ব্যর্থ হলে null (এই সেশনে আর চেষ্টা নয়) */
    suspend fun load(remote: String, fs: String, path: String, size: Long): Sheet? = withContext(Dispatchers.IO) {
        val k = key(remote, path, size)
        decoded.get(k)?.let { return@withContext it }
        var bytes = cache.get(NS, k)
        if (bytes == null) {
            if (k in missing) return@withContext null
            val tmp = File(context.cacheDir, "seek_dl/${System.nanoTime()}").apply { mkdirs() }
            try {
                api.transferFile(fs, remotePath(path, size), tmp.absolutePath, "s.dms", false)
                bytes = File(tmp, "s.dms").readBytes().takeIf { it.isNotEmpty() }
                if (bytes != null) cache.put(NS, k, bytes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                bytes = null
            } finally {
                tmp.deleteRecursively()
            }
            if (bytes == null) { missing.add(k); return@withContext null }
        }
        parse(bytes)?.also { decoded.put(k, it) }
    }

    /** ফাইল কপি/সরানো/নাম বদলে: নতুন পথের জন্য বসানো ও ক্লাউডে তোলা */
    suspend fun migrate(srcRemote: String, dstRemote: String, dstFs: String, oldPath: String, newPath: String, size: Long) {
        val bytes = withContext(Dispatchers.IO) { cache.get(NS, key(srcRemote, oldPath, size)) } ?: return
        store(dstRemote, newPath, size, bytes)
        enqueueUpload(dstFs, newPath, size, bytes)
    }

    suspend fun deleteRemote(fs: String, path: String, size: Long) {
        runCatching { api.deleteFile(fs, remotePath(path, size)) }
    }

    // ---------------- আপলোড (ফোল্ডার-প্রিভিউয়ের মতোই: কাজের লাইন ফাঁকা হলে) ----------------

    private val queue = SidecarUploadQueue(context, api, "seek_up")

    fun enqueueUpload(fs: String, path: String, size: Long, bytes: ByteArray) =
        queue.enqueue(fs, remotePath(path, size), bytes)

    suspend fun flushUploads() = queue.flush()
    fun uploadStats(): Pair<Int, Int> = queue.waiting() to queue.failed()
    fun resetFailedUploads() = queue.resetFailed()

    companion object {
        private const val NS = "vseek"
        private const val NS_NONE = "vseek_none"
        const val HEADER = 24
        const val MAGIC = 0x444D5331 // "DMS1"

        /** শিট বানিয়ে ফাইল-ফরম্যাটে প্যাক করা (বানানো ThumbMetaReader.seekSheet-এ) */
        fun pack(durationMs: Long, count: Int, cols: Int, tileW: Int, tileH: Int, webp: ByteArray): ByteArray =
            ByteBuffer.allocate(HEADER + webp.size)
                .putInt(MAGIC).putLong(durationMs).putInt(count).putInt(cols)
                .putShort(tileW.toShort()).putShort(tileH.toShort())
                .put(webp).array()
    }
}

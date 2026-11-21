package com.drivemount.app.data.meta

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ভিডিওর টেকনিক্যাল মেটাডেটা সাইডকার (ছোট JSON, ≈২৫০ বাইট): Duration, Resolution, Video/Audio Codec,
 * FPS, Bitrate, HDR, Rotation। আপলোডের সময় ফোনে বের করা হয়; এনক্রিপ্টেড লোকাল ক্যাশে থাকে এবং
 * লুকানো `.dmmeta/vmeta/`-এ ক্লাউডে যায়, ফলে পরে ভিডিও না নামিয়েই তথ্য পাওয়া যায়।
 */
class VideoMetas(
    private val context: Context,
    private val api: RcloneApi,
    private val cache: VaultCache,
) {
    data class Info(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val videoCodec: String,
        val audioCodec: String,
        val fps: Float,
        val bitrate: Long,
        val hdr: String, // SDR, HDR10, HLG, DolbyVision
        val rotation: Int,
        /** ক্লাউডে (.dmmeta/fast) moov-সামনে-আনা কপি আছে/আসছে */
        val fast: Boolean = false,
    ) {
        fun toBytes(): ByteArray = JSONObject()
            .put("v", 1).put("dur", durationMs).put("w", width).put("h", height)
            .put("vc", videoCodec).put("ac", audioCodec).put("fps", fps.toDouble())
            .put("br", bitrate).put("hdr", hdr).put("rot", rotation).put("ff", fast)
            .toString().toByteArray()

        companion object {
            fun parse(b: ByteArray): Info? = runCatching {
                val j = JSONObject(String(b))
                Info(
                    j.optLong("dur"), j.optInt("w"), j.optInt("h"), j.optString("vc"), j.optString("ac"),
                    j.optDouble("fps", 0.0).toFloat(), j.optLong("br"), j.optString("hdr", "SDR"), j.optInt("rot"), j.optBoolean("ff", false),
                )
            }.getOrNull()
        }
    }

    private val missing = ConcurrentHashMap.newKeySet<String>()
    private fun key(remote: String, path: String, size: Long) = "$remote|$path|$size"

    fun remotePath(path: String, size: Long): String {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val id = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
        return joinPath(joinPath(joinPath(dir, META_DIR), "vmeta"), "$id.dmv")
    }

    fun store(remote: String, path: String, size: Long, bytes: ByteArray) = cache.put(NS, key(remote, path, size), bytes)

    fun has(remote: String, path: String, size: Long) = cache.has(NS, key(remote, path, size))

    fun removeLocal(remote: String, path: String, size: Long) {
        cache.remove(NS, key(remote, path, size))
        missing.remove(key(remote, path, size))
    }

    /** লোকাল ক্যাশ থেকে; না থাকলে ক্লাউডের ছোট ফাইলটি এনে। নেই হলে null */
    suspend fun load(remote: String, fs: String, path: String, size: Long): Info? = withContext(Dispatchers.IO) {
        val k = key(remote, path, size)
        var bytes = cache.get(NS, k)
        if (bytes == null) {
            if (k in missing) return@withContext null
            val tmp = File(context.cacheDir, "vmeta_dl/${System.nanoTime()}").apply { mkdirs() }
            try {
                api.transferFile(fs, remotePath(path, size), tmp.absolutePath, "m.dmv", false)
                bytes = File(tmp, "m.dmv").readBytes().takeIf { it.isNotEmpty() }
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
        Info.parse(bytes)
    }

    suspend fun migrate(srcRemote: String, dstRemote: String, dstFs: String, oldPath: String, newPath: String, size: Long) {
        val bytes = withContext(Dispatchers.IO) { cache.get(NS, key(srcRemote, oldPath, size)) } ?: return
        store(dstRemote, newPath, size, bytes)
        enqueueUpload(dstFs, newPath, size, bytes)
    }

    suspend fun deleteRemote(fs: String, path: String, size: Long) {
        runCatching { api.deleteFile(fs, remotePath(path, size)) }
    }

    // ---------------- আপলোড (কাজের লাইন ফাঁকা হলে) ----------------

    private val queue = SidecarUploadQueue(context, api, "vmeta_up")
    private val fastQueue = SidecarUploadQueue(context, api, "fast_up")

    /** fast-start কপির ক্লাউড-পথ: `.dmmeta/fast/<id>.<ext>` (মূল ফাইলের পাশে নয়, তাই মূল ফাইল অপরিবর্তিত) */
    fun fastRemotePath(path: String, size: Long): String {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val id = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
        val ext = name.substringAfterLast('.', "mp4").lowercase()
        return joinPath(joinPath(joinPath(dir, META_DIR), "fast"), "$id.$ext")
    }

    fun enqueueFast(fs: String, path: String, size: Long, file: File) = fastQueue.enqueueFile(fs, fastRemotePath(path, size), file)

    suspend fun deleteFast(fs: String, path: String, size: Long) {
        runCatching { api.deleteFile(fs, fastRemotePath(path, size)) }
    }

    /** ফাইল নাম বদল/সরানো (একই ক্লাউডে): fast-start কপিও নতুন পথে সরানো, সরাসরি ক্লাউডের ভেতরে */
    suspend fun moveFast(fs: String, oldPath: String, newPath: String, size: Long) {
        runCatching { api.transferFile(fs, fastRemotePath(oldPath, size), fs, fastRemotePath(newPath, size), true) }
    }

    fun markFast(bytes: ByteArray): ByteArray = Info.parse(bytes)?.copy(fast = true)?.toBytes() ?: bytes

    fun enqueueUpload(fs: String, path: String, size: Long, bytes: ByteArray) =
        queue.enqueue(fs, remotePath(path, size), bytes)

    suspend fun flushUploads() { queue.flush(); fastQueue.flush() }
    fun uploadStats(): Pair<Int, Int> = (queue.waiting() + fastQueue.waiting()) to (queue.failed() + fastQueue.failed())
    fun resetFailedUploads() { queue.resetFailed(); fastQueue.resetFailed() }

    companion object {
        private const val NS = "vmeta"

        /** লোকাল ফাইল থেকে */
        fun extract(file: File, fast: Boolean = false): ByteArray? = extract(fast) { r, e -> r.setDataSource(file.path); e.setDataSource(file.path) }

        /** স্ট্রিম URL থেকে (পুরো ভিডিও না নামিয়ে) */
        fun extractUrl(url: String): ByteArray? = extract(false) { r, e -> r.setDataSource(url, HashMap()); e.setDataSource(url, HashMap()) }

        private fun extract(fast: Boolean, open: (MediaMetadataRetriever, MediaExtractor) -> Unit): ByteArray? {
            val r = MediaMetadataRetriever()
            val ex = MediaExtractor()
            return try {
                open(r, ex)
                fun meta(k: Int) = r.extractMetadata(k)
                var w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                var h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val dur = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val rot = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                var br = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                var vc = ""
                var ac = ""
                var fps = 0f
                var hdr = "SDR"
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && vc.isEmpty()) {
                        vc = mime.removePrefix("video/")
                        if (w == 0) w = f.optInt(MediaFormat.KEY_WIDTH)
                        if (h == 0) h = f.optInt(MediaFormat.KEY_HEIGHT)
                        fps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrElse { f.optInt(MediaFormat.KEY_FRAME_RATE).toFloat() } else 0f
                        hdr = when {
                            mime == "video/dolby-vision" -> "DolbyVision"
                            f.optInt(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
                            f.optInt(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                            else -> "SDR"
                        }
                        if (br == 0L) br = f.optInt(MediaFormat.KEY_BIT_RATE).toLong()
                    } else if (mime.startsWith("audio/") && ac.isEmpty()) {
                        ac = mime.removePrefix("audio/")
                    }
                }
                if (fps <= 0f) { // কনটেইনার FPS না দিলে ফ্রেম-সংখ্যা ÷ সময়
                    val frames = if (android.os.Build.VERSION.SDK_INT >= 28)
                        meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() else null
                    if (frames != null && dur > 0) fps = frames * 1000f / dur
                }
                if (w <= 0 && h <= 0 && dur <= 0L) null
                else Info(dur, w, h, vc, ac, fps, br, hdr, rot, fast).toBytes()
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { r.release() }
                runCatching { ex.release() }
            }
        }

        private fun MediaFormat.optInt(k: String) = if (containsKey(k)) runCatching { getInteger(k) }.getOrDefault(0) else 0
    }
}

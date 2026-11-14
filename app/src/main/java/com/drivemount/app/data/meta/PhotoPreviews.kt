package com.drivemount.app.data.meta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ছবির "মাঝারি" প্রিভিউ (~১২৮০px, ~১০০-২০০KB)।
 *  - আপলোডের সময় বানিয়ে ক্লাউডের `.dmmeta/prev/<id>.webp`-এ রাখা হয়
 *  - ফোনে এনক্রিপ্টেড ক্যাশে (আলাদা, বড় সীমার) জমে থাকে, তাই ইন্টারনেট ছাড়াও ছবি দেখা যায়
 *  - ফুল-স্ক্রিনে আসল ছবির আগে এটি দেখানো হয়
 * সব ভারী কাজ পেছনের থ্রেডে; UI শুধু একটি ছোট ছবি ডিকোড করে।
 */
class PhotoPreviews(
    private val context: Context,
    private val api: RcloneApi,
    private val cache: VaultCache,
) {
    private val prefs = context.getSharedPreferences("photos_ui", Context.MODE_PRIVATE)

    /** Photos ট্যাবের ফোল্ডারের প্রিভিউ আগেভাগে ফোনে জমানো (শুধু Wi-Fi-তে) */
    var offlineEnabled: Boolean
        get() = prefs.getBoolean("offline", true)
        set(v) { prefs.edit().putBoolean("offline", v).apply() }

    private val missing = ConcurrentHashMap.newKeySet<String>()

    private fun key(remote: String, path: String, size: Long) = "$remote|$path|$size"

    /** ছোট ছবির আলাদা প্রিভিউ লাগে না: আসল ছবিই যথেষ্ট ছোট */
    fun needsPreview(size: Long) = size >= MIN_ORIGINAL_BYTES

    fun has(remote: String, path: String, size: Long) = cache.has(NS, key(remote, path, size))

    fun store(remote: String, path: String, size: Long, bytes: ByteArray) {
        cache.put(NS, key(remote, path, size), bytes)
    }

    fun usedBytes(): Long = cache.sizeBytes()

    fun clear() {
        cache.clear()
        missing.clear()
    }

    /** ক্লাউডে প্রিভিউয়ের পথ: নাম+সাইজ থেকে হ্যাশ, তাই ফাইল বদলালে পুরোনো প্রিভিউ আর মেলে না */
    fun remotePath(path: String, size: Long): String {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val id = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
        return joinPath(joinPath(joinPath(dir, META_DIR), "prev"), "$id.webp")
    }

    suspend fun bitmap(remote: String, path: String, size: Long): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = cache.get(NS, key(remote, path, size)) ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** ক্লাউড থেকে প্রিভিউটি (ছোট ফাইল) এনে ফোনে রাখে; নেই/ব্যর্থ হলে false (এই সেশনে আর চেষ্টা নয়) */
    suspend fun fetchRemote(remote: String, fs: String, path: String, size: Long): Boolean {
        if (has(remote, path, size)) return true
        val k = key(remote, path, size)
        if (k in missing) return false
        val tmp = File(context.cacheDir, "prev_dl/${System.nanoTime()}").apply { mkdirs() }
        return try {
            api.transferFile(fs, remotePath(path, size), tmp.absolutePath, "p.webp", false)
            val bytes = File(tmp, "p.webp").readBytes()
            if (bytes.isEmpty()) throw IllegalStateException("empty")
            store(remote, path, size, bytes)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            missing.add(k)
            false
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** ফোল্ডারের সাইডকার তালিকায় প্রিভিউটি আছে জানা থাকলেই শুধু আনে (ব্যর্থ রিকোয়েস্ট বাঁচাতে) */
    suspend fun ensureCached(remote: String, fs: String, path: String, size: Long, existing: Set<String>): Boolean {
        if (!needsPreview(size)) return true
        if (has(remote, path, size)) return true
        if (remotePath(path, size) !in existing) return false
        return fetchRemote(remote, fs, path, size)
    }

    /** ফাইল কপি/সরানো/নাম বদলে: প্রিভিউ নতুন পথের জন্য ফোনে বসানো ও ক্লাউডে (নতুন নামের হ্যাশে) তোলা */
    suspend fun migrate(srcRemote: String, dstRemote: String, dstFs: String, oldPath: String, newPath: String, size: Long) {
        val bytes = withContext(Dispatchers.IO) { cache.get(NS, key(srcRemote, oldPath, size)) } ?: return
        store(dstRemote, newPath, size, bytes)
        enqueueUpload(dstFs, newPath, size, bytes)
    }

    // ---------------- আপলোড ----------------

    private class Upload(val fs: String, val remotePath: String, val file: File) { var attempts = 0 }

    private val uploads = ConcurrentLinkedQueue<Upload>()

    /** উৎস-ফোল্ডারের (key) ক্লাউডে থাকা প্রিভিউ-ফাইলের পথ: তালিকা থেকে জানা, ব্যাকগ্রাউন্ড কাজ এটি ব্যবহার করে */
    val remoteSets = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    fun enqueueUpload(fs: String, path: String, size: Long, bytes: ByteArray) {
        val file = File(context.cacheDir, "prev_up/${System.nanoTime()}.webp")
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            uploads.add(Upload(fs, remotePath(path, size), file))
        }
    }

    /** জমে থাকা প্রিভিউগুলো ক্লাউডে পাঠায় (কাজের লাইন ফাঁকা হলে); ব্যর্থ হলে ফোনের কপি থেকেই যায় */
    suspend fun flushUploads() = withContext(Dispatchers.IO) {
        while (true) {
            val up = uploads.poll() ?: break
            var keep = false
            try {
                api.transferFile(up.file.parent ?: continue, up.file.name, up.fs, up.remotePath, false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                keep = true
                uploads.add(up)
                throw e
            } catch (_: Exception) {
                // ৩ বার পর্যন্ত পরে আবার চেষ্টা; ফোনে প্রিভিউ আগে থেকেই আছে
                if (++up.attempts < 3) { keep = true; uploads.add(up) }
            } finally {
                if (!keep) up.file.delete()
            }
        }
    }

    private companion object {
        const val NS = "prev"
        const val MIN_ORIGINAL_BYTES = 250L * 1024
    }
}

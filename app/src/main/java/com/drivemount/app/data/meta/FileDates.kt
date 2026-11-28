package com.drivemount.app.data.meta

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * প্রতিটি আপলোড-করা ফাইলের দুটি তারিখ: ১) তোলার/বানানোর তারিখ (ছবির EXIF, ভিডিওর মেটাডেটা, নইলে মূল ফাইলের তারিখ)
 * ২) আপলোডের তারিখ। ফোনের এনক্রিপ্টেড ক্যাশে থাকে এবং ফোল্ডার-প্রতি একটি ছোট প্যাকে (`.dmmeta/dates.dmd`) ক্লাউডে যায়,
 * তাই অন্য ফোনেও দুটি তারিখই দেখা যায়। যেকোনো ধরনের ফাইলের জন্য (ছবি/ভিডিও/PDF/...)।
 */
class FileDates(
    private val context: Context,
    private val api: RcloneApi,
    private val cache: VaultCache,
) {
    /** 0 = অজানা */
    data class D(val taken: Long, val uploaded: Long)

    private val mem = ConcurrentHashMap<String, D>()
    private val none = ConcurrentHashMap.newKeySet<String>() // ক্যাশে নেই বলে জানা
    private val triedFolders = ConcurrentHashMap.newKeySet<String>()

    private class Pending(val fs: String, val folder: String, val name: String, val size: Long, val d: D)

    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Pending>()

    private fun key(remote: String, path: String, size: Long) = "$remote|$path|$size"

    /** মেমরি থেকে তাৎক্ষণিক (সারি আঁকার সময়); না থাকলে null */
    fun peek(remote: String, path: String, size: Long): D? = mem[key(remote, path, size)]

    private fun put(remote: String, path: String, size: Long, d: D) {
        val k = key(remote, path, size)
        mem[k] = d
        none.remove(k)
        cache.put(NS, k, encodeOne(d))
    }

    /** ফোল্ডারের ফাইলগুলোর তারিখ ডিস্ক-ক্যাশ থেকে মেমরিতে আনা; কিছু নতুন পেলে true */
    suspend fun warm(remote: String, entries: List<RemoteEntry>): Boolean = withContext(Dispatchers.IO) {
        var found = false
        for (e in entries) {
            if (e.isDir) continue
            val size = e.size ?: continue
            val k = key(remote, e.path, size)
            if (mem.containsKey(k) || k in none) continue
            val b = cache.get(NS, k)
            val d = b?.let { runCatching { decodeOne(it) }.getOrNull() }
            if (d != null) { mem[k] = d; found = true } else none.add(k)
        }
        found
    }

    /**
     * ফোল্ডারে তারিখ-জানা-নেই এমন ফাইল থাকলে ক্লাউডের প্যাক (`.dmmeta/dates.dmd`) একবার এনে ক্যাশে বসানো
     * (অন্য ফোন থেকে আপলোড করা ফাইলের তারিখের জন্য)। প্রতি ফোল্ডারে সেশনে একবারই চেষ্টা। নতুন কিছু পেলে true।
     */
    suspend fun ensureFolder(remote: String, fs: String, folder: String, entries: List<RemoteEntry>): Boolean {
        if (entries.none { !it.isDir && it.size != null && peek(remote, it.path, it.size) == null }) return false
        if (!triedFolders.add("$remote|$folder")) return false
        return withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "dates_dl/${System.nanoTime()}").apply { mkdirs() }
            try {
                api.transferFile(fs, packPath(folder), tmp.absolutePath, PACK, false)
                val list = decodePack(File(tmp, PACK).readBytes())
                var any = false
                for ((name, size, d) in list) {
                    put(remote, joinPath(folder, name), size, d)
                    any = true
                }
                any
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false // প্যাক নেই/নেট নেই: কিছু করার নেই
            } finally {
                tmp.deleteRecursively()
            }
        }
    }

    /** আপলোড শেষে: দুটি তারিখ জমা (ফোনে সঙ্গে সঙ্গে, ক্লাউডে লাইন ফাঁকা হলে) */
    fun record(remote: String, fs: String, path: String, size: Long, taken: Long, uploaded: Long) {
        val d = D(taken, uploaded)
        put(remote, path, size, d)
        pending.add(Pending(fs, path.substringBeforeLast('/', ""), path.substringAfterLast('/'), size, d))
    }

    /** কপি/সরানো/নাম বদলে: তারিখ দুটি নতুন পথে নেওয়া */
    suspend fun migrate(srcRemote: String, dstRemote: String, dstFs: String, oldPath: String, newPath: String, size: Long) {
        val d = peek(srcRemote, oldPath, size) ?: withContext(Dispatchers.IO) {
            cache.get(NS, key(srcRemote, oldPath, size))?.let { runCatching { decodeOne(it) }.getOrNull() }
        } ?: return
        if (!dstFs.endsWith(":") || dstFs.startsWith("/")) return
        record(dstRemote, dstFs, newPath, size, d.taken, d.uploaded)
    }

    /** জমা থাকা তারিখগুলো ফোল্ডার-প্রতি একবারে ক্লাউডের প্যাকে মিলিয়ে লেখা */
    suspend fun flush() = withContext(Dispatchers.IO) {
        if (pending.isEmpty()) return@withContext
        val byFolder = LinkedHashMap<Pair<String, String>, MutableList<Pending>>()
        while (true) {
            val p = pending.poll() ?: break
            if (!p.fs.endsWith(":") || p.fs.startsWith("/")) continue // ফোনের নিজের পথে প্যাক লাগে না
            byFolder.getOrPut(p.fs to p.folder) { ArrayList() } += p
        }
        for ((k, items) in byFolder) {
            val (fs, folder) = k
            val tmp = File(context.cacheDir, "dates_up/${System.nanoTime()}").apply { mkdirs() }
            try {
                val merged = LinkedHashMap<String, Triple<String, Long, D>>()
                runCatching {
                    api.transferFile(fs, packPath(folder), tmp.absolutePath, PACK, false)
                    decodePack(File(tmp, PACK).readBytes())
                }.getOrNull()?.forEach { merged["${it.first}|${it.second}"] = it }
                for (p in items) merged["${p.name}|${p.size}"] = Triple(p.name, p.size, p.d)
                File(tmp, PACK).writeBytes(encodePack(merged.values))
                api.transferFile(tmp.absolutePath, PACK, fs, packPath(folder), false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                pending.addAll(items); throw e
            } catch (_: Exception) {
                pending.addAll(items) // নেট/সার্ভারের সমস্যা: পরের বার আবার (ফোনে তারিখ আগে থেকেই আছে)
            } finally {
                tmp.deleteRecursively()
            }
        }
    }

    private fun packPath(folder: String) = joinPath(joinPath(folder, META_DIR), PACK)

    // ---------------- বাইনারি ফরম্যাট ----------------

    private fun encodeOne(d: D): ByteArray {
        val b = ByteArrayOutputStream()
        DataOutputStream(b).use { it.writeLong(d.taken); it.writeLong(d.uploaded) }
        return b.toByteArray()
    }

    private fun decodeOne(bytes: ByteArray): D = DataInputStream(ByteArrayInputStream(bytes)).use { D(it.readLong(), it.readLong()) }

    private fun encodePack(items: Collection<Triple<String, Long, D>>): ByteArray {
        val b = ByteArrayOutputStream()
        DataOutputStream(b).use {
            it.writeInt(MAGIC)
            it.writeInt(items.size)
            for ((name, size, d) in items) {
                it.writeUTF(name); it.writeLong(size); it.writeLong(d.taken); it.writeLong(d.uploaded)
            }
        }
        return b.toByteArray()
    }

    private fun decodePack(bytes: ByteArray): List<Triple<String, Long, D>> =
        DataInputStream(ByteArrayInputStream(bytes)).use {
            require(it.readInt() == MAGIC) { "bad dates pack" }
            val n = it.readInt()
            require(n in 0..20000) { "bad dates pack size" }
            List(n) { _ -> Triple(it.readUTF(), it.readLong(), D(it.readLong(), it.readLong())) }
        }

    companion object {
        private const val NS = "fdates"
        private const val PACK = "dates.dmd"
        private const val MAGIC = 0x444D4431 // "DMD1"

        private val OLDEST = 631_152_000_000L // ১৯৯০-০১-০১: এর আগের তারিখ ভুল ধরা হয় (কিছু ডিভাইসে ১৯০৪/১৯৭০)

        private fun sane(ms: Long?): Long? = ms?.takeIf { it >= OLDEST && it <= System.currentTimeMillis() + 86_400_000L }

        /** ছবির EXIF বা ভিডিওর মেটাডেটা থেকে তোলার তারিখ; না পেলে null */
        fun fromMedia(file: File): Long? {
            val name = file.name
            return runCatching {
                when {
                    ThumbMetaReader.isImage(name) -> {
                        val ex = android.media.ExifInterface(file.path)
                        val s = ex.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: ex.getAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                            ?: ex.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                        s?.let { sane(SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it.trim())?.time) }
                    }
                    ThumbMetaReader.isMedia(name) -> {
                        val r = MediaMetadataRetriever()
                        try {
                            r.setDataSource(file.path)
                            val s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            s?.let {
                                val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                                sane(f.parse(it.take(15))?.time)
                            }
                        } finally {
                            runCatching { r.release() }
                        }
                    }
                    else -> null
                }
            }.getOrNull()
        }

        /** ফাইল-পিকার (content URI) থেকে বাছা ফাইলের মূল তারিখ: DATE_TAKEN, নইলে last-modified; না পেলে null */
        fun fromUri(context: Context, uri: Uri): Long? = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                fun col(n: String) = c.getColumnIndex(n).takeIf { it >= 0 }?.let { i -> c.getLong(i).takeIf { v -> v > 0 } }
                sane(col("datetaken"))
                    ?: sane(col("last_modified"))
                    ?: sane(col("date_modified")?.let { it * 1000 })
            }
        }.getOrNull()
    }
}

package com.drivemount.app.data.meta

import android.content.Context
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** জিপের ভেতরের একটি এন্ট্রি; পথ "/"-এ শেষ হলে ফোল্ডার */
class ZipEntryInfo(val path: String, val size: Long, val csize: Long, val mtime: Long) {
    val isDir get() = path.endsWith("/")
}

/** জিপের "সেন্ট্রাল ডিরেক্টরি" শুধু শেষ অংশ পড়ে বের করা — পুরো ফাইল নামানো/এক্সট্রাক্ট লাগে না */
interface ZipSource {
    val size: Long
    fun read(off: Long, len: Int): ByteArray
}

class FileZipSource(private val file: File) : ZipSource {
    override val size: Long get() = file.length()
    override fun read(off: Long, len: Int): ByteArray = RandomAccessFile(file, "r").use { r ->
        r.seek(off); ByteArray(len).also { r.readFully(it) }
    }
}

/** rclone-এর স্ট্রিম URL (লোকাল/ক্লাউড/ভল্ট সবই) থেকে Range রিকোয়েস্টে পড়া */
class UrlZipSource(private val url: String, override val size: Long) : ZipSource {
    override fun read(off: Long, len: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000; c.readTimeout = 40_000
            c.setRequestProperty("Range", "bytes=$off-${off + len - 1}")
            val code = c.responseCode
            if (code != 206 && code != 200) throw java.io.IOException("HTTP $code")
            val ins = c.inputStream
            if (code == 200 && off > 0) { var left = off; while (left > 0) { val s = ins.skip(left); if (s <= 0) break; left -= s } }
            val out = ByteArray(len)
            var n = 0
            while (n < len) { val r = ins.read(out, n, len - n); if (r < 0) break; n += r }
            if (n < len) throw java.io.IOException("short read")
            return out
        } finally { c.disconnect() }
    }
}

object ZipIndex {
    private const val MAGIC = 0x445A4931 // "DZI1"
    private const val MAX_CD = 64L * 1024 * 1024

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)
    private fun u64(b: ByteArray, o: Int) = u32(b, o) or (u32(b, o + 4) shl 32)

    private fun dosTime(date: Int, time: Int): Long = runCatching {
        GregorianCalendar((date shr 9) + 1980, ((date shr 5) and 15) - 1, date and 31, time shr 11, (time shr 5) and 63, (time and 31) * 2)
            .timeInMillis
    }.getOrDefault(0L)

    private fun nameOf(b: ByteArray, o: Int, n: Int, utf8Flag: Boolean): String {
        val s = String(b, o, n, Charsets.UTF_8)
        return if (utf8Flag || !s.contains('\uFFFD')) s else String(b, o, n, Charset.forName("IBM437"))
    }

    /** ঠিকমতো জিপ না হলে বা ডিরেক্টরি অতি বড় হলে null */
    fun read(src: ZipSource): List<ZipEntryInfo>? = runCatching {
        val n = src.size
        if (n < 22) return null
        val tailLen = minOf(n, 66_000L).toInt()
        val tail = src.read(n - tailLen, tailLen)
        var i = tail.size - 22
        while (i >= 0 && u32(tail, i) != 0x06054b50L) i--
        if (i < 0) return null
        var cdSize = u32(tail, i + 12)
        var cdOff = u32(tail, i + 16)
        if (i >= 20 && u32(tail, i - 20) == 0x07064b50L) {
            val rec = src.read(u64(tail, i - 20 + 8), 56)
            if (u32(rec, 0) == 0x06064b50L) { cdSize = u64(rec, 40); cdOff = u64(rec, 48) }
        }
        if (cdSize <= 0 || cdSize > MAX_CD) return if (cdSize == 0L) emptyList() else null
        val cd = src.read(cdOff, cdSize.toInt())
        val out = ArrayList<ZipEntryInfo>()
        var p = 0
        while (p + 46 <= cd.size && u32(cd, p) == 0x02014b50L) {
            val flags = u16(cd, p + 8)
            val time = u16(cd, p + 12)
            val date = u16(cd, p + 14)
            var cs = u32(cd, p + 20)
            var us = u32(cd, p + 24)
            val nlen = u16(cd, p + 28)
            val elen = u16(cd, p + 30)
            val clen = u16(cd, p + 32)
            val name = nameOf(cd, p + 46, nlen, flags and 0x800 != 0)
            var e = p + 46 + nlen
            val eEnd = e + elen
            while (e + 4 <= eEnd) {
                val id = u16(cd, e); val len = u16(cd, e + 2)
                if (id == 1) {
                    var q = e + 4
                    if (us == 0xFFFFFFFFL && q + 8 <= eEnd) { us = u64(cd, q); q += 8 }
                    if (cs == 0xFFFFFFFFL && q + 8 <= eEnd) { cs = u64(cd, q) }
                }
                e += 4 + len
            }
            if (name.isNotEmpty()) out += ZipEntryInfo(name.replace('\\', '/').trimStart('/'), us, cs, dosTime(date, time))
            p += 46 + nlen + elen + clen
        }
        out
    }.getOrNull()

    fun encode(list: List<ZipEntryInfo>): ByteArray {
        val bo = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bo)).use { d ->
            d.writeInt(MAGIC); d.writeInt(list.size)
            for (e in list) { d.writeUTF(e.path); d.writeLong(e.size); d.writeLong(e.csize); d.writeLong(e.mtime) }
        }
        return bo.toByteArray()
    }

    fun decode(bytes: ByteArray): List<ZipEntryInfo>? = runCatching {
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { d ->
            if (d.readInt() != MAGIC) return null
            val n = d.readInt()
            val out = ArrayList<ZipEntryInfo>(n)
            repeat(n) { out += ZipEntryInfo(d.readUTF(), d.readLong(), d.readLong(), d.readLong()) }
            out
        }
    }.getOrNull()
}

/**
 * জিপের ফোল্ডার-ট্রী ও সাইজের তালিকা: ফোনের এনক্রিপ্টেড ক্যাশে + ক্লাউডে ছোট সাইডকার (`.dmmeta/zidx/`)।
 * আপলোডের সময়ই বানিয়ে তোলা হয়; না থাকলে (পুরোনো জিপ) জিপের শেষ অংশ Range-এ পড়ে বানিয়ে ক্যাশ ও আপলোড করে।
 */
class ZipIndexStore(
    private val context: Context,
    private val api: RcloneApi,
    private val cache: VaultCache,
) {
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private fun key(remote: String, path: String, size: Long) = "$remote|$path|$size"

    fun remotePath(path: String, size: Long): String {
        val dir = path.substringBeforeLast('/', "")
        val id = MessageDigest.getInstance("SHA-1").digest("${path.substringAfterLast('/')}|$size".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
        return joinPath(joinPath(joinPath(dir, META_DIR), "zidx"), "$id.dzi")
    }

    /** আপলোডের আগে লোকাল ফাইল থেকে বানানো (move হলে সোর্স পরে থাকে না) */
    suspend fun buildFromFile(f: File): ByteArray? = withContext(Dispatchers.IO) {
        ZipIndex.read(FileZipSource(f))?.let { ZipIndex.encode(it) }
    }

    /** এই জিপের ফোল্ডার-ট্রী ইনডেক্স লোকালে আছে কি */
    fun has(fs: String, path: String, size: Long): Boolean = cache.has(NS, key(fs.removeSuffix(":"), path, size))

    suspend fun storeAndUpload(fs: String, path: String, size: Long, bytes: ByteArray) {
        if (!fs.endsWith(":") || fs.startsWith("/")) return // শুধু ক্লাউড/ভল্ট
        withContext(Dispatchers.IO) { cache.put(NS, key(fs.removeSuffix(":"), path, size), bytes) }
        queue.enqueue(fs, remotePath(path, size), bytes)
    }

    /** ক্যাশ → ক্লাউডের সাইডকার → (শেষ উপায়) Range-এ জিপের শেষ অংশ পড়া */
    suspend fun load(remote: String, fs: String, path: String, size: Long): List<ZipEntryInfo>? = withContext(Dispatchers.IO) {
        val k = key(remote, path, size)
        cache.get(NS, k)?.let { b -> ZipIndex.decode(b)?.let { return@withContext it } }
        val isLocal = fs.endsWith(":/")
        if (!isLocal && k !in missing) {
            val tmp = File(context.cacheDir, "zidx_dl/${System.nanoTime()}").apply { mkdirs() }
            try {
                api.transferFile(fs, remotePath(path, size), tmp.absolutePath, "z.dzi", false)
                val b = File(tmp, "z.dzi").readBytes().takeIf { it.isNotEmpty() }
                val list = b?.let { ZipIndex.decode(it) }
                if (b != null && list != null) { cache.put(NS, k, b); return@withContext list }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally { tmp.deleteRecursively() }
            missing.add(k)
        }
        val url = api.streamUrl(fs, path, "application/zip", background = false)
        val list = ZipIndex.read(UrlZipSource(url, size)) ?: return@withContext null
        val b = ZipIndex.encode(list)
        cache.put(NS, k, b)
        if (!isLocal) runCatching { queue.enqueue(fs, remotePath(path, size), b); queue.flush() }
        list
    }

    suspend fun deleteRemote(fs: String, path: String, size: Long) {
        runCatching { api.deleteFile(fs, remotePath(path, size)) }
    }

    private val queue = SidecarUploadQueue(context, api, "zidx_up")
    suspend fun flushUploads() = queue.flush()
    fun uploadStats(): Pair<Int, Int> = queue.waiting() to queue.failed()
    fun resetFailedUploads() = queue.resetFailed()

    companion object { private const val NS = "zidx" }
}

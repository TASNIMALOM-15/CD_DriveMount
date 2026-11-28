package com.drivemount.app.data.meta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.joinPath
import com.drivemount.app.security.VaultCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** প্রতিটি ফোল্ডারের ভেতরের লুকানো সাইডকার ফোল্ডার; তালিকায় ব্যবহারকারীকে দেখানো হয় না */
const val META_DIR = ".dmmeta"

private const val PACK_NAME = "pack.dmp"
private const val THUMB_PACK_NAME = "thumbs2.dmp" // v2: EXIF-ঘোরানো থাম্বনেল (পুরোনো thumbs.dmp কাত হতে পারে)
private const val NS = "apk"
private const val NS_THUMB = "thumb2"
private const val AUDIO_PACK_NAME = "audio.dmp"
private const val NS_AUDIO = "audio"
private const val MAX_AUDIO_PACK_ENTRIES = 200
private val ALL_PACKS = listOf(PACK_NAME, THUMB_PACK_NAME, AUDIO_PACK_NAME)
private const val MAX_PACK_ENTRIES = 400
private const val MAX_FLUSH_ATTEMPTS = 3
private const val MAX_THUMB_PACK_ENTRIES = 300

/**
 * সাইডকার মেটাডেটা: APK আপলোডের সময়ই তার আইকন বের করে
 *  1. লোকালে এনক্রিপ্টেড ক্যাশে রাখে (VaultCache), তাই ব্রাউজে সঙ্গে সঙ্গে আইকন দেখা যায়
 *  2. ক্লাউডে ফোল্ডারের `.dmmeta/pack.dmp`-এ জমা রাখে (এনক্রিপ্টেড ভল্টে crypt নিজেই এনক্রিপ্ট করে),
 *     যাতে অন্য ফোনে/নতুন ইনস্টলে ফোল্ডার খুললেই ১টা ছোট রিকোয়েস্টে সব আইকন আসে
 *
 * ভারী কাজ (APK পড়া, প্যাক মেলানো/আপলোড/ডাউনলোড) সবই পেছনের থ্রেডে; UI শুধু ছোট WebP ডিকোড করে।
 */
class SidecarManager(
    private val context: Context,
    private val api: RcloneApi,
    private val cache: VaultCache,
    private val previews: PhotoPreviews,
    private val seeks: SeekSheets,
    private val dates: FileDates,
    private val vmetas: VideoMetas = VideoMetas(context, api, VaultCache(context, "vmeta_cache", 16L * 1024 * 1024, touchOnRead = true)),
) {
    /** Compose-এ পড়া হয়: নতুন আইকন এলে বাড়ে, তাই শুধু APK-র আইকনগুলো আবার আঁকা হয় */
    val version = mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(1)

    private val icons = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** আপলোড শেষ, কিন্তু ক্লাউডের প্যাকে এখনো লেখা হয়নি: "fs\u0000ফোল্ডার" -> তথ্য */
    private val pending = ConcurrentHashMap<String, CopyOnWriteArrayList<SidecarEntry>>()
    private val lastImport = ConcurrentHashMap<String, Long>()

    private val thumbs = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun key(remote: String, path: String, size: Long) = "$remote|$path|$size"

    private fun isApk(name: String) = name.endsWith(".apk", ignoreCase = true)
    private fun packOf(name: String) = when {
        isApk(name) -> PACK_NAME
        AudioMetaReader.isAudio(name) -> AUDIO_PACK_NAME
        else -> THUMB_PACK_NAME
    }
    private fun nsOf(name: String) = nsOfPack(packOf(name))
    private fun nsOfPack(pack: String) = when (pack) {
        PACK_NAME -> NS
        AUDIO_PACK_NAME -> NS_AUDIO
        else -> NS_THUMB
    }
    private fun capOf(pack: String) = when (pack) {
        PACK_NAME -> MAX_PACK_ENTRIES
        AUDIO_PACK_NAME -> MAX_AUDIO_PACK_ENTRIES
        else -> MAX_THUMB_PACK_ENTRIES
    }

    /** এই নামের ফাইলের জন্য সাইডকার তৈরি হয় কি (APK, ছবি, ভিডিও) */
    fun supports(name: String) = isApk(name) || ThumbMetaReader.isMedia(name) || AudioMetaReader.isAudio(name)

    /** প্রিভিউ লোকালে আছে কি: মেমরি বা একটি ফাইল-stat; কোনো ডিক্রিপ্ট/নেটওয়ার্ক নয় */
    fun hasThumb(remote: String, path: String, size: Long): Boolean {
        val k = key(remote, path, size)
        return thumbs.get(k) != null || cache.has(NS_THUMB, k)
    }

    fun cachedThumb(remote: String, path: String, size: Long): Bitmap? = thumbs.get(key(remote, path, size))

    suspend fun thumbFor(remote: String, path: String, size: Long): Bitmap? {
        val k = key(remote, path, size)
        thumbs.get(k)?.let { return it }
        return withContext(Dispatchers.IO) {
            val blob = cache.get(NS_THUMB, k) ?: return@withContext null
            val entry = runCatching { SidecarCodec.decodeOne(blob) }.getOrNull() ?: return@withContext null
            val bmp = BitmapFactory.decodeByteArray(entry.icon, 0, entry.icon.size) ?: return@withContext null
            thumbs.put(k, bmp)
            bmp
        }
    }

    fun cachedIcon(remote: String, path: String, size: Long): Bitmap? = icons.get(key(remote, path, size))

    /** আইকন: মেমরি, না থাকলে এনক্রিপ্টেড ডিস্ক-ক্যাশ; কোথাও না থাকলে null (কোনো নেটওয়ার্ক কল হয় না) */
    suspend fun iconFor(remote: String, path: String, size: Long): Bitmap? {
        val k = key(remote, path, size)
        icons.get(k)?.let { return it }
        return withContext(Dispatchers.IO) {
            val blob = cache.get(NS, k) ?: return@withContext null
            val entry = runCatching { SidecarCodec.decodeOne(blob) }.getOrNull() ?: return@withContext null
            val bmp = BitmapFactory.decodeByteArray(entry.icon, 0, entry.icon.size) ?: return@withContext null
            icons.put(k, bmp)
            bmp
        }
    }

    // ---------------- আপলোডের সময় ----------------

    /** আপলোড শুরুর আগে (সোর্স ফাইল এখনো আছে; "move" হলে পরে থাকে না) তথ্য বের করা */
    suspend fun prepare(file: File?, onStage: (Int) -> Unit = {}, cancelled: () -> Boolean = { false }, allowFast: Boolean = true): SidecarEntry? {
        if (file == null) return null
        return withContext(Dispatchers.IO) {
            when {
                isApk(file.name) -> ApkMetaReader.read(context, file, file.name)
                AudioMetaReader.isAudio(file.name) -> AudioMetaReader.read(file, file.name)
                else -> ThumbMetaReader.read(file, file.name, onStage)?.let { if (allowFast) attachFast(file, it, onStage, cancelled) else it }
            }
        }
    }

    /** MP4/MOV-এর moov শেষে থাকলে lossless fast-start কপি বানানো (মূল ফাইল শুধু পড়া হয়, কখনো বদলায় না) */
    private fun attachFast(file: File, e: SidecarEntry, onStage: (Int) -> Unit = {}, cancelled: () -> Boolean = { false }): SidecarEntry {
        if (!FastStart.isCandidate(file.name) || !FastStart.isEnabled(context)) return e
        if (!FastStart.needsFastStart(file)) return e
        if (cancelled()) return e
        onStage(com.drivemount.app.data.transfer.TransferStage.FAST)
        val dir = File(context.cacheDir, "fast_tmp/${System.nanoTime()}")
        // কপির জন্য ফাঁকা জায়গা লাগে (≈ ভিডিওর সমান)
        if (context.cacheDir.usableSpace < file.length() + 64L * 1024 * 1024) return e
        val out = File(dir, file.name)
        if (!FastStart.remux(file, out) { !cancelled() }) { dir.deleteRecursively(); return e }
        return e.copy(fast = out, vmeta = e.vmeta?.let { vmetas.markFast(it) })
    }

    /** আপলোড সফল হলে: লোকাল ক্যাশে রাখা ও ক্লাউডের প্যাকের জন্য লাইনে রাখা */
    suspend fun commit(dstFs: String, dstPath: String, entry: SidecarEntry) {
        // শুধু rclone remote ("dmabc:" ধরনের); ফোনের নিজের পথে সাইডকার লাগে না
        if (!dstFs.endsWith(":") || dstFs.startsWith("/")) return
        val remote = dstFs.removeSuffix(":")
        val named = entry.copy(name = dstPath.substringAfterLast('/'))
        withContext(Dispatchers.IO) { cache.put(nsOf(named.name), key(remote, dstPath, named.size), SidecarCodec.encodeOne(named)) }
        named.preview?.let { p ->
            previews.store(remote, dstPath, named.size, p)
            previews.enqueueUpload(dstFs, dstPath, named.size, p)
        }
        named.seek?.let { sk ->
            seeks.store(remote, dstPath, named.size, sk)
            seeks.enqueueUpload(dstFs, dstPath, named.size, sk)
        }
        named.fast?.let { f -> vmetas.enqueueFast(dstFs, dstPath, named.size, f) }
        named.vmeta?.let { vm ->
            vmetas.store(remote, dstPath, named.size, vm)
            vmetas.enqueueUpload(dstFs, dstPath, named.size, vm)
        }
        version.intValue++
        val dir = dstPath.substringBeforeLast('/', "")
        pending.getOrPut("$dstFs\u0000$dir\u0000${packOf(named.name)}") { CopyOnWriteArrayList() }
            .also { l -> l.removeAll { it.name == named.name } }.add(named) // একই ফাইলের দ্বিতীয় এন্ট্রি নয়
    }

    /**
     * লাইনে থাকা তথ্য ক্লাউডের প্যাকে লেখে। ফোল্ডার-প্রতি একবারই (পড়া → মেলানো → লেখা), তাই ৫০টা APK
     * একসঙ্গে আপলোড করলেও ৫০ বার নয়। কাজের লাইন ফাঁকা হলে ডাকা হয়; ব্যর্থ হলে লোকাল আইকন থেকেই যায়।
     */
    suspend fun flushPending() = withContext(Dispatchers.IO) {
        if (!flushing.compareAndSet(false, true)) return@withContext
        try {
            lastFlush = SystemClock.elapsedRealtime()
            for (k in pending.keys.toList()) {
                val items = pending.remove(k) ?: continue
                val parts = k.split('\u0000')
                try {
                    mergeIntoRemotePack(parts[0], parts[1], parts[2], items.toList())
                    failures.remove(k)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    pending.getOrPut(k) { CopyOnWriteArrayList() }.addAll(items)
                    throw e
                } catch (_: Exception) {
                    // নেট/সার্ভারের সমস্যা: ৩ বার পর্যন্ত পরে আবার চেষ্টা (লোকাল তথ্য আগে থেকেই আছে, তাই এই ফোনে কিছু হারায় না)
                    val n = (failures[k] ?: 0) + 1
                    if (n < MAX_FLUSH_ATTEMPTS) {
                        failures[k] = n
                        pending.getOrPut(k) { CopyOnWriteArrayList() }.addAll(items)
                    } else {
                        // ৩ বারেও না হলে চুপচাপ ফেলে না দিয়ে "ব্যর্থ" তালিকায় রাখা: ব্যবহারকারী Retry দিতে পারবে
                        failures.remove(k)
                        packFailed.getOrPut(k) { CopyOnWriteArrayList() }.also { l ->
                            l.removeAll { e -> items.any { it.name == e.name } }
                        }.addAll(items)
                    }
                }
            }
            runCatching { previews.flushUploads() }
            runCatching { seeks.flushUploads() }
            runCatching { (context.applicationContext as com.drivemount.app.DriveMountApp).zipIndex.flushUploads() }
            runCatching { vmetas.flushUploads() }
            runCatching { dates.flush() }
        } finally {
            flushing.set(false)
        }
    }

    /** ক্লাউড-প্যাকে লেখা ৩ বার ব্যর্থ হওয়া এন্ট্রি (লোকাল আইকন আছে); [retryFailed]-এ আবার লাইনে ফেরে */
    private val packFailed = ConcurrentHashMap<String, CopyOnWriteArrayList<SidecarEntry>>()

    /** আপলোডের পরে ক্লাউডে যেতে বাকি প্রিভিউ-ফাইল: অপেক্ষায় [waiting], ব্যর্থ [failed] */
    data class Backlog(val waiting: Int, val failed: Int) { val total get() = waiting + failed }

    suspend fun backlog(): Backlog = withContext(Dispatchers.IO) {
        val qs = listOf(
            runCatching { previews.uploadStats() }.getOrDefault(0 to 0),
            runCatching { seeks.uploadStats() }.getOrDefault(0 to 0),
            runCatching { (context.applicationContext as com.drivemount.app.DriveMountApp).zipIndex.uploadStats() }.getOrDefault(0 to 0),
            runCatching { vmetas.uploadStats() }.getOrDefault(0 to 0),
        )
        Backlog(
            waiting = pending.values.sumOf { it.size } + qs.sumOf { it.first },
            failed = packFailed.values.sumOf { it.size } + qs.sumOf { it.second },
        )
    }

    /** ব্যর্থ প্রিভিউ-আপলোড আবার চালানো (অ্যাপের নিজস্ব স্কোপে, তাই স্ক্রিন ছাড়লেও চলে) */
    fun retryFailed() {
        scope.launch {
            for (k in packFailed.keys.toList()) {
                val list = packFailed.remove(k) ?: continue
                pending.getOrPut(k) { CopyOnWriteArrayList() }.also { l ->
                    l.removeAll { e -> list.any { it.name == e.name } }
                }.addAll(list)
                failures.remove(k)
            }
            runCatching { previews.resetFailedUploads() }
            runCatching { seeks.resetFailedUploads() }
            runCatching { (context.applicationContext as com.drivemount.app.DriveMountApp).zipIndex.resetFailedUploads() }
            runCatching { vmetas.resetFailedUploads() }
            var waited = 0L
            while (flushing.get() && waited < 60_000L) { kotlinx.coroutines.delay(500); waited += 500 }
            flushPending()
        }
    }

    private val flushing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val failures = ConcurrentHashMap<String, Int>()
    @Volatile private var lastFlush = 0L
    @Volatile private var recovered = false

    /**
     * ফাইল কপি/সরানো/নাম বদলের পর: পুরোনো পথের থাম্বনেল/আইকন/কভার/প্রিভিউ নতুন পথের জন্য বসানো
     * (নতুন ফোল্ডারের ক্লাউড-প্যাকেও লেখার জন্য লাইনে)। না থাকলে কিছুই করে না।
     */
    suspend fun migrate(srcRemote: String, dstRemote: String, dstFs: String, oldPath: String, newPath: String, size: Long) {
        val oldName = oldPath.substringAfterLast('/')
        val newName = newPath.substringAfterLast('/')
        runCatching { dates.migrate(srcRemote, dstRemote, dstFs, oldPath, newPath, size) } // যেকোনো ধরনের ফাইল: দুটি তারিখ সঙ্গে যায়
        if (!supports(oldName) || nsOf(oldName) != nsOf(newName)) return
        withContext(Dispatchers.IO) {
            val blob = cache.get(nsOf(oldName), key(srcRemote, oldPath, size))
            val entry = blob?.let { runCatching { SidecarCodec.decodeOne(it) }.getOrNull() }
            if (entry != null) commit(dstFs, newPath, entry.copy(name = newName, preview = null))
            if (ThumbMetaReader.isImage(oldName)) previews.migrate(srcRemote, dstRemote, dstFs, oldPath, newPath, size)
            else if (ThumbMetaReader.isMedia(oldName)) {
                if (srcRemote == dstRemote) vmetas.moveFast(dstFs, oldPath, newPath, size)
                previews.migrate(srcRemote, dstRemote, dstFs, oldPath, newPath, size)
                seeks.migrate(srcRemote, dstRemote, dstFs, oldPath, newPath, size)
                vmetas.migrate(srcRemote, dstRemote, dstFs, oldPath, newPath, size)
            }
        }
    }

    private suspend fun mergeIntoRemotePack(fs: String, dir: String, pack: String, items: List<SidecarEntry>) {
        val tmp = newTmpDir()
        try {
            val remotePack = joinPath(joinPath(dir, META_DIR), pack)
            val merged = LinkedHashMap<String, SidecarEntry>()
            // আগের প্যাক (না থাকলে খালি)
            runCatching {
                api.transferFile(fs, remotePack, tmp.absolutePath, pack, false)
                SidecarCodec.decodePack(File(tmp, pack).readBytes())
            }.getOrNull()?.forEach { merged[it.name] = it }
            for (e in items) {
                merged.remove(e.name)
                merged[e.name] = e
            }
            while (merged.size > capOf(pack)) merged.remove(merged.keys.first())
            File(tmp, "new.dmp").writeBytes(SidecarCodec.encodePack(merged.values))
            api.transferFile(tmp.absolutePath, "new.dmp", fs, remotePack, false)
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ---------------- ফোল্ডার খোলার সময় ----------------

    /**
     * ফোল্ডারে `.dmmeta` থাকলে এবং কিছু APK-র আইকন লোকালে না থাকলে, পেছনে প্যাকটি একবার এনে লোকালে বসায়।
     * একই ফোল্ডারের জন্য ৫ মিনিটে একবারের বেশি নয়, আর একসঙ্গে একটার বেশি নয়।
     */
    fun onFolderListed(remote: String, fs: String, folder: String, list: List<RemoteEntry>) {
        // সব কাজ (ফিল্টার, ডিস্ক-চেক) পেছনের থ্রেডে: ডাকা হয় UI-র কাছের থ্রেড থেকে
        scope.launch {
            // আগের ব্যর্থ প্যাক-আপলোড থাকলে (১ মিনিটে একবার) আবার চেষ্টা
            // (Process Kill-এর পর ডিস্কে থেকে যাওয়া সাইডকার-আপলোডও প্রথমবার এখানে ফেরে)
            if ((pending.isNotEmpty() || !recovered) && SystemClock.elapsedRealtime() - lastFlush > 60_000L) { recovered = true; flushPending() }
            for (pack in ALL_PACKS) {
                val ns = nsOfPack(pack)
                val files = list.filter { !it.isDir && packOf(it.name) == pack && supports(it.name) }
                if (files.isEmpty()) continue
                if (files.all { cache.has(ns, key(remote, it.path, it.size ?: -1L)) }) continue
                val importKey = "$remote|$folder|$pack"
                val now = SystemClock.elapsedRealtime()
                val last = lastImport[importKey]
                if (last != null && now - last < 5 * 60_000L) continue
                lastImport[importKey] = now
                gate.withPermit { runCatching { importPack(remote, fs, folder, pack, ns, files) } }
            }
        }
    }

    private suspend fun importPack(remote: String, fs: String, folder: String, pack: String, ns: String, files: List<RemoteEntry>) {
        val tmp = newTmpDir()
        try {
            api.transferFile(fs, joinPath(joinPath(folder, META_DIR), pack), tmp.absolutePath, pack, false)
            val byName = SidecarCodec.decodePack(File(tmp, pack).readBytes()).associateBy { it.name }
            var added = 0
            for (f in files) {
                val e = byName[f.name] ?: continue
                val size = f.size ?: -1L
                if (e.size != size) continue // একই নামে অন্য ফাইল বসে গেছে
                cache.put(ns, key(remote, f.path, size), SidecarCodec.encodeOne(e))
                added++
            }
            // নাম বদলানো ফাইল: নামে মেলেনি, কিন্তু একই ফোল্ডারে আকার-অনুযায়ী ঠিক একটি করে অমিল ফাইল ও অমিল এন্ট্রি থাকলে সেটি ধরা
            val names = files.map { it.name }.toSet()
            val orphans = byName.values.filter { it.name !in names && it.size > 0 }.groupBy { it.size }
            val unmatched = files.filter { byName[it.name] == null && (it.size ?: -1L) > 0 }.groupBy { it.size ?: -1L }
            for ((size, fl) in unmatched) {
                val ol = orphans[size] ?: continue
                if (fl.size == 1 && ol.size == 1) {
                    cache.put(ns, key(remote, fl[0].path, size), SidecarCodec.encodeOne(ol[0].copy(name = fl[0].name)))
                    added++
                }
            }
            if (added > 0) version.intValue++
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ---------------- backfill-এর জন্য ----------------

    /** থাম্বনেল/আইকন আছে এবং (বড় ছবির) প্রিভিউও আছে কি: backfill শুধু অসম্পূর্ণগুলোতে কাজ করে */
    fun isComplete(remote: String, path: String, size: Long, name: String): Boolean =
        hasEntry(remote, path, size, name) &&
            (!ThumbMetaReader.isImage(name) || !previews.needsPreview(size) || previews.has(remote, path, size))

    /** এই ফাইলের সাইডকার লোকালে আছে কি */
    fun hasEntry(remote: String, path: String, size: Long, name: String): Boolean =
        cache.has(nsOf(name), key(remote, path, size))

    /** ফোল্ডারের ক্লাউড-প্যাক এখনই (থ্রটল ছাড়া) আমদানি: যেগুলো আগেই ক্লাউডে আছে সেগুলো আবার বানানো লাগে না */
    suspend fun importNow(remote: String, fs: String, folder: String, list: List<RemoteEntry>) {
        for (pack in ALL_PACKS) {
            val ns = nsOfPack(pack)
            val files = list.filter { !it.isDir && supports(it.name) && packOf(it.name) == pack }
            if (files.isEmpty() || files.all { cache.has(ns, key(remote, it.path, it.size ?: -1L)) }) continue
            gate.withPermit { runCatching { importPack(remote, fs, folder, pack, ns, files) } }
        }
    }

    /**
     * Photos/Videos/Music ট্যাবের পুরো (recursive) তালিকা থেকে: যেসব ফোল্ডারে সাইডকার প্যাক আছে এবং
     * লোকালে তথ্য নেই, সেগুলোর প্যাক আমদানি। প্যাক আছে কি না তালিকা থেকেই জানা যায়, তাই বাড়তি ব্যর্থ রিকোয়েস্ট নেই।
     */
    suspend fun importFromListing(remote: String, fs: String, all: List<RemoteEntry>, maxFolders: Int = 150): Int {
        val meta = all.asSequence().filter { !it.isDir && (it.path.startsWith("$META_DIR/") || it.path.contains("/$META_DIR/")) }
            .map { it.path }.toHashSet()
        if (meta.isEmpty()) return 0
        val byDir = all.filter { !it.isDir && supports(it.name) && !it.path.startsWith("$META_DIR/") && !it.path.contains("/$META_DIR/") }
            .groupBy { it.path.substringBeforeLast('/', "") }
        var folders = 0
        for ((dir, files) in byDir) {
            for (pack in ALL_PACKS) {
                if (joinPath(joinPath(dir, META_DIR), pack) !in meta) continue
                val ns = nsOfPack(pack)
                val mine = files.filter { packOf(it.name) == pack }
                if (mine.isEmpty() || mine.all { cache.has(ns, key(remote, it.path, it.size ?: -1L)) }) continue
                gate.withPermit { runCatching { importPack(remote, fs, dir, pack, ns, mine) } }
            }
            if (++folders >= maxFolders) return folders // একবারে সীমিত; পরের রিফ্রেশে বাকিগুলো
        }
        return folders
    }

    /** ফাইল মোছার পর তার মাঝারি প্রিভিউ ক্লাউড থেকেও সরানো, যাতে ব্যবহারকারীর কাছে একটিই ছবি মনে হয় */
    suspend fun removePreview(fs: String, path: String, size: Long) {
        val name = path.substringAfterLast('/')
        if (ThumbMetaReader.isImage(name)) {
            if (previews.needsPreview(size)) runCatching { api.deleteFile(fs, previews.remotePath(path, size)) }
        } else if (ThumbMetaReader.isMedia(name)) {
            runCatching { api.deleteFile(fs, previews.remotePath(path, size)) } // ভিডিও: মাঝারি থাম্বনেল/পোস্টার
            seeks.deleteRemote(fs, path, size) // ভিডিও: সিকবার-শিট
            vmetas.deleteRemote(fs, path, size) // ভিডিও: মেটাডেটা
            vmetas.deleteFast(fs, path, size) // ভিডিও: fast-start কপি (শুধু আমাদের বানানো; মূল নয়)
        }
    }

    // ---------------- অডিও ----------------

    /** গানের তথ্য (ট্যাগ); ছবি ছাড়া হালকা। কোনো নেটওয়ার্ক কল নেই: লোকাল সাইডকার থেকে */
    class AudioInfo(val title: String, val artist: String, val album: String, val hasArt: Boolean)

    private val audioText = ConcurrentHashMap<String, AudioInfo>()

    suspend fun audioInfo(remote: String, path: String, size: Long): AudioInfo? {
        val k = key(remote, path, size)
        audioText[k]?.let { return it }
        return withContext(Dispatchers.IO) {
            val blob = cache.get(NS_AUDIO, k) ?: return@withContext null
            val e = runCatching { SidecarCodec.decodeOne(blob) }.getOrNull() ?: return@withContext null
            AudioInfo(e.label, e.pkg, e.version, e.icon.isNotEmpty()).also { audioText[k] = it }
        }
    }

    private val localTried = ConcurrentHashMap.newKeySet<String>()

    /** লোকাল ফাইলের ট্যাগ ও কভার ফাইল থেকেই পড়ে ফোনের ক্যাশে রাখে (নেটওয়ার্ক/ক্লাউড লেখা নেই); প্রতি ফাইলে একবার */
    suspend fun ensureLocalAudio(remote: String, path: String, size: Long) {
        val k = key(remote, path, size)
        if (audioText.containsKey(k) || !localTried.add(k)) return
        withContext(Dispatchers.IO) {
            if (cache.get(NS_AUDIO, k) != null) return@withContext
            val f = File("/" + path.trimStart('/'))
            val name = path.substringAfterLast('/')
            // সরাসরি ফাইল পড়া না গেলে (স্টোরেজ অনুমতি ইত্যাদি) প্লেয়ারের মতোই স্ট্রিম URL দিয়ে
            var entry = if (f.canRead()) AudioMetaReader.read(f, name) else null
            if (entry == null) {
                val url = runCatching { api.streamUrl(com.drivemount.app.engine.fsRootFor(remote, "local"), path, com.drivemount.app.engine.guessMime(name, ""), background = true) }.getOrNull()
                    ?: return@withContext
                entry = AudioMetaReader.readUrl(url, name, size) ?: return@withContext
            }
            cache.put(NS_AUDIO, k, SidecarCodec.encodeOne(entry.copy(name = name)))
        }
    }

    fun cachedAudioArt(remote: String, path: String, size: Long): Bitmap? = thumbs.get("a|" + key(remote, path, size))

    suspend fun audioArt(remote: String, path: String, size: Long): Bitmap? {
        val k = key(remote, path, size)
        thumbs.get("a|$k")?.let { return it }
        return withContext(Dispatchers.IO) {
            val blob = cache.get(NS_AUDIO, k) ?: return@withContext null
            val e = runCatching { SidecarCodec.decodeOne(blob) }.getOrNull() ?: return@withContext null
            if (e.icon.isEmpty()) return@withContext null
            val bmp = BitmapFactory.decodeByteArray(e.icon, 0, e.icon.size) ?: return@withContext null
            thumbs.put("a|$k", bmp)
            bmp
        }
    }

    /** গান না নামিয়ে স্ট্রিম URL-এর শুরুর অংশ থেকে ট্যাগ ও কভার নিয়ে রাখা */
    suspend fun commitAudioFromUrl(fs: String, path: String, size: Long, url: String) {
        val name = path.substringAfterLast('/')
        val entry = withContext(Dispatchers.IO) { AudioMetaReader.readUrl(url, name, size) } ?: return
        commit(fs, path, entry)
    }

    /**
     * গানের ট্যাগ ও কভার শুধু এই ফোনের ক্যাশে রাখা (ক্লাউডে কিছু লেখা হয় না, তাই ব্যবহারকারীর অনুমতি লাগে না)।
     * ব্রাউজে সাইডকার-ছাড়া গানের কভার দেখাতে ব্যবহৃত; শুরুর ছোট অংশ পড়ে।
     */
    suspend fun cacheAudioLocally(remote: String, path: String, size: Long, url: String) {
        val name = path.substringAfterLast('/')
        val entry = withContext(Dispatchers.IO) { AudioMetaReader.readUrl(url, name, size) } ?: return
        withContext(Dispatchers.IO) {
            cache.put(NS_AUDIO, key(remote, path, size), SidecarCodec.encodeOne(entry.copy(name = name)))
        }
        version.intValue++
    }

    /** ভিডিও না নামিয়ে স্ট্রিম URL থেকে একটি ফ্রেম নিয়ে প্রিভিউ বানিয়ে রাখা */
    suspend fun commitVideoFromUrl(fs: String, path: String, size: Long, url: String) {
        val name = path.substringAfterLast('/')
        val entry = withContext(Dispatchers.IO) { ThumbMetaReader.readVideoUrl(url, name, size) } ?: return
        commit(fs, path, entry)
    }

    /**
     * Backfill: পুরোনো ভিডিওর সিকবার-শিট। ক্লাউডে আগে থেকে থাকলে (অন্য ফোনের/আপলোডের তৈরি) ছোট ফাইলটা আনে;
     * নইলে স্ট্রিম URL থেকে বানিয়ে ফোনে রেখে ক্লাউডে তোলে। ফল: 1 = শিট হয়েছে, 0 = লাগবে না (ছোট ভিডিও), -1 = ব্যর্থ।
     */
    suspend fun backfillSeek(fs: String, path: String, size: Long, url: String, cloudHas: Boolean): Int {
        val remote = fs.removeSuffix(":")
        if (cloudHas && seeks.load(remote, fs, path, size) != null) return 1
        val r = withContext(Dispatchers.IO) { ThumbMetaReader.seekSheetFromUrl(url) }
        val bytes = r.bytes
        return when {
            bytes != null -> {
                seeks.store(remote, path, size, bytes)
                seeks.enqueueUpload(fs, path, size, bytes)
                1
            }
            r.permanent -> { seeks.markNone(remote, path, size); 0 }
            else -> -1
        }
    }

    /** লোকাল ফাইলের ভিডিও থাম্বনেল ও সিকবার-শিট শুধু ফোনের ক্যাশে রাখা (কোথাও কিছু আপলোড হয় না); 1 = ঠিক, -1 = ব্যর্থ */
    suspend fun prepareLocalVideo(remote: String, path: String, size: Long, url: String, needThumb: Boolean, needSeek: Boolean): Int {
        val name = path.substringAfterLast('/')
        if (needThumb) {
            val entry = withContext(Dispatchers.IO) { ThumbMetaReader.readVideoUrl(url, name, size) } ?: return -1
            withContext(Dispatchers.IO) { cache.put(nsOf(name), key(remote, path, size), SidecarCodec.encodeOne(entry.copy(name = name))) }
        }
        if (needSeek) {
            val r = withContext(Dispatchers.IO) { ThumbMetaReader.seekSheetFromUrl(url) }
            val b = r.bytes
            when {
                b != null -> seeks.store(remote, path, size, b)
                r.permanent -> seeks.markNone(remote, path, size)
                else -> return -1
            }
        }
        version.intValue++
        return 1
    }

    /** ক্লাউডে মুছে/বদলে যাওয়া ফাইলের লোকাল থাম্বনেল, আইকন, প্রিভিউ, সিকবার ও মেটাডেটা সরানো (ক্লাউডে কিছু লেখা হয় না) */
    fun purgeLocal(remote: String, path: String, size: Long) {
        val k = key(remote, path, size)
        cache.remove(NS, k); cache.remove(NS_THUMB, k); cache.remove(NS_AUDIO, k)
        fastChoice.remove(k); icons.remove(k); thumbs.remove(k); thumbs.remove("a|$k"); audioText.remove(k)
        previews.removeLocal(remote, path, size)
        seeks.removeLocal(remote, path, size)
        vmetas.removeLocal(remote, path, size)
    }

    /** অন্য ডিভাইসের আপলোড করা ভিডিও-মেটাডেটা (ছোট ফাইল) ফোনে আনা; আগেই থাকলে কিছু করে না */
    suspend fun syncVideoInfo(remote: String, fs: String, path: String, size: Long) {
        if (!vmetas.has(remote, path, size)) vmetas.load(remote, fs, path, size)
    }

    private val fastChoice = ConcurrentHashMap<String, String>()

    /**
     * স্ট্রিমের জন্য কোন ক্লাউড-পথ: fast-start কপি থাকলে (ও আকার মিললে) সেটি, নইলে মূল ফাইল।
     * কপি না পেলে বা কোনো সমস্যা হলে সবসময় মূল ফাইলে ফিরে যায়।
     */
    suspend fun fastStartPath(remote: String, fs: String, path: String, size: Long): String {
        if (!FastStart.isCandidate(path) || size <= 0 || !fs.endsWith(":") || fs.startsWith("/")) return path
        val k = key(remote, path, size)
        fastChoice[k]?.let { return it }
        val info = runCatching { vmetas.load(remote, fs, path, size) }.getOrNull() ?: return path
        if (!info.fast) { fastChoice[k] = path; return path }
        val fp = vmetas.fastRemotePath(path, size)
        val ok = runCatching { api.stat(fs, fp)?.size == size }.getOrDefault(false)
        if (!ok) return path // আপলোড এখনো শেষ হয়নি হতে পারে: পরে আবার দেখা হবে
        fastChoice[k] = fp
        return fp
    }

    fun notifyChanged() { version.intValue++ }

    /** ভিডিওর সংরক্ষিত মেটাডেটা (লোকাল, না থাকলে ক্লাউড থেকে) */
    suspend fun videoInfo(remote: String, fs: String, path: String, size: Long) = vmetas.load(remote, fs, path, size)

    private fun newTmpDir(): File =
        File(context.cacheDir, "sidecar/${System.nanoTime()}").apply { mkdirs() }
}

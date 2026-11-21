package com.drivemount.app.data.meta

import android.content.Context
import com.drivemount.app.engine.RcloneApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * সাইডকার ফাইলের (প্রিভিউ/সিকবার/মেটাডেটা) আপলোড-লাইন, তিনটি ক্লাসে সমান।
 *  - ডিস্কে স্থায়ী: অ্যাপ বন্ধ/Process Kill হলেও পরের বার `restore()` লাইন ফিরিয়ে আনে
 *  - ডুপ্লিকেট প্রতিরোধ: একই ক্লাউড-পথের জন্য একটিই এন্ট্রি (পরেরটি আগেরটিকে বদলায়)
 *  - যাচাই: আপলোডের পর ক্লাউডে ফাইলের আকার মিলিয়ে দেখা; না মিললে আবার চেষ্টা
 */
class SidecarUploadQueue(
    private val context: Context,
    private val api: RcloneApi,
    private val dirName: String,
) {
    private class Item(val fs: String, val remotePath: String, val file: File, val meta: File) { var attempts = 0 }

    private val items = ConcurrentHashMap<String, Item>()
    @Volatile private var restored = false

    /** আপলোডের অপেক্ষায় (এখনো চেষ্টার সুযোগ আছে) */
    fun waiting(): Int { restore(); return items.values.count { it.attempts < MAX_ATTEMPTS } }

    /** ৩ বার চেষ্টা করেও ব্যর্থ: ফাইল ডিস্কে আছে, [resetFailed] দিলে আবার চেষ্টা হবে */
    fun failed(): Int { restore(); return items.values.count { it.attempts >= MAX_ATTEMPTS } }

    /** ব্যর্থ এন্ট্রিগুলোকে আবার চেষ্টার যোগ্য করা (এরপর [flush] ডাকতে হবে) */
    fun resetFailed() { restore(); items.values.forEach { if (it.attempts >= MAX_ATTEMPTS) it.attempts = 0 } }
    private val dir get() = File(context.cacheDir, dirName)

    private fun idOf(fs: String, remotePath: String) =
        MessageDigest.getInstance("SHA-1").digest("$fs|$remotePath".toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    fun enqueue(fs: String, remotePath: String, bytes: ByteArray) {
        runCatching {
            dir.mkdirs()
            val id = idOf(fs, remotePath)
            val tmp = File(dir, "$id.tmp")
            tmp.writeBytes(bytes)
            val file = File(dir, "$id.bin")
            tmp.renameTo(file) // একই id = একই ফাইল: ডুপ্লিকেট এন্ট্রি হয় না
            val meta = File(dir, "$id.meta")
            meta.writeText("$fs\n$remotePath")
            items[id] = Item(fs, remotePath, file, meta)
        }
    }

    /** বড় ফাইল (fast-start কপি): [src] সরিয়ে (নইলে কপি করে) লাইনে রাখা; [src] এরপর লাইনের মালিকানায় */
    fun enqueueFile(fs: String, remotePath: String, src: File) {
        runCatching {
            dir.mkdirs()
            val id = idOf(fs, remotePath)
            val file = File(dir, "$id.bin")
            if (!src.renameTo(file)) { src.copyTo(file, overwrite = true); src.delete() }
            val meta = File(dir, "$id.meta")
            meta.writeText("$fs\n$remotePath")
            items[id] = Item(fs, remotePath, file, meta)
        }
    }

    /** আগের Process-এ লাইনে থাকা কিন্তু শেষ না হওয়া আপলোড ফেরানো; ৭ দিনের পুরোনো ফেলে দেওয়া */
    private fun restore() {
        if (restored) return
        restored = true
        val limit = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        dir.listFiles()?.forEach { f ->
            if (f.extension == "tmp") { f.delete(); return@forEach }
            if (f.extension != "meta") return@forEach
            val id = f.nameWithoutExtension
            val bin = File(dir, "$id.bin")
            val lines = runCatching { f.readText().split('\n') }.getOrNull()
            if (!bin.isFile || lines == null || lines.size < 2 || f.lastModified() < limit) {
                bin.delete(); f.delete(); return@forEach
            }
            items.putIfAbsent(id, Item(lines[0], lines[1], bin, f))
        }
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        restore()
        for ((id, up) in items.entries.toList()) {
            if (up.attempts >= MAX_ATTEMPTS) continue // ব্যর্থ হিসেবে চিহ্নিত: ব্যবহারকারী Retry দিলে আবার চলবে
            var done = false
            try {
                api.transferFile(dir.absolutePath, up.file.name, up.fs, up.remotePath, false)
                val remoteSize = runCatching { api.stat(up.fs, up.remotePath)?.size }.getOrNull()
                if (remoteSize != up.file.length()) throw java.io.IOException("verify")
                done = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // ৩ বার পর্যন্ত; এরপর "ব্যর্থ" হয়ে লাইনেই থাকে (ফাইল ডিস্কে), Retry বা পরের Process-এ (restore) আবার চেষ্টা
                up.attempts++
            }
            if (done) {
                items.remove(id, up)
                up.file.delete(); up.meta.delete()
            }
        }
    }

    private companion object { const val MAX_ATTEMPTS = 3 }
}

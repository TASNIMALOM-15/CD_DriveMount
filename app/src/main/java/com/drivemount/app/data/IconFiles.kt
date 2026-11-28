package com.drivemount.app.data

import android.content.Context
import android.net.Uri
import com.drivemount.app.ui.components.FabIcons
import java.io.File

/** ডিভাইস থেকে import করা আইকনের ছবি/SVG ফাইল (filesDir/fab_icons) */
object IconFiles {
    private const val MAX_BYTES = 2L * 1024 * 1024

    /** [uri]-এর ফাইল কপি করে। সফল হলে কী ("file:<নাম>") ফেরত দেয়, নইলে null */
    fun import(context: Context, tag: String, uri: Uri): String? = try {
        val dir = File(context.filesDir, FabIcons.DIR).apply { mkdirs() }
        val name = tag.replace(Regex("[^A-Za-z0-9_]"), "_") + "_" + System.currentTimeMillis()
        val out = File(dir, name)
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(8192)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    total += r
                    if (total > MAX_BYTES) { out.delete(); throw IllegalStateException("too big") }
                    o.write(buf, 0, r)
                }
            }
        } ?: throw IllegalStateException("no stream")
        FabIcons.FILE_PREFIX + name
    } catch (e: Exception) {
        null
    }

    fun delete(context: Context, key: String?) {
        if (key != null && key.startsWith(FabIcons.FILE_PREFIX)) {
            runCatching { File(File(context.filesDir, FabIcons.DIR), key.removePrefix(FabIcons.FILE_PREFIX)).delete() }
        }
    }
}

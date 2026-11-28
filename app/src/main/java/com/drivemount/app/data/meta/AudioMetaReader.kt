package com.drivemount.app.data.meta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * গানের ট্যাগ (নাম, শিল্পী, অ্যালবাম) ও ছোট album art (২০০px WebP) সাইডকারের জন্য।
 * ট্যাগ বা ছবি না থাকলেও একটি খালি এন্ট্রি বানানো হয়, যাতে backfill বারবার একই ফাইল ধরে না।
 * শুধু পেছনের থ্রেডে চলে।
 */
object AudioMetaReader {

    private const val ART_SIDE = 200
    private val EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma")

    fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in EXT

    fun read(file: File, name: String): SidecarEntry? =
        build(name, file.length()) { it.setDataSource(file.path) }

    /** স্ট্রিম URL থেকে (পুরো ফাইল না নামিয়ে, range request) */
    fun readUrl(url: String, name: String, size: Long): SidecarEntry? =
        build(name, size) { it.setDataSource(url, HashMap()) }

    private fun build(name: String, size: Long, source: (MediaMetadataRetriever) -> Unit): SidecarEntry? {
        val r = MediaMetadataRetriever()
        return try {
            source(r)
            fun tag(k: Int) = r.extractMetadata(k)?.trim().orEmpty().take(200)
            val title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifEmpty { tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) }
            val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val art = runCatching { r.embeddedPicture?.let { encodeArt(it) } }.getOrNull() ?: ByteArray(0)
            SidecarEntry(name, size, title, artist, album, art)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun encodeArt(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ART_SIDE) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = ART_SIDE.toFloat() / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        } else src
        val out = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bmp.compress(format, 72, out)
        if (bmp !== src) bmp.recycle()
        src.recycle()
        return out.toByteArray()
    }
}

package com.drivemount.app.data.meta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ছবি/ভিডিওর ছোট প্রিভিউ (সাইডকারের জন্য)। শুধু পেছনের থ্রেডে চলে।
 * ছবি: সরাসরি ছোট করে ডিকোড (পুরো ছবি মেমরিতে আসে না)। ভিডিও: একটি ফ্রেম।
 */
object ThumbMetaReader {

    private const val MAX_SIDE = 320
    private const val PREVIEW_SIDE = 1280
    private const val PREVIEW_MIN_FILE = 250L * 1024
    private const val MAX_IMAGE_FILE = 100L * 1024 * 1024
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts")

    fun isMedia(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT || ext in VIDEO_EXT
    }

    fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT

    fun read(file: File, name: String): SidecarEntry? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return runCatching {
            when (ext) {
                in IMAGE_EXT -> imageEntry(file, name)
                in VIDEO_EXT -> video(file)?.let { SidecarEntry(name, file.length(), "", "", "", it) }
                else -> null
            }
        }.getOrNull()
    }

    /** স্ট্রিম URL থেকে ভিডিওর ফ্রেম (পুরো ফাইল নামে না) */
    fun readVideoUrl(url: String, name: String, size: Long): SidecarEntry? {
        val r = MediaMetadataRetriever()
        val bytes = runCatching {
            r.setDataSource(url, HashMap())
            val bmp = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0L)
            bmp?.let { encode(it) }
        }.getOrNull()
        runCatching { r.release() }
        return bytes?.let { SidecarEntry(name, size, "", "", "", it) }
    }

    /** একবার ডিকোড করে ছোট থাম্বনেল (৩২০px) ও (বড় ছবির ক্ষেত্রে) মাঝারি প্রিভিউ (১২৮০px) দুটোই বানায় */
    private fun imageEntry(file: File, name: String): SidecarEntry? {
        if (file.length() > MAX_IMAGE_FILE) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val big = maxOf(bounds.outWidth, bounds.outHeight)
        val wantPreview = file.length() >= PREVIEW_MIN_FILE && big > PREVIEW_SIDE
        val target = if (wantPreview) PREVIEW_SIDE else MAX_SIDE
        var sample = 1
        while (big / (sample * 2) >= target) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        bmp = oriented(file, bmp)

        val preview = if (wantPreview) scaleAndEncode(bmp, PREVIEW_SIDE, 70) else null
        val thumb = scaleAndEncode(bmp, MAX_SIDE, 72)
        bmp.recycle()
        return SidecarEntry(name, file.length(), "", "", "", thumb, preview)
    }

    /** ক্যামেরার ছবির EXIF ঘোরানো বিবেচনায় না নিলে থাম্বনেল কাত হয়ে যায় */
    @Suppress("DEPRECATION")
    private fun oriented(file: File, src: Bitmap): Bitmap {
        val orientation = runCatching {
            android.media.ExifInterface(file.path)
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val m = android.graphics.Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = runCatching { Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) }.getOrNull() ?: return src
        if (out !== src) src.recycle()
        return out
    }

    private fun scaleAndEncode(src: Bitmap, side: Int, quality: Int): ByteArray {
        val scale = side.toFloat() / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        } else src
        val out = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bmp.compress(format, quality, out)
        if (bmp !== src) bmp.recycle()
        return out.toByteArray()
    }

    private fun video(file: File): ByteArray? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val bmp = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0L) ?: return null
            return encode(bmp)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun encode(src: Bitmap): ByteArray {
        val scale = MAX_SIDE.toFloat() / maxOf(src.width, src.height)
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

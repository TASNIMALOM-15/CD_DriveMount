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
    private const val PREVIEW_MIN_FILE = 300L * 1024
    private const val TINY_SIDE = 160
    private const val VIDEO_MEDIUM_SIDE = 720
    private const val TINY_MAX_BYTES = 10 * 1024 // ছোট থাম্বনেল: ২–১০KB
    private const val MEDIUM_MAX_BYTES = 300 * 1024 // মাঝারি: ১০০–৩০০KB
    private const val MAX_IMAGE_FILE = 100L * 1024 * 1024
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts")

    fun isMedia(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT || ext in VIDEO_EXT
    }

    fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT

    fun read(file: File, name: String, onStage: (Int) -> Unit = {}): SidecarEntry? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return runCatching {
            when (ext) {
                in IMAGE_EXT -> { onStage(com.drivemount.app.data.transfer.TransferStage.THUMB); imageEntry(file, name) }
                in VIDEO_EXT -> run { onStage(com.drivemount.app.data.transfer.TransferStage.THUMB); videoFrame(file) }?.let { f ->
                    val (small, medium) = dual(f)
                    SidecarEntry(name, file.length(), "", "", "", small, medium, seek = run { onStage(com.drivemount.app.data.transfer.TransferStage.SEEK); seekSheet(file) }, vmeta = VideoMetas.extract(file))
                }
                else -> null
            }
        }.getOrNull()
    }

    /** শিট বানানোর ফল: bytes = শিট; permanent = কখনো লাগবে না (ছোট ভিডিও), আবার চেষ্টা বৃথা */
    class SeekBuild(val bytes: ByteArray?, val permanent: Boolean = false)

    /** আপলোডের আগে ফোনের ফাইল থেকে; ১৫ সেকেন্ডের বেশি লাগলে ছেড়ে দেয়, যাতে আপলোড আটকে না যায় */
    private fun seekSheet(file: File): ByteArray? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            buildSeekSheet(r, 15_000L).bytes
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Backfill: স্ট্রিম URL থেকে (পুরো ভিডিও না নামিয়ে, range request); সর্বোচ্চ ২ মিনিট */
    fun seekSheetFromUrl(url: String): SeekBuild {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(url, HashMap())
            buildSeekSheet(r, 120_000L)
        } catch (_: Throwable) {
            SeekBuild(null)
        } finally {
            runCatching { r.release() }
        }
    }

    /** ৩০ সেকেন্ডের বেশি ভিডিওর প্রতি ≈৮ সেকেন্ডে একটি ১৬০×৯০ ফ্রেম (সর্বোচ্চ ৪৮টি) এক ছবিতে */
    private fun buildSeekSheet(r: MediaMetadataRetriever, maxMs: Long): SeekBuild {
        val tw = 160
        val th = 90
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return SeekBuild(null)
        if (dur < 30_000L) return SeekBuild(null, permanent = true)
        val count = (dur / 8_000L).toInt().coerceIn(6, 48)
        val interval = dur / count
        val cols = minOf(count, 8)
        val rows = (count + cols - 1) / cols
        val sheet = Bitmap.createBitmap(cols * tw, rows * th, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(android.graphics.Color.BLACK)
        val started = android.os.SystemClock.elapsedRealtime()
        var got = 0
        for (i in 0 until count) {
            if (android.os.SystemClock.elapsedRealtime() - started > maxMs) { sheet.recycle(); return SeekBuild(null) }
            val us = i * interval * 1000L
            val frame = (if (Build.VERSION.SDK_INT >= 27) {
                r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, tw, th)
            } else {
                r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }) ?: continue
            // ঘরের ভেতরে অনুপাত ঠিক রেখে মাঝখানে বসানো
            val scale = minOf(tw.toFloat() / frame.width, th.toFloat() / frame.height)
            val w = (frame.width * scale).toInt().coerceAtLeast(1)
            val h = (frame.height * scale).toInt().coerceAtLeast(1)
            val left = (i % cols) * tw + (tw - w) / 2
            val top = (i / cols) * th + (th - h) / 2
            canvas.drawBitmap(frame, null, android.graphics.Rect(left, top, left + w, top + h), null)
            frame.recycle()
            got++
        }
        if (got < count / 2) { sheet.recycle(); return SeekBuild(null) } // অর্ধেকের বেশি ফ্রেম না পেলে শিট ঠিক নয়
        val out = ByteArrayOutputStream()
        sheet.compress(if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, 55, out)
        sheet.recycle()
        return SeekBuild(SeekSheets.pack(dur, count, cols, tw, th, out.toByteArray()))
    }

    /** স্ট্রিম URL থেকে ভিডিওর ফ্রেম (পুরো ফাইল নামে না) */
    fun readVideoUrl(url: String, name: String, size: Long): SidecarEntry? {
        val r = MediaMetadataRetriever()
        val pair = runCatching {
            r.setDataSource(url, HashMap())
            bestFrame(r, 8_000L)?.let { dual(it) }
        }.getOrNull()
        runCatching { r.release() }
        return pair?.let { SidecarEntry(name, size, "", "", "", it.first, it.second, vmeta = VideoMetas.extractUrl(url)) }
    }

    /** একবার ডিকোড করে ছোট থাম্বনেল (৩২০px) ও (বড় ছবির ক্ষেত্রে) মাঝারি প্রিভিউ (১২৮০px) দুটোই বানায় */
    private fun imageEntry(file: File, name: String): SidecarEntry? {
        if (file.length() > MAX_IMAGE_FILE) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val big = maxOf(bounds.outWidth, bounds.outHeight)
        val wantPreview = file.length() >= PREVIEW_MIN_FILE
        val target = if (wantPreview) PREVIEW_SIDE else TINY_SIDE
        var sample = 1
        while (big / (sample * 2) >= target) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        bmp = oriented(file, bmp)

        // মাঝারি (দেখে বোঝা যায়): ১২৮০px, ≤৩০০KB; আসলের চেয়ে ছোট না হলে বানানো হয় না
        val preview = if (wantPreview) {
            adaptive(bmp, PREVIEW_SIDE, intArrayOf(78, 68, 58, 48, 38), MEDIUM_MAX_BYTES)
                .takeIf { it.size < file.length() }
        } else null
        // খুব ছোট (২–১০KB): গ্রিডের ছোট ঘর ও ধীর নেটে সঙ্গে সঙ্গে দেখানোর জন্য
        val thumb = adaptive(bmp, TINY_SIDE, intArrayOf(55, 45, 35, 25, 18), TINY_MAX_BYTES)
        bmp.recycle()
        return SidecarEntry(name, file.length(), "", "", "", thumb, preview)
    }

    /** কোয়ালিটি ধাপে ধাপে কমিয়ে maxBytes-এর ভেতরে আনে; তবু বড় থাকলে ছবির মাপও কমায় */
    private fun adaptive(src: Bitmap, side: Int, qualities: IntArray, maxBytes: Int): ByteArray {
        var s = side
        var last = ByteArray(0)
        repeat(3) {
            for (q in qualities) {
                last = scaleAndEncode(src, s, q)
                if (last.size <= maxBytes) return last
            }
            s = (s * 0.8f).toInt().coerceAtLeast(64)
        }
        return last
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

    /** ভিডিওর একটি ফ্রেম (পরে dual() দিয়ে ছোট ১৬০px ও মাঝারি ৭২০px WebP হয়) */
    private fun videoFrame(file: File): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            return bestFrame(r, 4_000L)
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * সেরা ফ্রেম: ভিডিওর ১০%/৩৫%/৬০% সময় থেকে সর্বোচ্চ ৩টি ফ্রেম নিয়ে কালো, অন্ধকার ও ঝাপসা ফ্রেম বাদ;
     * বাকিগুলোর মধ্যে সবচেয়ে ধারালো ও উজ্জ্বলটি। ভালো ফ্রেম পেলেই থামে; [budgetMs] পেরোলে যা আছে তা-ই নেয়।
     */
    private fun bestFrame(r: MediaMetadataRetriever, budgetMs: Long): Bitmap? {
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val times = when {
            dur <= 0L -> listOf(0L)
            dur < 2_000L -> listOf(dur * 500L)
            else -> listOf(0.10, 0.35, 0.60).map { (dur * it).toLong() * 1000L }
        }
        val started = android.os.SystemClock.elapsedRealtime()
        var best: Bitmap? = null
        var bestScore = -1f
        for ((i, t) in times.withIndex()) {
            if (i > 0 && best != null && android.os.SystemClock.elapsedRealtime() - started > budgetMs) break
            val f = runCatching { r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull() ?: continue
            val (mean, sharp) = frameStats(f)
            val score = if (mean < 18f) 0f else sharp * minOf(1f, mean / 40f) // কালো = ০, অন্ধকার = কম নম্বর
            if (score > bestScore) { best?.recycle(); best = f; bestScore = score } else f.recycle()
            if (mean >= 40f && sharp >= 4f) break // যথেষ্ট ভালো: আর খোঁজার দরকার নেই
        }
        return best ?: runCatching { r.getFrameAtTime(0L) }.getOrNull()
    }

    /** ছোট (৪৮px) করে (গড় উজ্জ্বলতা, ধারালো-ভাব = পাশের পিক্সেলের গড় পার্থক্য); সস্তা হিসাব */
    private fun frameStats(src: Bitmap): Pair<Float, Float> {
        val w = 48
        val h = (w * src.height / src.width.coerceAtLeast(1)).coerceIn(8, 96)
        val s = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        if (s !== src) s.recycle()
        val l = IntArray(px.size) { val c = px[it]; (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000 }
        var sum = 0L
        var grad = 0L
        var n = 0
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val v = l[y * w + x]
            sum += v
            grad += Math.abs(v - l[y * w + x + 1]) + Math.abs(v - l[(y + 1) * w + x])
            n++
        }
        return if (n == 0) 0f to 0f else (sum.toFloat() / n) to (grad.toFloat() / (2 * n))
    }

    /** একটি ফ্রেম থেকে দুটি WebP: (ছোট ≈১৬০px গ্রিড/লিস্টের জন্য, মাঝারি ≈৭২০px প্রিভিউ/অফলাইন পোস্টারের জন্য); ফ্রেম রিসাইকেল হয় */
    private fun dual(src: Bitmap): Pair<ByteArray, ByteArray> {
        val small = adaptive(src, TINY_SIDE, intArrayOf(55, 45, 35, 25, 18), TINY_MAX_BYTES)
        val medium = adaptive(src, VIDEO_MEDIUM_SIDE, intArrayOf(70, 60, 50, 40), MEDIUM_MAX_BYTES)
        src.recycle()
        return small to medium
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

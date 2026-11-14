package com.drivemount.app.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** গানের ফাইলের ভেতরের তথ্য: নাম, শিল্পী, অ্যালবাম ও album art (থাকলে) */
class AudioMeta(
    val title: String?,
    val artist: String?,
    val album: String?,
    /** album art (সর্বোচ্চ ~১২০০px) */
    val art: Bitmap?,
    /** art-এর খুব ছোট রূপ; বড় করে আঁকলে মসৃণ ঝাপসা ব্যাকগ্রাউন্ড হয় */
    val blurred: Bitmap?,
)

object AudioMetaLoader {
    private val cache = LruCache<String, AudioMeta>(8)
    private val none = AudioMeta(null, null, null, null, null)
    private val gate = Semaphore(1)

    fun cached(key: String): AudioMeta? = cache.get(key)

    /** ফাইলটি পুরো না নামিয়ে (range request) ট্যাগ ও embedded ছবি পড়ে। না পারলে খালি ফল (বারবার চেষ্টা হয় না) */
    suspend fun load(key: String, url: String): AudioMeta {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cache.get(key)?.let { return@withPermit it }
                val meta = runCatching {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(url, HashMap())
                        val art = r.embeddedPicture?.let { decode(it, 1200) }
                        AudioMeta(
                            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                            art = art,
                            blurred = art?.let { tiny(it) },
                        )
                    } finally {
                        runCatching { r.release() }
                    }
                }.getOrDefault(none)
                cache.put(key, meta)
                meta
            }
        }
    }

    private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun tiny(src: Bitmap): Bitmap {
        val w = 32
        val h = (w * src.height / src.width.coerceAtLeast(1)).coerceIn(8, 64)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}

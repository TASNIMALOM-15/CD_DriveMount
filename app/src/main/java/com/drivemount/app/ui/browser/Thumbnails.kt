package com.drivemount.app.ui.browser

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play as LuPlay
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** থাম্বনেল বানানো যায় এমন ফাইলের ধরন */
enum class ThumbKind { IMAGE, VIDEO }

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

fun thumbKindOf(entry: RemoteEntry): ThumbKind? {
    if (entry.isDir) return null
    val mime = guessMime(entry.name, entry.mimeType)
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("video/") -> ThumbKind.VIDEO
        mime.startsWith("image/") && ext in IMAGE_EXT -> {
            // খুব বড় ছবি থাম্বনেলের জন্য নামানো হয় না
            if ((entry.size ?: 0L) > MAX_IMAGE_BYTES) null else ThumbKind.IMAGE
        }
        else -> null
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val decodeDispatcher = Dispatchers.Default.limitedParallelism(4)

/** একবার তৈরি হওয়া থাম্বনেল-লিংক মনে রাখা: স্ক্রলে ফিরে এলে আবার হিসাব/সার্ভার-কল লাগে না */
private val sourceCache = ConcurrentHashMap<String, ThumbSource>()

/** Coil-এর ImageLoader (ছবির জন্য): memory + disk cache */
private object ThumbLoader {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache { MemoryCache.Builder(context.applicationContext).maxSizePercent(0.2).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.applicationContext.cacheDir, "thumbs_img"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            // ৭ কলামে একসাথে ৮০+ থাম্বনেল দেখা যায়; সবগুলো একসাথে নামালে/ডিকোড করলে CPU ও লোকাল সার্ভার আটকে যায়
            .okHttpClient {
                OkHttpClient.Builder()
                    .dispatcher(Dispatcher().apply { maxRequests = 8; maxRequestsPerHost = 8 })
                    .build()
            }
            .decoderDispatcher(decodeDispatcher)
            .crossfade(120)
            .build()
            .also { loader = it }
    }
}

/** ভিডিওর একটি frame: ফাইলটি পুরো না নামিয়ে (range request) MediaMetadataRetriever দিয়ে নেওয়া */
private object VideoThumbs {
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(6)

    private fun diskFile(context: Context, key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "thumbs_video").apply { mkdirs() }, "$hash.jpg")
    }

    /** persist = false হলে (এনক্রিপ্টেড ভল্ট) কিছুই ডিস্কে লেখা হয় না */
    suspend fun load(context: Context, key: String, source: ThumbSource, persist: Boolean): Bitmap? {
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            if (persist) {
                val f = diskFile(context, key)
                if (f.exists()) {
                    android.graphics.BitmapFactory.decodeFile(f.path)?.let { memory.put(key, it); return@withContext it }
                }
            }
            gate.withPermit {
                val bmp = runCatching {
                    val r = MediaMetadataRetriever()
                    try {
                        when (source) {
                            is ThumbSource.LocalFile -> r.setDataSource(source.file.path)
                            is ThumbSource.Url -> r.setDataSource(source.url, HashMap())
                        }
                        val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        if (persist && durMs > 0) VideoMeta.putDuration(context, key, durMs)
                        // ছোট সাইজে ডিকোড (API 27+): দ্রুত ও কম মেমরি
                        fun grab(us: Long): Bitmap? =
                            if (android.os.Build.VERSION.SDK_INT >= 27) {
                                r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
                            } else {
                                r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            }
                        var frame = grab(1_000_000) ?: grab(0)
                        // প্রথম ফ্রেম কালো হলে ভিডিওর ভেতরের অন্য জায়গা থেকে নেওয়া
                        if (frame != null && durMs > 0 && isMostlyBlack(frame)) {
                            for (frac in listOf(0.1, 0.25)) {
                                val alt = grab((durMs * 1000 * frac).toLong())
                                if (alt != null && !isMostlyBlack(alt)) {
                                    frame = alt
                                    break
                                }
                            }
                        }
                        frame?.let { scaleDown(it, 480) }
                    } finally {
                        runCatching { r.release() }
                    }
                }.getOrNull()
                if (bmp != null) {
                    memory.put(key, bmp)
                    if (persist) {
                        runCatching {
                            FileOutputStream(diskFile(context, key)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                        }
                    }
                }
                bmp
            }
        }
    }

    private fun isMostlyBlack(b: Bitmap): Boolean {
        val t = Bitmap.createScaledBitmap(b, 8, 8, true)
        var sum = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val c = t.getPixel(x, y)
            sum += (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / 3
        }
        return sum / 64 < 12
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(src.width, src.height)
        if (side <= maxSide) return src
        val ratio = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt().coerceAtLeast(1), (src.height * ratio).toInt().coerceAtLeast(1), true)
    }
}

sealed interface ThumbSource {
    data class LocalFile(val file: File) : ThumbSource
    data class Url(val url: String) : ThumbSource
}

/**
 * ছবি বা ভিডিওর থাম্বনেল। তৈরি না হওয়া পর্যন্ত বা ব্যর্থ হলে fallback (সাধারণ আইকন) দেখায়।
 * এনক্রিপ্টেড ভল্টের ফাইলের থাম্বনেল কখনও ডিস্কে জমা হয় না, শুধু মেমরিতে থাকে।
 */
@Composable
fun FileThumbnail(
    account: AccountEntity,
    entry: RemoteEntry,
    kind: ThumbKind,
    size: Dp,
    modifier: Modifier = Modifier,
    aspect: Float? = null,
    showBadge: Boolean = true,
    /** থাম্বনেলের ডিকোড-সাইজ (px); ছোট ঘরে (৬/৭ কলাম) ছোট সাইজ দিলে স্ক্রল মসৃণ হয় */
    thumbPx: Int? = null,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DriveMountApp
    val persist = account.backendType != "crypt"
    val key = "${account.remoteName}|${entry.path}|${entry.modTimeMillis}|${entry.size}"
    var failed by remember(key) { mutableStateOf(false) }

    val source by produceState<ThumbSource?>(sourceCache[key], key) {
        if (value != null) return@produceState
        value = runCatching {
            if (account.backendType == "local") {
                ThumbSource.LocalFile(File("/" + entry.path))
            } else {
                ThumbSource.Url(
                    app.rcloneApi.streamUrl(
                        fsRootFor(account.remoteName, account.backendType),
                        entry.path,
                        guessMime(entry.name, entry.mimeType),
                    ),
                )
            }
        }.getOrNull().also { if (it == null) failed = true else sourceCache[key] = it }
    }

    // Mega/Drive/pCloud: সার্ভারের তৈরি থাম্বনেল আগে চেষ্টা; না পেলে নিচের আগের পদ্ধতি
    val isMega = hasServerThumbs(account.backendType)
    var megaDone by remember(key) { mutableStateOf(!isMega) }
    val megaBitmap by produceState<Bitmap?>(null, key) {
        if (isMega) {
            value = ServerThumbs.thumbnail(context, account, entry, key)
            megaDone = true
        }
    }

    val videoBitmap by produceState<Bitmap?>(null, key, source, megaDone, megaBitmap) {
        val s = source
        if (megaBitmap != null) {
            value = megaBitmap
        } else if (megaDone && kind == ThumbKind.VIDEO && s != null) {
            value = VideoThumbs.load(context, key, s, persist)
            if (value == null) failed = true
        }
    }

    Box(
        modifier = modifier.then(if (aspect != null) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.size(size)),
        contentAlignment = Alignment.Center,
    ) {
        val s = source
        when {
            failed -> fallback()
            videoBitmap != null -> Image(
                bitmap = videoBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            kind == ThumbKind.IMAGE && s != null && megaDone -> {
                val px = thumbPx ?: if (aspect != null) 360 else with(androidx.compose.ui.platform.LocalDensity.current) { (size * 2).roundToPx().coerceAtLeast(64) }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(
                            when (s) {
                                is ThumbSource.LocalFile -> s.file
                                is ThumbSource.Url -> s.url
                            },
                        )
                        .size(px)
                        .memoryCacheKey(if (thumbPx != null) "$key|$px" else key)
                        .apply {
                            if (persist) diskCacheKey(key) else diskCachePolicy(CachePolicy.DISABLED)
                        }
                        .build(),
                    imageLoader = ThumbLoader.get(context),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.matchParentSize(),
                )
            }
            else -> fallback()
        }
        // ভিডিও চেনাতে ছোট play চিহ্ন
        if (showBadge && kind == ThumbKind.VIDEO && !failed && videoBitmap != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.LuPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * ব্যাকগ্রাউন্ডে আগেভাগে থাম্বনেল বানিয়ে ডিস্কে রাখা (Videos ট্যাব খুললে চালু হয়)।
 * ছবির ক্ষেত্রে শুধু সার্ভারের (ছোট) থাম্বনেল আনা হয়; বাকি ছবি পুরো নামাতে হয় বলে এখানে বাদ।
 */
suspend fun prefetchThumbnail(context: Context, account: AccountEntity, entry: RemoteEntry) {
    if (account.backendType == "crypt" || account.backendType == "local") return
    val kind = thumbKindOf(entry) ?: return
    val key = "${account.remoteName}|${entry.path}|${entry.modTimeMillis}|${entry.size}"
    if (hasServerThumbs(account.backendType) && ServerThumbs.thumbnail(context, account, entry, key) != null) return
    if (kind != ThumbKind.VIDEO) return
    val app = context.applicationContext as DriveMountApp
    val source = sourceCache[key] ?: runCatching {
        ThumbSource.Url(
            app.rcloneApi.streamUrl(
                fsRootFor(account.remoteName, account.backendType),
                entry.path,
                guessMime(entry.name, entry.mimeType),
            ),
        )
    }.getOrNull()?.also { sourceCache[key] = it } ?: return
    VideoThumbs.load(context, key, source, true)
}

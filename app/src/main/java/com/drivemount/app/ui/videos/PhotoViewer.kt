package com.drivemount.app.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X as LuX
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import coil.memory.MemoryCache
import coil.disk.DiskCache
import coil.ImageLoader
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.drivemount.app.R
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime

/** পূর্ণ-পর্দায় ছবি: বাম-ডানে সোয়াইপ, দুই আঙুলে জুম, ডাবল-ট্যাপে বড়/ছোট */
@Composable
fun PhotoViewerDialog(
    items: List<VideoItem>,
    startIndex: Int,
    accounts: Map<Long, AccountEntity>,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return
    val app = LocalContext.current.applicationContext as DriveMountApp
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // একবার ট্যাপে বার/নাম/নিচের ছবির সারি আস্তে মিলিয়ে যায়; আবার ট্যাপে ফিরে আসে
        var chrome by remember { mutableStateOf(true) }
        val chromeAlpha by androidx.compose.animation.core.animateFloatAsState(
            if (chrome) 1f else 0f,
            androidx.compose.animation.core.tween(380),
            label = "viewerChrome",
        )
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val strip = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(pager.currentPage) { strip.animateScrollToItem(pager.currentPage) }
        val halfScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp / 2
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 2) { page ->
                PhotoPage(items[page], accounts[items[page].accountId], app, onTap = { chrome = !chrome })
            }
            if (chromeAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                    ) {
                        Icon(Lucide.LuX, contentDescription = null, tint = Color.White)
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                ),
                            )
                            .navigationBarsPadding(),
                    ) {
                        Text(
                            text = "${pager.currentPage + 1} / ${items.size}   " + items[pager.currentPage].entry.name,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        // সব ছবির সারি: বর্তমানটা একটু বড়; স্লাইড করা যায়, ট্যাপে সেই ছবিতে যায়
                        androidx.compose.foundation.lazy.LazyRow(
                            state = strip,
                            modifier = Modifier.fillMaxWidth().height(76.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = halfScreen - 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            itemsIndexed(items, key = { i, it -> "${it.entry.path}#$i" }) { i, item ->
                                val cur = i == pager.currentPage
                                val sz by androidx.compose.animation.core.animateDpAsState(
                                    if (cur) 68.dp else 52.dp,
                                    androidx.compose.animation.core.tween(220),
                                    label = "stripSize",
                                )
                                val acc = accounts[item.accountId]
                                val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                Box(
                                    Modifier
                                        .size(sz)
                                        .clip(shape)
                                        .background(Color(0xFF2A2A2A))
                                        .then(if (cur) Modifier.border(2.dp, Color.White, shape) else Modifier)
                                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                                ) {
                                    if (acc != null) {
                                        com.drivemount.app.ui.browser.FileThumbnail(
                                            account = acc,
                                            entry = item.entry,
                                            kind = com.drivemount.app.ui.browser.ThumbKind.IMAGE,
                                            size = 68.dp,
                                            modifier = Modifier.fillMaxSize(),
                                            showBadge = false,
                                            thumbPx = 160,
                                            fallback = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * একটি ছবির পাতা: তিন ধাপে ক্রমে স্পষ্ট হয়
 *  ১. ছোট থাম্বনেল (সঙ্গে সঙ্গে, ডিস্ক থেকে)  ২. মাঝারি প্রিভিউ (ফোনের ক্যাশে, না থাকলে ক্লাউডের ছোট ফাইল)
 *  ৩. আসল ছবি (পেছনে নামে; এলে প্রিভিউয়ের ওপর ফুটে ওঠে)। ইন্টারনেট না থাকলে প্রিভিউই থাকে।
 */
@Composable
private fun PhotoPage(item: VideoItem, acc: AccountEntity?, app: DriveMountApp, onTap: () -> Unit) {
    val e = item.entry
    val size = e.size ?: -1L
    val remote = acc?.remoteName.orEmpty()
    val fs = acc?.let { fsRootFor(it.remoteName, it.backendType) }
    val secure = acc?.backendType == "crypt"
    val isLocalAcc = acc?.backendType == "local"

    val thumb by produceState<Bitmap?>(null, item) {
        if (acc != null) value = app.sidecar.thumbFor(remote, e.path, size)
    }
    val preview by produceState<Bitmap?>(null, item) {
        if (acc == null || fs == null || isLocalAcc) return@produceState // ফোনের নিজের ছবি: সরাসরি ফাইল, ক্লাউড-প্রিভিউ নয়
        val pv = app.photoPreviews
        value = pv.bitmap(remote, e.path, size)
        if (value == null) {
            val ok = if (pv.needsPreview(size)) pv.fetchRemote(remote, fs, e.path, size) else pv.fetchSmallOriginal(remote, fs, e.path, size)
            if (ok) value = pv.bitmap(remote, e.path, size)
        }
    }
    val url by produceState<String?>(null, item) {
        // অফলাইনে রাখা ছবি থাকলে সেটাই সরাসরি (নেট ছাড়া, তাৎক্ষণিক)
        val offline = acc?.let { app.offlineStore.localFile(it.id, e.path) }
        value = if (isLocalAcc) Uri.fromFile(java.io.File("/" + e.path)).toString()
        else if (offline != null) Uri.fromFile(offline).toString()
        else if (acc == null || fs == null) null else runCatching {
            app.rcloneApi.streamUrl(fs, e.path, guessMime(e.name, e.mimeType))
        }.getOrNull()
    }
    // আসল ছবির ক্যাশ-কী: স্থির (সেশন-বদলানো লোকাল URL নয়), তাই একবার খুললে আবার নামে না
    val cacheKey = "o|$remote|${e.path}|$size|${e.modTimeMillis ?: 0L}"
    val context = LocalContext.current
    // এনক্রিপ্টেড ভল্ট: আসল ছবি ডিস্কে সাদা অবস্থায় নয়; এনক্রিপ্টেড ক্যাশে (৪০MB পর্যন্ত) রেখে সেখান থেকেই দেখানো
    val secureBytes by produceState<ByteArray?>(null, item, url) {
        val u = url ?: return@produceState
        if (!secure || u.startsWith("file:")) return@produceState
        val vault = app.viewerVault
        value = withContext(Dispatchers.IO) {
            vault.get("orig", cacheKey) ?: runCatching {
                val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                try {
                    if (conn.contentLengthLong > SECURE_ORIG_MAX) null
                    else conn.inputStream.use { it.readBytes() }.takeIf { it.size <= SECURE_ORIG_MAX }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()?.also { vault.put("orig", cacheKey, it) }
        }
    }
    val model: Any? = if (secure && secureBytes != null) java.nio.ByteBuffer.wrap(secureBytes) else url
    ZoomableImage(
        model = model,
        cacheKey = cacheKey,
        placeholder = preview ?: thumb,
        secure = secure,
        loader = ViewerLoader.get(context, app.offlineStore.limitBytes),
        onTap = onTap,
    )
}

private const val SECURE_ORIG_MAX = 40L * 1024 * 1024

/**
 * আসল ছবির ক্যাশ: ফাইল দেখা হলে ডিস্কে জমে, অফলাইন সীমা (Settings) শেষ না হওয়া পর্যন্ত মুছে যায় না
 * (সীমা পূর্ণ হলে সবচেয়ে পুরোনো আগে যায়)। সীমা বদলালে অ্যাপ আবার চালুর পর কার্যকর হয়।
 */
private object ViewerLoader {
    private var loader: ImageLoader? = null

    @Synchronized
    fun get(context: Context, limitBytes: Long): ImageLoader {
        loader?.let { return it }
        val ctx = context.applicationContext
        return ImageLoader.Builder(ctx)
            .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.12).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(ctx.filesDir, "viewer_img"))
                    .maxSizeBytes(limitBytes.coerceAtLeast(256L * 1024 * 1024))
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
            .also { loader = it }
    }
}

private enum class FullState { LOADING, DONE, FAILED }

@Composable
private fun ZoomableImage(model: Any?, cacheKey: String, placeholder: Bitmap?, secure: Boolean, loader: ImageLoader, onTap: () -> Unit = {}) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var full by remember(model) { mutableStateOf(FullState.LOADING) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(Unit) {
                // জুম না থাকলে এক আঙুলের টান ছেড়ে দেওয়া হয়, যাতে পাতা বদলের সোয়াইপ কাজ করে
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        ) {
            // নিচের স্তর: প্রিভিউ/থাম্বনেল (আসল ছবি আসা পর্যন্ত ও ইন্টারনেট না থাকলে এটাই দেখা যায়)
            if (placeholder != null) {
                Image(
                    bitmap = placeholder.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // ওপরের স্তর: আসল ছবি; ভল্টের ছবি ডিস্কে সাদা অবস্থায় জমানো হয় না
            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .size(2560)
                        .crossfade(true)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .apply { if (secure) diskCachePolicy(CachePolicy.DISABLED) }
                        .build(),
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onSuccess = { full = FullState.DONE },
                    onError = { full = FullState.FAILED },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        when {
            placeholder == null && full != FullState.FAILED -> {
                if (full == FullState.LOADING) CircularProgressIndicator(color = Color.White)
            }
            full == FullState.LOADING -> LinearProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(2.dp),
                color = Color.White.copy(alpha = 0.7f),
                trackColor = Color.Transparent,
            )
            full == FullState.FAILED && placeholder != null -> Text(
                text = stringResource(R.string.photo_preview_only),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

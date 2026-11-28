package com.drivemount.app.ui.videos

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Row
import com.composables.icons.lucide.Trash2 as LuTrash2
import com.composables.icons.lucide.Info as LuInfo
import com.composables.icons.lucide.Share2 as LuShare2
import com.composables.icons.lucide.RotateCw as LuRotateCw
import com.composables.icons.lucide.Play as LuPlay
import com.composables.icons.lucide.Pause as LuPause
import com.composables.icons.lucide.Heart as LuHeart
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.geometry.isSpecified
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.calculateCentroid
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
    onDeleted: (VideoItem) -> Unit = {},
) {
    if (items.isEmpty()) return
    val app = LocalContext.current.applicationContext as DriveMountApp
    // এখান থেকে মোছা ছবি সঙ্গে সঙ্গে তালিকা থেকে সরে যায়
    var removed by remember { mutableStateOf(setOf<String>()) }
    val allItems = items
    val items = remember(allItems, removed) { allItems.filter { markKey(it) !in removed } }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, allItems.lastIndex)) { items.size }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val favs by app.mediaMarks.favorites.collectAsState()
    val rotations = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    var slideshow by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<VideoItem?>(null) }
    var deleteFor by remember { mutableStateOf<VideoItem?>(null) }

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
        val ctxStrip = LocalContext.current
        PhotoStripPrefs.load(ctxStrip)
        val rows = PhotoStripPrefs.rows
        val thumb = PhotoStripPrefs.sizeDp.dp
        // এক সারিতে বর্তমানটা একটু বড় দেখায়; স্লট নির্দিষ্ট মাপের, তাই স্ক্রল-অবস্থান লাফায় না
        val slot = if (rows == 1) thumb + 12.dp else thumb
        val strip = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val halfScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp / 2
        // গ্রিডে আইটেম কলাম-অনুযায়ী সাজানো; তাই সঠিক কলামে ঠিকমতো মাঝে আনা
        LaunchedEffect(pager.currentPage, rows) { strip.animateScrollToItem(pager.currentPage - (pager.currentPage % rows)) }
        // শুধু এই ছবি-ভিউয়ারে: একবার ট্যাপে স্ট্যাটাস/নেভিগেশন বারও লুকায়
        val hostView = androidx.compose.ui.platform.LocalView.current
        val dialogWindow = (hostView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        LaunchedEffect(chrome, dialogWindow) {
            dialogWindow?.let { w ->
                val ic = androidx.core.view.WindowCompat.getInsetsController(w, hostView)
                if (chrome) ic.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                else {
                    ic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    ic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        androidx.compose.runtime.DisposableEffect(dialogWindow) {
            onDispose {
                dialogWindow?.let { androidx.core.view.WindowCompat.getInsetsController(it, hostView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
            }
        }
        LaunchedEffect(slideshow, pager.currentPage) {
            if (slideshow && items.size > 1) {
                kotlinx.coroutines.delay(3_000)
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
        val dlgCtx = LocalContext.current
        infoFor?.let { it ->
            val acc = accounts[it.accountId]
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { infoFor = null },
                title = { Text(it.entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Path: /" + it.entry.path.trimStart('/'))
                        Text("Cloud: " + (acc?.displayName ?: "—"))
                        it.entry.size?.let { sz -> Text("Size: " + android.text.format.Formatter.formatFileSize(dlgCtx, sz)) }
                        it.entry.modTimeMillis?.let { t -> Text("Modified: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(t))) }
                        Text("Type: " + guessMime(it.entry.name, it.entry.mimeType))
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { infoFor = null }) { Text("OK") } },
            )
        }
        deleteFor?.let { it ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { deleteFor = null },
                title = { Text("Delete this photo?") },
                text = { Text(it.entry.name + "\n\nThis permanently deletes it from the cloud.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        deleteFor = null
                        val acc = accounts[it.accountId]
                        if (acc != null) scope.launch {
                            app.fileRepository.delete(acc, it.entry)
                                .onSuccess { _ -> removed = removed + markKey(it); onDeleted(it) }
                                .onFailure { e -> android.widget.Toast.makeText(dlgCtx, e.message ?: "Delete failed", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteFor = null }) { Text("Cancel") } },
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 2) { page ->
                PhotoPage(items[page], accounts[items[page].accountId], app, rotations[markKey(items[page])] ?: 0f, onTap = { chrome = !chrome })
            }
            if (chromeAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                    ) {
                        Icon(Lucide.LuX, contentDescription = null, tint = Color.White)
                    }
                    val curItem = items[pager.currentPage.coerceIn(0, items.lastIndex)]
                    val curKey = markKey(curItem)
                    val actCtx = LocalContext.current
                    Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 4.dp, top = 8.dp)) {
                        IconButton(onClick = { scope.launch { app.mediaMarks.toggleFavorites(listOf(curKey to com.drivemount.app.data.library.MediaKind.PHOTO)) } }) {
                            Icon(Lucide.LuHeart, contentDescription = "Favourite", tint = if (curKey in favs) Color(0xFFFF5252) else Color.White)
                        }
                        IconButton(onClick = { slideshow = !slideshow }) {
                            Icon(if (slideshow) Lucide.LuPause else Lucide.LuPlay, contentDescription = "Slideshow", tint = Color.White)
                        }
                        IconButton(onClick = { rotations[curKey] = ((rotations[curKey] ?: 0f) + 90f) % 360f }) {
                            Icon(Lucide.LuRotateCw, contentDescription = "Rotate", tint = Color.White)
                        }
                        IconButton(onClick = { scope.launch { shareImage(actCtx, app, accounts[curItem.accountId], curItem.entry) } }) {
                            Icon(Lucide.LuShare2, contentDescription = "Share", tint = Color.White)
                        }
                        IconButton(onClick = { infoFor = curItem }) {
                            Icon(Lucide.LuInfo, contentDescription = "Info", tint = Color.White)
                        }
                        IconButton(onClick = { deleteFor = curItem }) {
                            Icon(Lucide.LuTrash2, contentDescription = "Delete", tint = Color.White)
                        }
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
                        // সব ছবির সারি (১ বা ২ সারি, সেটিংসে ঠিক করা); স্লাইড করা যায়, ট্যাপে সেই ছবিতে যায়
                        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                            state = strip,
                            rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(rows),
                            modifier = Modifier.fillMaxWidth().height(slot * rows + 6.dp * (rows - 1) + 4.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = halfScreen - slot / 2),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(items, key = { i, it -> "${it.entry.path}#$i" }) { i, item ->
                                val cur = i == pager.currentPage
                                val sz by androidx.compose.animation.core.animateDpAsState(
                                    if (cur && rows == 1) thumb + 12.dp else thumb,
                                    androidx.compose.animation.core.tween(220),
                                    label = "stripSize",
                                )
                                val acc = accounts[item.accountId]
                                val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
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
}

/**
 * একটি ছবির পাতা: তিন ধাপে ক্রমে স্পষ্ট হয়
 *  ১. ছোট থাম্বনেল (সঙ্গে সঙ্গে, ডিস্ক থেকে)  ২. মাঝারি প্রিভিউ (ফোনের ক্যাশে, না থাকলে ক্লাউডের ছোট ফাইল)
 *  ৩. আসল ছবি (পেছনে নামে; এলে প্রিভিউয়ের ওপর ফুটে ওঠে)। ইন্টারনেট না থাকলে প্রিভিউই থাকে।
 */
@Composable
private fun PhotoPage(item: VideoItem, acc: AccountEntity?, app: DriveMountApp, rotation: Float, onTap: () -> Unit) {
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
        rotation = rotation,
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
private fun ZoomableImage(model: Any?, cacheKey: String, placeholder: Bitmap?, secure: Boolean, loader: ImageLoader, rotation: Float = 0f, onTap: () -> Unit = {}) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var full by remember(model) { mutableStateOf(FullState.LOADING) }
    val zoomScope = androidx.compose.runtime.rememberCoroutineScope()
    val rotFit = if (rotation % 180f != 0f && boxSize.width > 0 && boxSize.height > 0)
        minOf(boxSize.width.toFloat() / boxSize.height, boxSize.height.toFloat() / boxSize.width) else 1f
    /** [focus] (পর্দার বিন্দু) যেখানে আছে সেখানেই স্থির রেখে নতুন স্কেলে যায়; ছবি সীমার বাইরে যায় না */
    fun zoomAt(newScale: Float, focus: Offset) {
        val s = scale
        val ns = newScale.coerceIn(0.25f, 6f)
        val c = Offset(boxSize.width / 2f, boxSize.height / 2f)
        val d = focus - c
        val t = d - (d - offset) * (ns / s)
        val mx = (boxSize.width * (ns - 1f) / 2f).coerceAtLeast(0f)
        val my = (boxSize.height * (ns - 1f) / 2f).coerceAtLeast(0f)
        scale = ns
        offset = Offset(t.x.coerceIn(-mx, mx), t.y.coerceIn(-my, my))
    }
    fun panBy(d: Offset) {
        val mx = (boxSize.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val my = (boxSize.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        offset = Offset((offset.x + d.x).coerceIn(-mx, mx), (offset.y + d.y).coerceIn(-my, my))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    var multi = false
                    // ধাপ ১: পিঞ্চ/প্যান (জুম থাকলে এক আঙুলেও) অথবা ট্যাপ চেনা। জুম না থাকলে এক আঙুলের টান পেজারের জন্য ছাড়া হয়
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) multi = true
                        if (multi || scale > 1f) {
                            val z = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (z != 1f && centroid.isSpecified) zoomAt(scale * z, centroid)
                            val pan = event.calculatePan()
                            if (scale > 1f) panBy(pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) moved = true
                    } while (event.changes.any { it.pressed })
                    if (multi || moved) return@awaitEachGesture
                    // ধাপ ২: একবার ট্যাপ হয়েছে; অল্প সময়ে দ্বিতীয় ট্যাপ এলে ডাবল-ট্যাপ
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (second == null) {
                        onTap()
                        return@awaitEachGesture
                    }
                    val focus = second.position
                    var dragged = false
                    var lastY = focus.y
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == second.id } ?: break
                        if (!dragged && kotlin.math.abs(ch.position.y - focus.y) > viewConfiguration.touchSlop) dragged = true
                        if (dragged) {
                            // ডাবল-ট্যাপ ধরে নিচে টানলে জুম-ইন, ওপরে টানলে জুম-আউট (ট্যাপের জায়গাকে কেন্দ্র করে)
                            val dy = ch.position.y - lastY
                            if (dy != 0f) zoomAt(scale * kotlin.math.exp(dy * 0.006f), focus)
                            ch.consume()
                        }
                        lastY = ch.position.y
                    } while (ev.changes.any { it.pressed })
                    if (!dragged) {
                        // শুধু ডাবল-ট্যাপ: ট্যাপের জায়গায় জুম-ইন, জুম থাকলে জুম-আউট
                        val from = scale
                        val to = if (from > 1.05f || from < 0.95f) 1f else 2.5f
                        zoomScope.launch {
                            androidx.compose.animation.core.animate(from, to, animationSpec = androidx.compose.animation.core.tween(240)) { v, _ ->
                                zoomAt(v, focus)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    // ৯০°/২৭০° ঘোরালে ছবি পর্দায় ধরানোর জন্য ছোট করা হয়
                    scaleX = scale * rotFit,
                    scaleY = scale * rotFit,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation,
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


/** ছবিটি ক্যাশে নামিয়ে অন্য অ্যাপে শেয়ার (এনক্রিপ্টেড ভল্টের ছবি শেয়ার হয় না) */
private suspend fun shareImage(context: Context, app: DriveMountApp, acc: AccountEntity?, e: com.drivemount.app.engine.RemoteEntry) {
    if (acc == null) return
    if (acc.backendType == "crypt") {
        android.widget.Toast.makeText(context, "Vault photos can't be shared", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val file = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "open").apply { mkdirs() }
            val out = File(dir, "share_" + e.name.replace('/', '_'))
            if (acc.backendType == "local") File("/" + e.path).copyTo(out, overwrite = true)
            else {
                val fs = fsRootFor(acc.remoteName, acc.backendType)
                val url = app.rcloneApi.streamUrl(fs, e.path, guessMime(e.name, e.mimeType))
                java.net.URL(url).openStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
            out
        }.getOrNull()
    }
    if (file == null) {
        android.widget.Toast.makeText(context, "Couldn't prepare the photo", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType(guessMime(e.name, e.mimeType))
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

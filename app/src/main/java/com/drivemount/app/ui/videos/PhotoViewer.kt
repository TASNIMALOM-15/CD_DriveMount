package com.drivemount.app.ui.videos

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
import androidx.compose.material3.Icon
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                PhotoPage(items[page], accounts[items[page].accountId], app)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Lucide.LuX, contentDescription = null, tint = Color.White)
            }
            Text(
                text = "${pager.currentPage + 1} / ${items.size}   " + items[pager.currentPage].entry.name,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * একটি ছবির পাতা: তিন ধাপে ক্রমে স্পষ্ট হয়
 *  ১. ছোট থাম্বনেল (সঙ্গে সঙ্গে, ডিস্ক থেকে)  ২. মাঝারি প্রিভিউ (ফোনের ক্যাশে, না থাকলে ক্লাউডের ছোট ফাইল)
 *  ৩. আসল ছবি (পেছনে নামে; এলে প্রিভিউয়ের ওপর ফুটে ওঠে)। ইন্টারনেট না থাকলে প্রিভিউই থাকে।
 */
@Composable
private fun PhotoPage(item: VideoItem, acc: AccountEntity?, app: DriveMountApp) {
    val e = item.entry
    val size = e.size ?: -1L
    val remote = acc?.remoteName.orEmpty()
    val fs = acc?.let { fsRootFor(it.remoteName, it.backendType) }
    val secure = acc?.backendType == "crypt"

    val thumb by produceState<Bitmap?>(null, item) {
        if (acc != null) value = app.sidecar.thumbFor(remote, e.path, size)
    }
    val preview by produceState<Bitmap?>(null, item) {
        if (acc == null || fs == null) return@produceState
        val pv = app.photoPreviews
        value = pv.bitmap(remote, e.path, size)
        if (value == null && pv.needsPreview(size) && pv.fetchRemote(remote, fs, e.path, size)) {
            value = pv.bitmap(remote, e.path, size)
        }
    }
    val url by produceState<String?>(null, item) {
        // অফলাইনে রাখা ছবি থাকলে সেটাই সরাসরি (নেট ছাড়া, তাৎক্ষণিক)
        val offline = acc?.let { app.offlineStore.localFile(it.id, e.path) }
        value = if (offline != null) Uri.fromFile(offline).toString()
        else if (acc == null || fs == null) null else runCatching {
            app.rcloneApi.streamUrl(fs, e.path, guessMime(e.name, e.mimeType))
        }.getOrNull()
    }
    ZoomableImage(url = url, placeholder = preview ?: thumb, secure = secure)
}

private enum class FullState { LOADING, DONE, FAILED }

@Composable
private fun ZoomableImage(url: String?, placeholder: Bitmap?, secure: Boolean) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var full by remember(url) { mutableStateOf(FullState.LOADING) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
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
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .size(2560)
                        .crossfade(true)
                        .apply { if (secure) diskCachePolicy(CachePolicy.DISABLED) }
                        .build(),
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

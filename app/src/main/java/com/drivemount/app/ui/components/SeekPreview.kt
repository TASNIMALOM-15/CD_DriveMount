package com.drivemount.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.meta.SeekSheets
import com.drivemount.app.engine.fsRootFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** একসময়ে একটিই "স্ক্রলে চালু হওয়া" প্রিভিউ চলে (আঙুল সরালেও চলতে থাকে); নতুনটা শুরু হলে আগেরটা থামে */
object ActiveSeekPreview {
    var key by mutableStateOf<String?>(null)
}

@Stable
class SeekPreview(val key: String) {
    var held by mutableStateOf(false)
    var sheet by mutableStateOf<SeekSheets.Sheet?>(null)
    var frame by mutableIntStateOf(0)
    var previewed = false
    val active: Boolean get() = held || ActiveSeekPreview.key == key
}

@Composable
fun rememberSeekPreview(key: String, account: AccountEntity?, path: String, size: Long?): SeekPreview {
    val app = LocalContext.current.applicationContext as DriveMountApp
    val sp = remember(key) { SeekPreview(key) }
    val active = sp.active
    LaunchedEffect(active) {
        if (!active) { sp.sheet = null; return@LaunchedEffect }
        val sz = size ?: return@LaunchedEffect
        if (account == null || sz <= 0) return@LaunchedEffect
        val sh = app.seekSheets.load(account.remoteName, fsRootFor(account.remoteName, account.backendType), path, sz) ?: return@LaunchedEffect
        sp.sheet = sh
        sp.frame = 0
        while (true) {
            delay(220)
            sp.frame = (sp.frame + 1) % sh.count
        }
    }
    DisposableEffect(key) { onDispose { if (ActiveSeekPreview.key == key) ActiveSeekPreview.key = null } }
    return sp
}

/**
 * ট্যাপ = [onClick]; চেপে ধরলে প্রিভিউ (ছাড়লে থামে); আঙুল রেখে স্ক্রল শুরু করলে সেই আইটেমের প্রিভিউ চালু হয়
 * এবং আঙুল সরিয়ে নিলেও চলতে থাকে (আইটেম স্ক্রিন থেকে সরলে বা অন্যটি শুরু হলে থামে)।
 */
fun Modifier.seekPreviewGestures(sp: SeekPreview, onClick: () -> Unit): Modifier = this
    .pointerInput(sp) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                if ((c.position - down.position).getDistance() > slop) {
                    ActiveSeekPreview.key = sp.key
                    break
                }
            }
        }
    }
    .pointerInput(sp) {
        detectTapGestures(
            onPress = {
                sp.previewed = false
                val t = withTimeoutOrNull(200L) { tryAwaitRelease() }
                if (t == null) {
                    sp.previewed = true
                    ActiveSeekPreview.key = null
                    sp.held = true
                    tryAwaitRelease()
                    sp.held = false
                }
            },
            onTap = { if (!sp.previewed) onClick() },
        )
    }

/** থাম্বনেলের ওপর সিক-শিটের ফ্রেম (center-crop) আঁকে; প্রিভিউ না চললে কিছুই আঁকে না */
@Composable
fun SeekPreviewOverlay(sp: SeekPreview, modifier: Modifier = Modifier) {
    val sh = sp.sheet ?: return
    if (!sp.active) return
    val frame = sp.frame
    val img = remember(sh) { sh.bitmap.asImageBitmap() }
    val (ox, oy) = sh.tileOrigin(frame.toLong() * (sh.durationMs / sh.count))
    Canvas(modifier) {
        val scale = maxOf(size.width / sh.tileW, size.height / sh.tileH)
        val srcW = (size.width / scale).toInt().coerceIn(1, sh.tileW)
        val srcH = (size.height / scale).toInt().coerceIn(1, sh.tileH)
        drawImage(
            image = img,
            srcOffset = IntOffset(ox + (sh.tileW - srcW) / 2, oy + (sh.tileH - srcH) / 2),
            srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

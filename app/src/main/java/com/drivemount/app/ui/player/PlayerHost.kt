package com.drivemount.app.ui.player

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronDown as LuChevronDown
import com.composables.icons.lucide.Ellipsis as LuEllipsis
import com.composables.icons.lucide.FastForward as LuFastForward
import com.composables.icons.lucide.Gauge as LuGauge
import com.composables.icons.lucide.Headphones as LuHeadphones
import com.composables.icons.lucide.ListMusic as LuListMusic
import com.composables.icons.lucide.Maximize as LuMaximize
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.Film as LuFilm
import com.composables.icons.lucide.Pause as LuPause
import com.composables.icons.lucide.Play as LuPlay
import com.composables.icons.lucide.Repeat as LuRepeat
import com.composables.icons.lucide.Repeat1 as LuRepeat1
import com.composables.icons.lucide.Rewind as LuRewind
import com.composables.icons.lucide.Share2 as LuShare2
import com.composables.icons.lucide.Shuffle as LuShuffle
import com.composables.icons.lucide.SkipBack as LuSkipBack
import com.composables.icons.lucide.SkipForward as LuSkipForward
import com.composables.icons.lucide.X as LuX
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.drivemount.app.R
import com.drivemount.app.engine.RemoteEntry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class HostSheet { NONE, PLAYLIST, SPEED, TRACKS }

/**
 * সব পর্দার ওপরের প্লেয়ার-স্তর।
 *  - EXPANDED (ভিডিও): ওপরে ১৬:৯ ভিডিও, নিচে শিরোনাম ও "Up next" তালিকা (YouTube-স্টাইল)।
 *    ভিডিও ধরে নিচে টানলে ছোট হয়ে মিনি প্লেয়ার হয়।
 *  - MINI: ভাসমান ছোট প্লেয়ার। টেনে যেকোনো দিকে নেওয়া যায়, ছাড়লে নিজে থেকে সবচেয়ে কাছের কোনায় বসে যায়।
 *    এ অবস্থায় অ্যাপের অন্য ট্যাব/পর্দায় স্বাভাবিকভাবে কাজ করা যায়।
 *  - গান হলে ভিডিওর বদলে Now-Playing পর্দা; মিনি অবস্থায় ছোট কার্ড।
 *  - ভিডিওর "full screen" বাটনে আগের পূর্ণ-পর্দার PlayerScreen খোলে।
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerHost(c: PlaybackController) {
    val mode by c.mode.collectAsState()
    if (mode == PlaybackController.Mode.HIDDEN) return
    HostContent(c, mode)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun HostContent(c: PlaybackController, mode: PlaybackController.Mode) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val player = c.player
    val st = rememberPlayerState(player)

    val isAudio by c.isAudio.collectAsState()
    val fullscreen by c.fullscreen.collectAsState()
    val bottomBar by c.bottomBarPx.collectAsState()
    val extraBottom by c.extraBottomPx.collectAsState()
    val queue by c.queue.collectAsState()
    val error by c.error.collectAsState()
    val account by c.account.collectAsState()
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val expanded = mode == PlaybackController.Mode.EXPANDED
    val showFullscreen = fullscreen && !isAudio

    // গানের ভেতরের নাম/শিল্পী/album art (ফাইলের ট্যাগ থেকে)
    val fileMeta by produceState<AudioMeta?>(null, isAudio, st.index, queue) {
        val entry = queue.getOrNull(st.index)
        if (!isAudio || entry == null) {
            value = null
            return@produceState
        }
        val key = "${account?.id}|${entry.path}|${entry.modTimeMillis}"
        val hit = AudioMetaLoader.cached(key)
        if (hit != null) {
            value = hit
        } else {
            value = null
            val url = runCatching { c.externalUrl() }.getOrNull()?.first
            if (url != null) value = AudioMetaLoader.load(key, url)
        }
    }

    // Online album art (iTunes/Deezer) when the file has no embedded art or only a tiny one; loads after play, like lyrics
    val onlineArt by produceState<android.graphics.Bitmap?>(null, isAudio, st.index, fileMeta) {
        value = null
        val m = fileMeta
        if (!isAudio || m == null) return@produceState
        val emb = m.art
        if (emb != null && maxOf(emb.width, emb.height) >= 600) return@produceState
        val title = m.title?.takeIf { it.isNotBlank() } ?: queue.getOrNull(st.index)?.name.orEmpty()
        value = OnlineAlbumArt.load(title, m.artist.orEmpty(), m.album.orEmpty())
    }
    val meta = remember(fileMeta, onlineArt) {
        val m = fileMeta
        val a = onlineArt
        if (m == null || a == null) m else AudioMeta(m.title, m.artist, m.album, a, AudioMetaLoader.tiny(a))
    }

    var sheet by remember { mutableStateOf(HostSheet.NONE) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }

    // পর্দা ছাড়লে ভিডিও থামে (গান নয়, PiP-ও নয়) ও অবস্থান সংরক্ষিত হয়
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        c.savePosition()
        // ভিডিও থামে না: শুধু ছবি বন্ধ হয়, শব্দ ব্যাকগ্রাউন্ডে চলতে থাকে (PiP-তে ছবিও চলে)
        if (activity?.isInPictureInPictureMode != true) c.enterBackground()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { c.leaveBackground() }
    // লক-স্ক্রিন পেরিয়ে প্লেয়ার আবার এলে ছবি চালু করা
    DisposableEffect(Unit) {
        c.leaveBackground()
        onDispose { }
    }
    // চলার সময় প্রতি ৫ সেকেন্ডে অবস্থান সংরক্ষণ (অ্যাপ ক্র্যাশ করলেও হারায় না)
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) c.savePosition()
        }
    }
    // ফোন আড়াআড়ি ঘোরালে ভিডিও নিজে পূর্ণ পর্দায় যায়
    LaunchedEffect(landscape) {
        if (landscape && !c.isAudio.value && c.mode.value == PlaybackController.Mode.EXPANDED) c.setFullscreen(true)
    }
    // ভিডিও চলার সময় পর্দা জেগে থাকে
    DisposableEffect(st.isPlaying, isAudio) {
        view.keepScreenOn = st.isPlaying && !isAudio
        onDispose { view.keepScreenOn = false }
    }
    // নিয়ন্ত্রণ ৩.৫ সেকেন্ড পরে নিজে লুকায়
    LaunchedEffect(controlsVisible, st.isPlaying, interaction, sheet) {
        if (controlsVisible && st.isPlaying && sheet == HostSheet.NONE) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // ব্যাক: পূর্ণ পর্দা থেকে ছোট প্লেয়ারে, বড় প্লেয়ার থেকে মিনিতে
    BackHandler(enabled = expanded && !showFullscreen) { c.minimize() }
    BackHandler(enabled = showFullscreen) { c.setFullscreen(false) }

    Box(Modifier.fillMaxSize()) {
        if (showFullscreen) {
            // ফুলস্ক্রিনে প্লেয়ারের ফাঁকা অংশের (কালো বার) টাচ নিচের লাইব্রেরি/তালিকায় যেতে দেওয়া হয় না
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                PlayerScreen(onBack = { c.setFullscreen(false) }, vm = c)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val topInset = WindowInsets.statusBars.getTop(density).toFloat()
                val navInset = WindowInsets.navigationBars.getBottom(density).toFloat()
                val margin = with(density) { 12.dp.toPx() }
                val miniW = if (isAudio) min(with(density) { 300.dp.toPx() }, w - 2 * margin) else with(density) { 190.dp.toPx() }
                val miniH = if (isAudio) with(density) { 68.dp.toPx() } else miniW * 9f / 16f
                val reserved = max(bottomBar.toFloat(), navInset) + extraBottom
                val expH = if (landscape) min(w * 9f / 16f, h - topInset) else w * 9f / 16f

                // চার কোনার অবস্থান: 0 ওপর-বাম, 1 ওপর-ডান, 2 নিচ-বাম, 3 নিচ-ডান
                fun cornerOf(idx: Int): Offset {
                    val x = if (idx % 2 == 0) margin else w - miniW - margin
                    val y = if (idx < 2) topInset + margin else h - reserved - miniH - margin
                    return Offset(x, y)
                }

                // p: 1 = বড়, 0 = মিনি (টানার সময় মাঝামাঝি মান)
                val p = remember { Animatable(if (expanded) 1f else 0f) }
                val corner = remember { Animatable(cornerOf(c.miniCorner), Offset.VectorConverter) }
                var cornerIdx by remember { mutableIntStateOf(c.miniCorner) }

                LaunchedEffect(w, h, miniW, miniH, reserved, topInset) { corner.snapTo(cornerOf(cornerIdx)) }

                // ---- গানের মিনি: ২ সেকেন্ড পর শুধু অ্যালবাম-আর্টের ছোট বাবলে সংকুচিত; পাশে যেকোনো উচ্চতায় সরানো যায়; ট্যাপে ফেরে ----
                val bubS = with(density) { 40.dp.toPx() }
                val sideM = with(density) { 6.dp.toPx() }
                val bubMinY = topInset + sideM
                val bubMaxY = (h - reserved - bubS - sideM).coerceAtLeast(bubMinY)
                fun bubbleX(right: Boolean) = if (right) w - bubS - sideM else sideM
                fun bubbleFromCorner(o: Offset) =
                    Offset(bubbleX(o.x + miniW / 2f > w / 2f), (o.y + (miniH - bubS) / 2f).coerceIn(bubMinY, bubMaxY))
                var compact by remember { mutableStateOf(false) }
                var compactTick by remember { mutableIntStateOf(0) }
                val compactT = remember { Animatable(0f) }
                val bubble = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                LaunchedEffect(mode, isAudio) { compact = false }
                LaunchedEffect(mode, isAudio, compactTick, compact) {
                    if (isAudio && !expanded && !compact) {
                        delay(2_000)
                        bubble.snapTo(bubbleFromCorner(corner.value))
                        compact = true
                    }
                }
                LaunchedEffect(compact) { compactT.animateTo(if (compact) 1f else 0f, tween(320)) }
                fun decompress() {
                    val b = bubble.value
                    val idx = (if (b.y + bubS / 2f > h / 2f) 2 else 0) + (if (b.x + bubS / 2f > w / 2f) 1 else 0)
                    cornerIdx = idx
                    c.miniCorner = idx
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { corner.snapTo(cornerOf(idx)) }
                    compact = false
                    compactTick++
                }
                val bubbleDrag = Modifier.pointerInput(compact, w, h, bubS, reserved, topInset) {
                    if (!compact) return@pointerInput
                    var bpos = Offset.Zero
                    detectDragGestures(
                        onDragStart = { bpos = bubble.value },
                        onDrag = { change, amount ->
                            change.consume()
                            bpos += amount
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { bubble.snapTo(bpos) }
                        },
                        onDragEnd = {
                            val target = Offset(bubbleX(bpos.x + bubS / 2f > w / 2f), bpos.y.coerceIn(bubMinY, bubMaxY))
                            scope.launch { bubble.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 380f)) }
                        },
                    )
                }
                // টানার সময়কার মান আলাদা state-এ (p.snapTo-র দেরিতে-চলা coroutine আর animation-এর মধ্যে দৌড় এড়াতে);
                // নইলে ধীরে টানলে p মাঝপথে আটকে যেত আর মিনি থেকে বড় করা যেত না
                var dragging by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableFloatStateOf(1f) }
                // টানা শেষ হলে (বা mode বদলালে) সবসময় mode-এর লক্ষ্যমানে (বড় = ১, মিনি = ০) পৌঁছে থামে
                LaunchedEffect(mode, dragging) {
                    if (!dragging) p.animateTo(if (expanded) 1f else 0f, tween(260))
                }
                val dragMod = Modifier.dragToMinimize(
                    height = h,
                    onStart = {
                        dragFraction = p.value
                        dragging = true
                    },
                    onProgress = { dragFraction = it },
                    onEnd = { collapse ->
                        scope.launch {
                            p.snapTo(dragFraction)
                            dragging = false
                            if (collapse) c.minimize()
                        }
                    },
                )

                val pv = if (dragging) dragFraction else p.value
                val showPanel = pv > 0.02f
                val loading = st.state == Player.STATE_BUFFERING || (st.state == Player.STATE_IDLE && error == null)

                /** মিনি প্লেয়ার ধরে টানা: ছাড়লে (গতিসহ) সবচেয়ে কাছের কোনায় বসে যায় */
                val miniDrag = Modifier.pointerInput(mode, w, h, miniW, miniH, reserved, topInset) {
                    if (expanded) return@pointerInput
                    val tracker = VelocityTracker()
                    var pos = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            pos = corner.value
                            tracker.resetTracking()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            pos += amount
                            tracker.addPosition(change.uptimeMillis, pos)
                            // সঙ্গে সঙ্গে প্রয়োগ (দেরিতে চললে শেষের snap-animation কেটে দিত)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { corner.snapTo(pos) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            val cx = pos.x + miniW / 2f + v.x * 0.12f
                            val cy = pos.y + miniH / 2f + v.y * 0.12f
                            val idx = (if (cy > h / 2f) 2 else 0) + (if (cx > w / 2f) 1 else 0)
                            cornerIdx = idx
                            c.miniCorner = idx
                            scope.launch { corner.animateTo(cornerOf(idx), spring(dampingRatio = 0.75f, stiffness = 380f)) }
                        },
                        onDragCancel = {
                            scope.launch { corner.animateTo(cornerOf(cornerIdx)) }
                        },
                    )
                }

                if (!isAudio) {
                    // ---------------- ভিডিও ----------------
                    if (showPanel) {
                        VideoDetails(
                            topPad = with(density) { (topInset + expH).toDp() },
                            bottomInset = with(density) { navInset.toDp() },
                            alpha = pv,
                            st = st,
                            queue = queue,
                            account = account,
                            onSelect = { index ->
                                player.seekTo(index, C.TIME_UNSET)
                                player.play()
                            },
                            onSpeed = { sheet = HostSheet.SPEED },
                            onTracks = { sheet = HostSheet.TRACKS },
                            onPlaylist = { sheet = HostSheet.PLAYLIST },
                            onExternal = { scope.launch { openExternal(context, c) } },
                        )
                    }

                    val vx = lerp(corner.value.x, 0f, pv)
                    val vy = lerp(corner.value.y, topInset, pv)
                    val vw = lerp(miniW, w, pv)
                    val vh = lerp(miniH, expH, pv)
                    val shape = RoundedCornerShape(lerp(12f, 0f, pv).dp)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(vx.roundToInt(), vy.roundToInt()) }
                            .size(with(density) { vw.toDp() }, with(density) { vh.toDp() })
                            .shadow(elevation = (10f * (1f - pv)).dp, shape = shape)
                            .clip(shape)
                            .background(Color.Black)
                            // ট্যাপ: বড় অবস্থায় নিয়ন্ত্রণ দেখানো/লুকানো; মিনিতে বড় করা
                            .pointerInput(mode) {
                                detectTapGestures(
                                    onTap = {
                                        if (expanded) {
                                            controlsVisible = !controlsVisible
                                            interaction++
                                        } else {
                                            c.expand()
                                        }
                                    },
                                )
                            }
                            // বড় অবস্থায় নিচে টানা = মিনি; মিনিতে টানা = কোনায় সরানো
                            .then(
                                if (expanded) {
                                    dragMod
                                } else {
                                    miniDrag
                                },
                            ),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    this.player = c.player
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    setKeepContentOnPlayerReset(true)
                                }
                            },
                            onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // বড়/ছোট হওয়ার অ্যানিমেশনের মাঝে কোনো ওভারলে নয়: নইলে বড় নিয়ন্ত্রণ ছোট পর্দায় ঠেসে ওঠে
                        if (expanded && pv > 0.85f) {
                            ExpandedVideoOverlay(
                                visible = controlsVisible,
                                st = st,
                                loading = loading && error == null,
                                error = error,
                                onMinimize = { c.minimize() },
                                onFullscreen = { c.setFullscreen(true) },
                                onRetry = { c.retry() },
                                onExternal = { scope.launch { openExternal(context, c) } },
                                poke = { interaction++ },
                                player = player,
                            )
                        } else if (!expanded && pv < 0.15f) {
                            MiniVideoOverlay(st = st, player = player, onClose = { c.close() })
                        }
                    }
                } else {
                    // ---------------- গান ----------------
                    if (pv < 0.98f) {
                        val ct = compactT.value
                        val cardShape = RoundedCornerShape(lerp(18f, 20f, ct).dp)
                        AudioMiniCard(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        lerp(corner.value.x, bubble.value.x, ct).roundToInt(),
                                        lerp(corner.value.y, bubble.value.y, ct).roundToInt(),
                                    )
                                }
                                .size(with(density) { lerp(miniW, bubS, ct).toDp() }, with(density) { lerp(miniH, bubS, ct).toDp() })
                                .alpha(1f - pv)
                                .shadow(8.dp, cardShape)
                                .clip(cardShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .pointerInput(mode, compact) {
                                    detectTapGestures(onTap = { if (compact) decompress() else c.expand() })
                                }
                                .then(if (compact) bubbleDrag else miniDrag),
                            t = ct,
                            st = st,
                            player = player,
                            meta = meta,
                            onClose = { c.close() },
                        )
                    }
                    if (showPanel) {
                        val currentEntry = queue.getOrNull(st.index)
                        val bitChordUi by (context.applicationContext as com.drivemount.app.DriveMountApp)
                            .themeRepository.settings.collectAsState()
                        if (bitChordUi.bitChordPlayer) {
                        BitChordAudioPanel(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = (1f - pv) * h * 0.3f
                                    alpha = pv
                                }
                                .pointerInput(Unit) { detectTapGestures { } },
                            topInset = with(density) { topInset.toDp() },
                            bottomInset = with(density) { navInset.toDp() },
                            st = st,
                            player = player,
                            meta = meta,
                            folder = currentEntry?.path?.substringBeforeLast('/', "")?.substringAfterLast('/').orEmpty(),
                            format = currentEntry?.name?.substringAfterLast('.', "")?.uppercase().orEmpty(),
                            error = error,
                            dragModifier = dragMod,
                            onMinimize = { c.minimize() },
                            onSpeed = { sheet = HostSheet.SPEED },
                            onPlaylist = { sheet = HostSheet.PLAYLIST },
                            onRetry = { c.retry() },
                            onClose = { c.close() },
                        )
                        } else {
                        AudioPanel(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = (1f - pv) * h * 0.3f
                                    alpha = pv
                                }
                                .pointerInput(Unit) { detectTapGestures { } },
                            topInset = with(density) { topInset.toDp() },
                            bottomInset = with(density) { navInset.toDp() },
                            st = st,
                            player = player,
                            meta = meta,
                            folder = currentEntry?.path?.substringBeforeLast('/', "")?.substringAfterLast('/').orEmpty(),
                            format = currentEntry?.name?.substringAfterLast('.', "")?.uppercase().orEmpty(),
                            error = error,
                            dragModifier = dragMod,
                            onMinimize = { c.minimize() },
                            onSpeed = { sheet = HostSheet.SPEED },
                            onPlaylist = { sheet = HostSheet.PLAYLIST },
                            onRetry = { c.retry() },
                            onClose = { c.close() },
                        )
                        }
                    }
                }
            }
        }
    }

    // ---------------- sheet ----------------

    when (sheet) {
        HostSheet.PLAYLIST -> PlaylistSheet(
            items = queue,
            currentIndex = st.index,
            account = account,
            shuffle = st.shuffle,
            repeat = st.repeat,
            onSelect = { index ->
                player.seekTo(index, C.TIME_UNSET)
                player.play()
                sheet = HostSheet.NONE
            },
            onToggleShuffle = { player.shuffleModeEnabled = !player.shuffleModeEnabled },
            onCycleRepeat = { player.repeatMode = nextRepeat(player.repeatMode) },
            onDismiss = { sheet = HostSheet.NONE },
        )
        HostSheet.SPEED -> SpeedSheet(
            current = st.speed,
            onSelect = {
                player.setPlaybackSpeed(it)
                sheet = HostSheet.NONE
            },
            onDismiss = { sheet = HostSheet.NONE },
        )
        HostSheet.TRACKS -> TracksSheet(player = player, tracks = st.tracks, onDismiss = { sheet = HostSheet.NONE })
        HostSheet.NONE -> Unit
    }
}

private fun nextRepeat(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

/**
 * বড় প্লেয়ারে নিচে টানা: টানার সঙ্গে সঙ্গে ভিডিও ছোট হতে থাকে (১ → ০)।
 * অর্ধেকের বেশি নামলে বা জোরে ফ্লিক করলে মিনি প্লেয়ার, নইলে আবার বড়।
 */
private fun Modifier.dragToMinimize(
    height: Float,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onEnd: (collapse: Boolean) -> Unit,
): Modifier = pointerInput(height) {
    val tracker = VelocityTracker()
    var dragY = 0f
    var fraction = 1f
    detectVerticalDragGestures(
        onDragStart = {
            dragY = 0f
            fraction = 1f
            tracker.resetTracking()
            onStart()
        },
        onVerticalDrag = { change, amount ->
            change.consume()
            dragY += amount
            tracker.addPosition(change.uptimeMillis, Offset(0f, dragY))
            fraction = (1f - dragY / (height * 0.4f)).coerceIn(0f, 1f)
            onProgress(fraction)
        },
        onDragEnd = {
            val vy = tracker.calculateVelocity().y
            onEnd(fraction < 0.6f || vy > 1500f)
        },
        onDragCancel = { onEnd(false) },
    )
}

// ---------------- ভিডিও: বড় অবস্থার নিয়ন্ত্রণ ----------------

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BoxScope.ExpandedVideoOverlay(
    visible: Boolean,
    st: PlayerState,
    loading: Boolean,
    error: String?,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onRetry: () -> Unit,
    onExternal: () -> Unit,
    poke: () -> Unit,
    player: Player,
) {
    var scrub by remember { mutableLongStateOf(-1L) }

    if (loading) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
    }
    error?.let { message ->
        Column(
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.player_retry), color = Color.White) }
                TextButton(onClick = onExternal) { Text(stringResource(R.string.player_external), color = Color.White) }
            }
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
            // ওপর: ছোট করার তীর
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(Lucide.LuChevronDown, contentDescription = stringResource(R.string.player_minimize), tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text(
                    text = st.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
            }

            // মাঝ: আগের / প্লে-পজ / পরের
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                IconButton(
                    enabled = st.hasPrevious || st.position > 3_000,
                    onClick = {
                        if (player.currentPosition > 3_000 || !st.hasPrevious) player.seekTo(0) else player.seekToPreviousMediaItem()
                        poke()
                    },
                ) {
                    Icon(Lucide.LuSkipBack, contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    onClick = {
                        if (st.state == Player.STATE_ENDED) player.seekTo(0)
                        if (st.isPlaying) player.pause() else player.play()
                        poke()
                    },
                ) {
                    Icon(
                        imageVector = if (st.isPlaying) Lucide.LuPause else Lucide.LuPlay,
                        contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(
                    enabled = st.hasNext,
                    onClick = {
                        player.seekToNextMediaItem()
                        poke()
                    },
                ) {
                    Icon(Lucide.LuSkipForward, contentDescription = stringResource(R.string.player_next), tint = Color.White.copy(alpha = if (st.hasNext) 1f else 0.35f), modifier = Modifier.size(32.dp))
                }
            }

            // নিচ: seek bar + সময় + full screen
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(if (scrub >= 0) scrub else st.position) + " / " + formatTime(st.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onFullscreen) {
                        Icon(Lucide.LuMaximize, contentDescription = stringResource(R.string.player_fullscreen), tint = Color.White)
                    }
                }
                SeekBar(
                    position = st.position,
                    buffered = st.buffered,
                    duration = st.duration,
                    accent = MaterialTheme.colorScheme.primary,
                    onScrub = { target ->
                        scrub = target ?: -1L
                        poke()
                    },
                    onCommit = { target ->
                        player.seekTo(target)
                        st.position = target
                        poke()
                    },
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

// ---------------- ভিডিও: মিনি অবস্থার নিয়ন্ত্রণ ----------------

@Composable
private fun BoxScope.MiniVideoOverlay(st: PlayerState, player: Player, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x40000000)))
    IconButton(
        modifier = Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        onClick = {
            if (st.state == Player.STATE_ENDED) player.seekTo(0)
            if (st.isPlaying) player.pause() else player.play()
        },
    ) {
        Icon(
            imageVector = if (st.isPlaying) Lucide.LuPause else Lucide.LuPlay,
            contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
    IconButton(modifier = Modifier.align(Alignment.TopEnd).size(36.dp), onClick = onClose) {
        Icon(Lucide.LuX, contentDescription = stringResource(R.string.player_close), tint = Color.White, modifier = Modifier.size(20.dp))
    }
    LinearProgressIndicator(
        progress = { if (st.duration > 0) (st.position.toFloat() / st.duration).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.White.copy(alpha = 0.3f),
    )
}

// ---------------- ভিডিও: নিচের অংশ (শিরোনাম, বাটন, Up next) ----------------

@Composable
private fun VideoDetails(
    topPad: Dp,
    bottomInset: Dp,
    alpha: Float,
    st: PlayerState,
    queue: List<RemoteEntry>,
    account: com.drivemount.app.data.AccountEntity?,
    onSelect: (Int) -> Unit,
    onSpeed: () -> Unit,
    onTracks: () -> Unit,
    onPlaylist: () -> Unit,
    onExternal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = topPad),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = st.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (st.count > 1) {
                Text(
                    text = stringResource(R.string.player_playlist_count, st.index + 1, st.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onSpeed,
                label = { Text(speedLabel(st.speed)) },
                leadingIcon = { Icon(Lucide.LuGauge, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onTracks,
                label = { Text(stringResource(R.string.player_tracks)) },
                leadingIcon = { Icon(Lucide.LuListMusic, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onPlaylist,
                label = { Text(stringResource(R.string.player_playlist)) },
                leadingIcon = { Icon(Lucide.LuListMusic, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onExternal,
                label = { Text(stringResource(R.string.player_external)) },
                leadingIcon = { Icon(Lucide.LuShare2, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (queue.size > 1) {
            Text(
                text = stringResource(R.string.player_up_next),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            QueueList(
                items = queue,
                account = account,
                current = st.index,
                onSelect = onSelect,
                bottomPad = bottomInset,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QueueList(
    items: List<RemoteEntry>,
    account: com.drivemount.app.data.AccountEntity?,
    current: Int,
    onSelect: (Int) -> Unit,
    bottomPad: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.scrollToItem(current.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = bottomPad)) {
        itemsIndexed(items, key = { i, e -> "${e.path}#$i" }) { index, entry ->
            val isCurrent = index == current
            ListItem(
                headlineContent = {
                    Text(
                        text = entry.name.substringBeforeLast('.', entry.name),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                leadingContent = {
                    val kind = com.drivemount.app.ui.browser.thumbKindOf(entry)
                    Box(
                        modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val placeholder: @Composable () -> Unit = {
                            Icon(
                                if (kind == com.drivemount.app.ui.browser.ThumbKind.AUDIO) Lucide.LuMusic else Lucide.LuFilm,
                                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (kind != null && account != null) {
                            com.drivemount.app.ui.browser.FileThumbnail(
                                account = account, entry = entry, kind = kind, size = 96.dp,
                                modifier = Modifier.matchParentSize(), thumbPx = 240, showBadge = false, fallback = placeholder,
                            )
                        } else placeholder()
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(3.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp),
                        ) {
                            if (isCurrent) Icon(Lucide.LuPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            else Text((index + 1).toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                ),
                modifier = Modifier.clickable { onSelect(index) },
            )
        }
    }
}

// ---------------- গান: মিনি কার্ড ----------------

@Composable
private fun AudioMiniCard(modifier: Modifier, t: Float, st: PlayerState, player: Player, meta: AudioMeta?, onClose: () -> Unit) {
    // t: 0 = পূর্ণ মিনি কার্ড, 1 = শুধু অ্যালবাম-আর্টের ছোট বাবল
    val rest = (1f - t * 2.2f).coerceIn(0f, 1f)
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = lerp(10f, 0f, t).dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(lerp(48f, 40f, t).dp)
                    .clip(RoundedCornerShape(lerp(12f, 20f, t).dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val art = meta?.art
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Lucide.LuMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f).alpha(rest)) {
                Text(
                    text = meta?.title?.takeIf { it.isNotBlank() } ?: st.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = meta?.artist?.takeIf { it.isNotBlank() }
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (st.count > 1) {
                    Text(
                        text = stringResource(R.string.player_playlist_count, st.index + 1, st.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(modifier = Modifier.alpha(rest), onClick = {
                if (st.state == Player.STATE_ENDED) player.seekTo(0)
                if (st.isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    imageVector = if (st.isPlaying) Lucide.LuPause else Lucide.LuPlay,
                    contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                )
            }
            IconButton(modifier = Modifier.alpha(rest), onClick = onClose) {
                Icon(Lucide.LuX, contentDescription = stringResource(R.string.player_close))
            }
        }
        LinearProgressIndicator(
            progress = { if (st.duration > 0) (st.position.toFloat() / st.duration).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).alpha(rest),
        )
    }
}

// ---------------- গান: Now-Playing পর্দা ----------------

/**
 * ওপরে album art পুরো প্রস্থে; নিচে সেই art-এরই উল্টো (mirror) ঝাপসা রূপ ব্যাকগ্রাউন্ড।
 * শিরোনাম/শিল্পী, পাতলা সিক বার, বড় আগের/প্লে-পজ/পরের বাটন, আর নিচে শাফল / রিপিট+গতি / প্লেলিস্ট।
 */
@Composable
private fun AudioPanel(
    modifier: Modifier,
    topInset: Dp,
    bottomInset: Dp,
    st: PlayerState,
    player: Player,
    meta: AudioMeta?,
    folder: String,
    format: String,
    error: String?,
    dragModifier: Modifier,
    onMinimize: () -> Unit,
    onSpeed: () -> Unit,
    onPlaylist: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var scrub by remember { mutableLongStateOf(-1L) }
    var menu by remember { mutableStateOf(false) }
    val white = Color.White
    val dim = Color.White.copy(alpha = 0.62f)
    val glass = Color.White.copy(alpha = 0.16f)
    val art = meta?.art
    val title = meta?.title?.takeIf { it.isNotBlank() } ?: st.title
    val artist = meta?.artist?.takeIf { it.isNotBlank() } ?: folder
    val album = meta?.album?.takeIf { it.isNotBlank() }
    val fallbackTop = MaterialTheme.colorScheme.primaryContainer
    val fallbackBottom = MaterialTheme.colorScheme.tertiaryContainer

    BoxWithConstraints(modifier.background(Color(0xFF0E0F10))) {
        val artH = (maxHeight - 340.dp - bottomInset).coerceIn(maxHeight * 0.3f, maxHeight * 0.6f)

        // নিচের ব্যাকগ্রাউন্ড: art-এর উল্টো ঝাপসা রূপ
        Box(Modifier.fillMaxSize()) {
            val blurred = meta?.blurred
            if (blurred != null) {
                Image(
                    bitmap = blurred.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleY = -1f },
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(fallbackBottom, Color(0xFF0E0F10)))))
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        }

        Column(Modifier.fillMaxSize()) {
            // ওপরের album art (এখানে নিচে টানলে মিনি হয়)
            Box(Modifier.fillMaxWidth().height(artH).then(dragModifier)) {
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(fallbackTop, fallbackBottom))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Lucide.LuMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(120.dp),
                        )
                    }
                }
                // নিচের দিকে ধীরে মিশে যাওয়া, আর ওপরে লেখা পড়ার জন্য হালকা ছায়া
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.28f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                )
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onMinimize)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.size(width = 40.dp, height = 5.dp).clip(CircleShape).background(white.copy(alpha = 0.75f)))
                    }
                    if (folder.isNotEmpty()) {
                        Text(
                            text = "Playing from $folder",
                            color = white,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 48.dp),
                        )
                    }
                }
            }

            // নিচের অংশ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = bottomInset + 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (artist.isNotEmpty()) {
                            Text(artist, color = dim, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(glass),
                        ) {
                            Icon(Lucide.LuEllipsis, contentDescription = null, tint = white)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_speed)) },
                                leadingIcon = { Icon(Lucide.LuGauge, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onSpeed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_close)) },
                                leadingIcon = { Icon(Lucide.LuX, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onClose()
                                },
                            )
                        }
                    }
                }

                if (album != null) {
                    Row(
                        modifier = Modifier.padding(top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Lucide.LuMusic, contentDescription = null, tint = white)
                        Text(album, color = dim, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                error?.let { message ->
                    Text(message, color = Color(0xFFFF8A80), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.player_retry), color = white) }
                }

                SeekBar(
                    position = st.position,
                    buffered = st.buffered,
                    duration = st.duration,
                    accent = white,
                    onScrub = { target -> scrub = target ?: -1L },
                    onCommit = { target ->
                        player.seekTo(target)
                        st.position = target
                    },
                    modifier = Modifier.padding(top = 14.dp),
                    track = white,
                    showThumb = false,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(if (scrub >= 0) scrub else st.position), color = dim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (format.isNotEmpty()) {
                        Icon(Lucide.LuHeadphones, contentDescription = null, tint = dim, modifier = Modifier.size(14.dp))
                        Text(" $format", color = dim, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "-" + formatTime((st.duration - (if (scrub >= 0) scrub else st.position)).coerceAtLeast(0)),
                        color = dim,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }

                // আগের / প্লে-পজ / পরের
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(
                        modifier = Modifier.size(72.dp),
                        enabled = st.hasPrevious || st.position > 3_000,
                        onClick = { if (player.currentPosition > 3_000 || !st.hasPrevious) player.seekTo(0) else player.seekToPreviousMediaItem() },
                    ) {
                        Icon(
                            Lucide.LuRewind,
                            contentDescription = stringResource(R.string.player_previous),
                            tint = white.copy(alpha = if (st.hasPrevious || st.position > 3_000) 1f else 0.4f),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(84.dp),
                        onClick = {
                            if (st.state == Player.STATE_ENDED) player.seekTo(0)
                            if (st.isPlaying) player.pause() else player.play()
                        },
                    ) {
                        Icon(
                            imageVector = if (st.isPlaying) Lucide.LuPause else Lucide.LuPlay,
                            contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                            tint = white,
                            modifier = Modifier.size(76.dp),
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(72.dp),
                        enabled = st.hasNext,
                        onClick = { player.seekToNextMediaItem() },
                    ) {
                        Icon(
                            Lucide.LuFastForward,
                            contentDescription = stringResource(R.string.player_next),
                            tint = white.copy(alpha = if (st.hasNext) 1f else 0.4f),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }

                // নিচের সারি: শাফল | রিপিট + গতি | প্লেলিস্ট
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                        Icon(
                            Lucide.LuShuffle,
                            contentDescription = stringResource(R.string.player_shuffle),
                            tint = if (st.shuffle) white else dim,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.height(56.dp).clip(RoundedCornerShape(50)).background(glass),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { player.repeatMode = nextRepeat(player.repeatMode) }, modifier = Modifier.width(72.dp).fillMaxHeight()) {
                            Icon(
                                imageVector = if (st.repeat == Player.REPEAT_MODE_ONE) Lucide.LuRepeat1 else Lucide.LuRepeat,
                                contentDescription = stringResource(R.string.player_repeat),
                                tint = if (st.repeat == Player.REPEAT_MODE_OFF) dim else white,
                            )
                        }
                        Box(Modifier.width(1.dp).height(56.dp).background(white.copy(alpha = 0.3f)))
                        Box(
                            modifier = Modifier.width(72.dp).fillMaxHeight().clickable(onClick = onSpeed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(speedLabel(st.speed), color = white, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onPlaylist) {
                        Icon(
                            Lucide.LuListMusic,
                            contentDescription = stringResource(R.string.player_playlist),
                            tint = white,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
    }
}

package com.drivemount.app.ui.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.CoroutineScope
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
    val queue by c.queue.collectAsState()
    val error by c.error.collectAsState()
    val account by c.account.collectAsState()
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val expanded = mode == PlaybackController.Mode.EXPANDED
    val showFullscreen = fullscreen && !isAudio

    var sheet by remember { mutableStateOf(HostSheet.NONE) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }

    // পর্দা ছাড়লে ভিডিও থামে (গান নয়, PiP-ও নয়) ও অবস্থান সংরক্ষিত হয়
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        c.savePosition()
        if (!c.isAudio.value && activity?.isInPictureInPictureMode != true) player.pause()
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
            PlayerScreen(onBack = { c.setFullscreen(false) }, vm = c)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val topInset = WindowInsets.statusBars.getTop(density).toFloat()
                val navInset = WindowInsets.navigationBars.getBottom(density).toFloat()
                val margin = with(density) { 12.dp.toPx() }
                val miniW = if (isAudio) min(with(density) { 300.dp.toPx() }, w - 2 * margin) else with(density) { 190.dp.toPx() }
                val miniH = if (isAudio) with(density) { 68.dp.toPx() } else miniW * 9f / 16f
                val reserved = max(bottomBar.toFloat(), navInset)
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
                LaunchedEffect(mode) { p.animateTo(if (expanded) 1f else 0f, tween(260)) }

                val pv = p.value
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
                            scope.launch { corner.snapTo(pos) }
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
                                    Modifier.dragToMinimize(h, p, scope, c)
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
                        if (expanded) {
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
                        } else {
                            MiniVideoOverlay(st = st, player = player, onClose = { c.close() })
                        }
                    }
                } else {
                    // ---------------- গান ----------------
                    if (pv < 0.98f) {
                        AudioMiniCard(
                            modifier = Modifier
                                .offset { IntOffset(corner.value.x.roundToInt(), corner.value.y.roundToInt()) }
                                .size(with(density) { miniW.toDp() }, with(density) { miniH.toDp() })
                                .alpha(1f - pv)
                                .shadow(8.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .pointerInput(mode) { detectTapGestures(onTap = { c.expand() }) }
                                .then(miniDrag),
                            st = st,
                            player = player,
                            onClose = { c.close() },
                        )
                    }
                    if (showPanel) {
                        AudioPanel(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = (1f - pv) * h * 0.3f
                                    alpha = pv
                                }
                                .background(MaterialTheme.colorScheme.surface)
                                .pointerInput(Unit) { detectTapGestures { } },
                            topInset = with(density) { topInset.toDp() },
                            bottomInset = with(density) { navInset.toDp() },
                            st = st,
                            player = player,
                            entry = queue.getOrNull(st.index),
                            error = error,
                            loading = loading && error == null,
                            dragModifier = Modifier.dragToMinimize(h, p, scope, c),
                            onMinimize = { c.minimize() },
                            onSpeed = { sheet = HostSheet.SPEED },
                            onPlaylist = { sheet = HostSheet.PLAYLIST },
                            onRetry = { c.retry() },
                        )
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
 * বড় প্লেয়ারে নিচে টানা: টানার সঙ্গে সঙ্গে p (১ → ০) কমে ভিডিও ছোট হতে থাকে।
 * অর্ধেকের বেশি নামলে বা জোরে ফ্লিক করলে মিনি প্লেয়ার, নইলে আবার বড়।
 */
private fun Modifier.dragToMinimize(
    height: Float,
    p: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    scope: CoroutineScope,
    c: PlaybackController,
): Modifier = pointerInput(height) {
    val tracker = VelocityTracker()
    var dragY = 0f
    detectVerticalDragGestures(
        onDragStart = {
            dragY = 0f
            tracker.resetTracking()
        },
        onVerticalDrag = { change, amount ->
            change.consume()
            dragY += amount
            tracker.addPosition(change.uptimeMillis, Offset(0f, dragY))
            val fraction = (1f - dragY / (height * 0.4f)).coerceIn(0f, 1f)
            scope.launch { p.snapTo(fraction) }
        },
        onDragEnd = {
            val vy = tracker.calculateVelocity().y
            if (p.value < 0.6f || vy > 1500f) {
                c.minimize()
            } else {
                scope.launch { p.animateTo(1f, tween(200)) }
            }
        },
        onDragCancel = {
            scope.launch { p.animateTo(1f, tween(200)) }
        },
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
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.player_minimize), tint = Color.White, modifier = Modifier.size(30.dp))
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
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(32.dp))
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
                        imageVector = if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
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
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), tint = Color.White.copy(alpha = if (st.hasNext) 1f else 0.35f), modifier = Modifier.size(32.dp))
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
                        Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.player_fullscreen), tint = Color.White)
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
            imageVector = if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
    IconButton(modifier = Modifier.align(Alignment.TopEnd).size(36.dp), onClick = onClose) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.player_close), tint = Color.White, modifier = Modifier.size(20.dp))
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
                leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onTracks,
                label = { Text(stringResource(R.string.player_tracks)) },
                leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onPlaylist,
                label = { Text(stringResource(R.string.player_playlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onExternal,
                label = { Text(stringResource(R.string.player_external)) },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
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
    current: Int,
    onSelect: (Int) -> Unit,
    bottomPad: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.scrollToItem(current.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = bottomPad)) {
        itemsIndexed(items, key = { _, e -> e.path }) { index, entry ->
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
                    if (isCurrent) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Text((index + 1).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun AudioMiniCard(modifier: Modifier, st: PlayerState, player: Player, onClose: () -> Unit) {
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(st.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (st.count > 1) {
                    Text(
                        text = stringResource(R.string.player_playlist_count, st.index + 1, st.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = {
                if (st.state == Player.STATE_ENDED) player.seekTo(0)
                if (st.isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    imageVector = if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.player_close))
            }
        }
        LinearProgressIndicator(
            progress = { if (st.duration > 0) (st.position.toFloat() / st.duration).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
        )
    }
}

// ---------------- গান: Now-Playing পর্দা ----------------

@Composable
private fun AudioPanel(
    modifier: Modifier,
    topInset: Dp,
    bottomInset: Dp,
    st: PlayerState,
    player: Player,
    entry: RemoteEntry?,
    error: String?,
    loading: Boolean,
    dragModifier: Modifier,
    onMinimize: () -> Unit,
    onSpeed: () -> Unit,
    onPlaylist: () -> Unit,
    onRetry: () -> Unit,
) {
    var scrub by remember { mutableLongStateOf(-1L) }
    val folder = entry?.path?.substringBeforeLast('/', "")?.substringAfterLast('/').orEmpty()
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = topInset, bottom = bottomInset + 16.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ওপরের অংশ ও ছবি: এখানে নিচে টানলে মিনি হয়
        Column(modifier = Modifier.fillMaxWidth().then(dragModifier), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.player_minimize), modifier = Modifier.size(32.dp))
                }
                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }
            Box(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(112.dp),
                    )
                }
            }
        }

        Text(
            text = st.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (folder.isNotEmpty()) {
            Text(
                text = folder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
        }

        SeekBar(
            position = st.position,
            buffered = st.buffered,
            duration = st.duration,
            accent = MaterialTheme.colorScheme.primary,
            onScrub = { target -> scrub = target ?: -1L },
            onCommit = { target ->
                player.seekTo(target)
                st.position = target
            },
            modifier = Modifier.padding(top = 16.dp),
            track = onSurface,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(if (scrub >= 0) scrub else st.position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(st.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // শাফল | আগের | প্লে-পজ | পরের | রিপিট
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.player_shuffle),
                    tint = if (st.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                enabled = st.hasPrevious || st.position > 3_000,
                onClick = { if (player.currentPosition > 3_000 || !st.hasPrevious) player.seekTo(0) else player.seekToPreviousMediaItem() },
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(36.dp))
            }
            IconButton(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                onClick = {
                    if (st.state == Player.STATE_ENDED) player.seekTo(0)
                    if (st.isPlaying) player.pause() else player.play()
                },
            ) {
                Icon(
                    imageVector = if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(42.dp),
                )
            }
            IconButton(enabled = st.hasNext, onClick = { player.seekToNextMediaItem() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { player.repeatMode = nextRepeat(player.repeatMode) }) {
                Icon(
                    imageVector = if (st.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = stringResource(R.string.player_repeat),
                    tint = if (st.repeat == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // গতি ও প্লেলিস্ট
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSpeed) {
                Icon(Icons.Rounded.Speed, contentDescription = stringResource(R.string.player_speed), modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text(speedLabel(st.speed))
            }
            TextButton(onClick = onPlaylist) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = stringResource(R.string.player_playlist), modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (st.count > 1) "${st.index + 1}/${st.count}" else stringResource(R.string.player_playlist))
            }
        }
    }
}

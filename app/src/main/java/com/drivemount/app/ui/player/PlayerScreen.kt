package com.drivemount.app.ui.player

import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.drivemount.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Sheet { NONE, PLAYLIST, SPEED, TRACKS }

private const val SEEK_STEP_MS = 10_000L

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    vm: PlaybackController,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val player = vm.player
    val st = rememberPlayerState(player)
    val error by vm.error.collectAsState()
    val account by vm.account.collectAsState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var inPip by remember { mutableStateOf(false) }

    // স্পর্শ-নিয়ন্ত্রণের অবস্থা
    var scrubTarget by remember { mutableLongStateOf(-1L) }      // seek bar বা সোয়াইপে টানা লক্ষ্য
    var swipeSeeking by remember { mutableStateOf(false) }
    var seekFlash by remember { mutableIntStateOf(0) }           // ডবল-ট্যাপে জমা সেকেন্ড (− = পেছনে)
    var boosted by remember { mutableStateOf(false) }
    var levelKind by remember { mutableIntStateOf(0) }           // 1 = উজ্জ্বলতা, 2 = ভলিউম
    var level by remember { mutableFloatStateOf(0f) }
    var levelTick by remember { mutableIntStateOf(0) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    // ---------------- সিস্টেমের সাথে মেলানো ----------------

    // পূর্ণ পর্দা: status/navigation bar লুকানো, notch-এর নিচেও ছবি
    DisposableEffect(activity) {
        val window = activity?.window
        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            controller = WindowCompat.getInsetsController(window, window.decorView).also {
                it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsetsCompat.Type.systemBars())
            }
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.also {
                    it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null && Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.also {
                    it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    // বেরোনোর সময় উজ্জ্বলতা ও ঘোরানোর সেটিং আগের অবস্থায়
    DisposableEffect(activity) {
        val original = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = original
            activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }

    // চওড়া ভিডিও পেলে একবার নিজে থেকে landscape-এ নেওয়া
    var autoRotated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(st.videoWidth, st.videoHeight) {
        if (!autoRotated && st.videoWidth > st.videoHeight && st.videoHeight > 0 &&
            configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        ) {
            autoRotated = true
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // চলার সময় পর্দা জেগে থাকে
    DisposableEffect(st.isPlaying) {
        view.keepScreenOn = st.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    // Picture-in-Picture
    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        val leaveHint = Runnable { if (player.isPlaying) enterPip(activity, st.videoWidth, st.videoHeight) }
        componentActivity?.addOnPictureInPictureModeChangedListener(pipListener)
        if (Build.VERSION.SDK_INT < 31) componentActivity?.addOnUserLeaveHintListener(leaveHint)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(pipListener)
            if (Build.VERSION.SDK_INT < 31) componentActivity?.removeOnUserLeaveHintListener(leaveHint)
            if (Build.VERSION.SDK_INT >= 31) {
                activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }
    LaunchedEffect(st.isPlaying, st.videoWidth, st.videoHeight) {
        if (Build.VERSION.SDK_INT >= 31 && activity != null) {
            activity.setPictureInPictureParams(pipParams(st.videoWidth, st.videoHeight, autoEnter = st.isPlaying))
        }
    }

    // পর্দা ছাড়লে থামে (PiP-তে নয়) ও অবস্থান সংরক্ষিত হয়
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.savePosition()
        if (activity?.isInPictureInPictureMode != true) player.pause()
    }
    // চলার সময় প্রতি ৫ সেকেন্ডে অবস্থান সংরক্ষণ (অ্যাপ ক্র্যাশ করলেও হারায় না)
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) vm.savePosition()
        }
    }

    // নিয়ন্ত্রণ ৩.৫ সেকেন্ড পরে নিজে লুকায় (চলার সময়)
    LaunchedEffect(controlsVisible, st.isPlaying, interaction, sheet) {
        if (controlsVisible && st.isPlaying && sheet == Sheet.NONE) {
            delay(3_500)
            controlsVisible = false
        }
    }
    // ডবল-ট্যাপের সূচক ও উজ্জ্বলতা/ভলিউমের সূচক কিছুক্ষণ পরে মুছে যায়
    LaunchedEffect(seekFlash) {
        if (seekFlash != 0) {
            delay(700)
            seekFlash = 0
        }
    }
    LaunchedEffect(levelTick) {
        if (levelKind != 0) {
            delay(900)
            levelKind = 0
        }
    }

    BackHandler(enabled = sheet != Sheet.NONE) { sheet = Sheet.NONE }
    BackHandler(enabled = locked && sheet == Sheet.NONE) { locked = false }

    fun poke() {
        interaction++
    }

    fun seekRelative(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        val dur = player.duration
        player.seekTo(if (dur != androidx.media3.common.C.TIME_UNSET) target.coerceAtMost(dur) else target)
    }

    // ---------------- দৃশ্য ----------------

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = vm.player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { it.resizeMode = resizeMode },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // স্পর্শ: ট্যাপ = নিয়ন্ত্রণ দেখানো/লুকানো, ডবল-ট্যাপ = ±১০ সেকেন্ড, ধরে রাখা = ২× গতি,
        // ওপর-নিচ টানা = বাম পাশে উজ্জ্বলতা / ডান পাশে ভলিউম, পাশে টানা = এগোনো-পেছানো
        Box(
            modifier = Modifier
                .fillMaxSize()
                // সিস্টেমের ব্যাক-জেসচার ও নোটিফিকেশন প্যানেলের কিনারা বাদ: সেখানে প্লেয়ারের স্পর্শ কাজ করে না
                .windowInsetsPadding(WindowInsets.systemGestures)
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 40.dp)
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!locked && !inPip) {
                                val forward = offset.x > size.width / 2f
                                seekRelative(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
                                seekFlash = if (forward) maxOf(seekFlash, 0) + 10 else minOf(seekFlash, 0) - 10
                            }
                        },
                        onPress = {
                            if (locked) return@detectTapGestures
                            val speedBefore = player.playbackParameters.speed
                            val job = scope.launch {
                                delay(450)
                                boosted = true
                                player.setPlaybackSpeed(2f)
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (boosted) {
                                boosted = false
                                player.setPlaybackSpeed(speedBefore)
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    // একটি দিক বেছে নিয়ে তবেই কাজ: হালকা ছোঁয়া বা সামান্য সরায় কিছু ঘটে না।
                    // পাশে টানা = এগোনো-পেছানো, ওপর-নিচ = উজ্জ্বলতা/ভলিউম
                    val trigger = 36.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val leftSide = down.position.x < size.width / 2f
                        var dx = 0f
                        var dy = 0f
                        var axis = 0 // 0 = এখনও ঠিক হয়নি, 1 = পাশে, 2 = ওপর-নিচ
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || event.changes.size > 1) break
                            val d = change.positionChange()
                            if (axis == 0) {
                                dx += d.x
                                dy += d.y
                                if (abs(dx) > trigger && abs(dx) > abs(dy) * 2f) {
                                    axis = 1
                                    swipeSeeking = true
                                    scrubTarget = player.currentPosition
                                } else if (abs(dy) > trigger && abs(dy) > abs(dx) * 2f) {
                                    axis = 2
                                    if (leftSide) {
                                        val current = activity?.window?.attributes?.screenBrightness ?: -1f
                                        level = if (current >= 0f) current else systemBrightness(context)
                                        levelKind = 1
                                    } else {
                                        level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                        levelKind = 2
                                    }
                                    levelTick++
                                }
                                continue
                            }
                            change.consume()
                            if (axis == 1) {
                                val dur = st.duration
                                scrubTarget = (scrubTarget + d.x * 30f).toLong().coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                            } else {
                                level = (level - d.y / size.height * 1.3f).coerceIn(0f, 1f)
                                if (leftSide) {
                                    activity?.window?.let { w ->
                                        w.attributes = w.attributes.also { it.screenBrightness = level.coerceAtLeast(0.02f) }
                                    }
                                } else {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * maxVolume), 0)
                                }
                                levelTick++
                            }
                        }
                        // আঙুল তোলা বা বাতিল
                        if (axis == 1) {
                            if (scrubTarget >= 0) player.seekTo(scrubTarget)
                            swipeSeeking = false
                            scrubTarget = -1L
                        } else if (axis == 2) {
                            levelTick++
                        }
                    }
                },
        )

        if (!inPip) {
            // ---- সূচক ও বার্তা ----
            val loading = st.state == Player.STATE_BUFFERING || (st.state == Player.STATE_IDLE && error == null)
            if (loading && error == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }

            if (seekFlash != 0) {
                FlashBadge(
                    text = if (seekFlash > 0) "$seekFlash s ⏩" else "⏪ ${-seekFlash} s",
                    modifier = Modifier.align(if (seekFlash > 0) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 48.dp),
                )
            }
            if (boosted) {
                FlashBadge("2× ⏩", Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 12.dp))
            }
            if (scrubTarget >= 0) {
                FlashBadge(
                    text = formatTime(scrubTarget) + " / " + formatTime(st.duration),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (levelKind != 0) {
                LevelBadge(
                    icon = if (levelKind == 1) Icons.Rounded.BrightnessMedium else Icons.Rounded.VolumeUp,
                    level = level,
                    modifier = Modifier.align(if (levelKind == 1) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 40.dp),
                )
            }

            error?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = message, color = Color.White, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.retry() }) { Text(stringResource(R.string.player_retry)) }
                        OutlinedButton(onClick = { scope.launch { openExternal(context, vm) } }) {
                            Text(stringResource(R.string.player_external))
                        }
                    }
                }
            }

            // ---- লক করা অবস্থা: শুধু আনলক বাটন ----
            AnimatedVisibility(
                visible = locked && controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                RoundButton(Icons.Rounded.LockOpen, stringResource(R.string.player_unlock), modifier = Modifier.padding(horizontal = 32.dp)) {
                    locked = false
                    poke()
                }
            }

            // ---- নিয়ন্ত্রণ ----
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // ওপরের সারি
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Color(0x80000000))
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = st.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (st.count > 1) {
                                Text(
                                    text = stringResource(R.string.player_playlist_count, st.index + 1, st.count),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        IconButton(onClick = { scope.launch { openExternal(context, vm) } }) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.player_external), tint = Color.White)
                        }
                    }

                    // মাঝের বাটন
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        RoundButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), enabled = st.hasPrevious || st.position > 3_000) {
                            if (player.currentPosition > 3_000 || !st.hasPrevious) player.seekTo(0) else player.seekToPreviousMediaItem()
                            poke()
                        }
                        RoundButton(Icons.Rounded.Replay10, stringResource(R.string.player_back_10)) {
                            seekRelative(-SEEK_STEP_MS)
                            poke()
                        }
                        RoundButton(
                            icon = if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            description = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                            big = true,
                        ) {
                            if (st.state == Player.STATE_ENDED) player.seekTo(0)
                            if (st.isPlaying) player.pause() else player.play()
                            poke()
                        }
                        RoundButton(Icons.Rounded.Forward10, stringResource(R.string.player_forward_10)) {
                            seekRelative(SEEK_STEP_MS)
                            poke()
                        }
                        RoundButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), enabled = st.hasNext) {
                            player.seekToNextMediaItem()
                            poke()
                        }
                    }

                    // নিচের অংশ: seek bar + বাটন সারি
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color(0x80000000))
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 12.dp),
                    ) {
                        SeekBar(
                            position = st.position,
                            buffered = st.buffered,
                            duration = st.duration,
                            accent = MaterialTheme.colorScheme.primary,
                            onScrub = { target ->
                                scrubTarget = target ?: -1L
                                poke()
                            },
                            onCommit = { target ->
                                player.seekTo(target)
                                st.position = target
                                poke()
                            },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTime(if (scrubTarget >= 0 && !swipeSeeking) scrubTarget else st.position),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(" / " + formatTime(st.duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmallButton(Icons.Rounded.Lock, stringResource(R.string.player_lock)) {
                                    locked = true
                                    controlsVisible = false
                                }
                                SmallButton(Icons.Rounded.Speed, stringResource(R.string.player_speed), label = speedLabel(st.speed)) {
                                    sheet = Sheet.SPEED
                                }
                                SmallButton(Icons.Rounded.AspectRatio, stringResource(R.string.player_aspect)) {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    poke()
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmallButton(Icons.Rounded.Subtitles, stringResource(R.string.player_tracks)) {
                                    sheet = Sheet.TRACKS
                                }
                                SmallButton(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.player_pip)) {
                                    enterPip(activity, st.videoWidth, st.videoHeight)
                                }
                                SmallButton(Icons.Rounded.ScreenRotation, stringResource(R.string.player_rotate)) {
                                    activity?.requestedOrientation =
                                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        }
                                    poke()
                                }
                                // প্লেলিস্ট বাটন (লেখাসহ, সহজে চোখে পড়ার জন্য)
                                SmallButton(
                                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    description = stringResource(R.string.player_playlist),
                                    label = if (st.count > 1) "${st.index + 1}/${st.count}" else null,
                                ) {
                                    sheet = Sheet.PLAYLIST
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- sheet ----------------

    when (sheet) {
        Sheet.PLAYLIST -> PlaylistSheet(
            items = vm.items,
            currentIndex = st.index,
            account = account,
            shuffle = st.shuffle,
            repeat = st.repeat,
            onSelect = { index ->
                player.seekTo(index, androidx.media3.common.C.TIME_UNSET)
                player.play()
                sheet = Sheet.NONE
            },
            onToggleShuffle = { player.shuffleModeEnabled = !player.shuffleModeEnabled },
            onCycleRepeat = {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.SPEED -> SpeedSheet(
            current = st.speed,
            onSelect = {
                player.setPlaybackSpeed(it)
                sheet = Sheet.NONE
            },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.TRACKS -> TracksSheet(player = player, tracks = st.tracks, onDismiss = { sheet = Sheet.NONE })
        Sheet.NONE -> Unit
    }
}

// ---------------- ছোট উপাদান ----------------

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(if (big) 72.dp else 48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (big) 0.45f else 0.30f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(if (big) 44.dp else 28.dp),
        )
    }
}

@Composable
private fun SmallButton(icon: ImageVector, description: String, label: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
        if (label != null) Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FlashBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LevelBadge(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 110.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .size(width = 6.dp, height = (110 * level).dp)
                    .background(Color.White),
            )
        }
        Text("${Math.round(level * 100)}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------- সাহায্যকারী ----------------

internal fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun systemBrightness(context: Context): Float = try {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
} catch (_: Exception) {
    0.5f
}

private fun pipParams(width: Int, height: Int, autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    if (width > 0 && height > 0) {
        // Android শুধু ০.৪২–২.৩৯ অনুপাত নেয়
        val ratio = (width.toFloat() / height).coerceIn(0.43f, 2.38f)
        builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
    }
    if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(autoEnter)
    return builder.build()
}

private fun enterPip(activity: android.app.Activity?, width: Int, height: Int) {
    activity ?: return
    try {
        activity.enterPictureInPictureMode(pipParams(width, height, autoEnter = false))
    } catch (_: Exception) {
        // এই ফোনে PiP নেই বা বন্ধ
    }
}

/** অন্য প্লেয়ার অ্যাপে (VLC, MX ইত্যাদি) একই লোকাল লিংক খোলে */
internal suspend fun openExternal(context: Context, vm: PlaybackController) {
    val (url, mime) = vm.externalUrl() ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime))
    } catch (_: ActivityNotFoundException) {
        // অন্য প্লেয়ার নেই
    }
}

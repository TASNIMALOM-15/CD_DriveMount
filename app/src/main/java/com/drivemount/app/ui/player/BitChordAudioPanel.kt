package com.drivemount.app.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.composables.icons.lucide.Ellipsis as LuEllipsis
import com.composables.icons.lucide.Gauge as LuGauge
import com.composables.icons.lucide.Headphones as LuHeadphones
import com.composables.icons.lucide.ListMusic as LuListMusic
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.Repeat as LuRepeat
import com.composables.icons.lucide.Repeat1 as LuRepeat1
import com.composables.icons.lucide.Shuffle as LuShuffle
import com.composables.icons.lucide.Volume1 as LuVolume1
import com.composables.icons.lucide.Volume2 as LuVolume2
import com.composables.icons.lucide.X as LuX
import com.drivemount.app.R
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.composables.icons.lucide.Cast as LuCast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * BitChord-style now-playing screen (portrait): full-screen blurred artwork backdrop with a
 * dark scrim, centred artwork that shrinks when paused, 20sp title/artist, thin bouncy
 * scrubber, big white transport glyphs, volume slider, and a pill-shaped action row.
 * Same parameters as AudioPanel so it can replace it 1:1 (see PlayerHost).
 */

private val BcWhite = Color.White
private val BcGutter = 30.dp
private val BcMaxWidth = 560.dp
private val BcScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Black.copy(alpha = 0.64f),
)
private val BcActionSize = 44.dp
private val BcPillSegment = 64.dp

// BitChord layout constants (NowPlayingScreen.kt)
private val BcDismissStrip = 32.dp
private val BcArtBoxTopPad = 8.dp
private val BcArtTitleGap = 20.dp
private val BcHeaderHeight = 60.dp
private const val BcHeroFadeFraction = 0.42f
private const val BcFadeCurveStops = 16
// controls deck: lyric strip + scrubber + transport + volume + actions + caption + bottom gap
private val BcDeckHeight = 304.dp

private fun bcSmootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BitChordAudioPanel(
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
    val view = LocalView.current
    val tick: () -> Unit = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    val title = meta?.title?.takeIf { it.isNotBlank() } ?: st.title
    val artist = meta?.artist?.takeIf { it.isNotBlank() } ?: folder
    val dim = BcWhite.copy(alpha = 0.55f)
    val art = meta?.art
    val blurImage = rememberBcBlur(art)

    BoxWithConstraints(modifier.background(Color(0xFF121212))) {
        // --- BitChord hero sizing (fullArt / heroHeight exactly as NowPlayingScreen) ---
        val fullArt = minOf(
            maxWidth,
            maxHeight - BcDeckHeight - bottomInset - topInset - BcDismissStrip - BcArtBoxTopPad -
                BcArtTitleGap - BcHeaderHeight,
        ).coerceAtLeast(120.dp)
        val artBottom = topInset + BcDismissStrip + BcArtBoxTopPad + fullArt
        val heroHeight = artBottom + BcArtTitleGap / 2
        val headerTop = artBottom + BcArtTitleGap

        // ---- FullArtworkBlurBackdrop: 128px box-blur of the art, cropped, with the scrim ----
        Crossfade(targetState = blurImage, animationSpec = tween(320, easing = FastOutSlowInEasing), label = "bcBackdrop") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawRect(brush = BcScrim) }

        // ---- hero artwork: full width, bottom 42% eased out with smootherStep (DstIn mask) ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .then(dragModifier)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .background(Color.Black.copy(alpha = 0.18f))
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = Array(BcFadeCurveStops + 1) { i ->
                                val t = i / BcFadeCurveStops.toFloat()
                                t to Color.Black.copy(alpha = 1f - bcSmootherStep(t))
                            },
                            startY = size.height * (1f - BcHeroFadeFraction),
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(LuMusicIcon, null, tint = BcWhite.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
            }
        }

        // ---- top strip: status-bar scrim + handle + "Playing from" caption ----
        val scrimColors = remember {
            List(8) { i ->
                val progress = i / 7f
                Color.Black.copy(alpha = 0.35f * (1f - progress).toDouble().pow(1.5).toFloat())
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + BcDismissStrip)
                .background(Brush.verticalGradient(scrimColors))
                .then(dragModifier),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onMinimize)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(BcWhite.copy(alpha = 0.55f)))
                }
                if (folder.isNotEmpty()) {
                    Text(
                        text = "Playing from $folder",
                        color = BcWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                }
            }
        }

        // ---- title / artist header (60dp, directly under the artwork) ----
        Row(
            modifier = Modifier
                .offset(y = headerTop)
                .fillMaxWidth()
                .height(BcHeaderHeight)
                .padding(horizontal = BcGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, color = BcWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, modifier = Modifier.basicMarquee())
                if (artist.isNotEmpty()) {
                    Text(artist, color = dim, fontSize = 20.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, modifier = Modifier.basicMarquee())
                }
            }
            Spacer(Modifier.width(10.dp))
            Box {
                BcCircleGlyph(LuEllipsisIcon, onClick = { tick(); menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_shuffle)) },
                        leadingIcon = { Icon(LuShuffleIcon, contentDescription = null) },
                        onClick = { menu = false; player.shuffleModeEnabled = !player.shuffleModeEnabled },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_repeat)) },
                        leadingIcon = { Icon(if (st.repeat == Player.REPEAT_MODE_ONE) LuRepeat1Icon else LuRepeatIcon, contentDescription = null) },
                        onClick = {
                            menu = false
                            player.repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_speed) + " · " + speedLabel(st.speed)) },
                        leadingIcon = { Icon(Lucide.LuGauge, contentDescription = null) },
                        onClick = { menu = false; onSpeed() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_close)) },
                        leadingIcon = { Icon(Lucide.LuX, contentDescription = null) },
                        onClick = { menu = false; onClose() },
                    )
                }
            }
        }

        // ---- controls deck (bottom) ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = BcMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = BcGutter)
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // lyric strip slot (BitChord keeps this row; nothing to show without lyrics)
            if (error != null) {
                Row(
                    Modifier.fillMaxWidth().height(34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.player_retry), color = BcWhite) }
                }
            } else {
                Spacer(Modifier.height(34.dp))
            }

            // scrubber
            val duration = st.duration
            val shown = if (duration > 0) {
                ((if (scrub >= 0) scrub else st.position).toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            BcThinSlider(
                value = shown,
                onValueChange = { f -> if (duration > 0) scrub = (f * duration).toLong() },
                onValueChangeFinished = {
                    if (scrub >= 0) {
                        player.seekTo(scrub)
                        st.position = scrub
                    }
                    scrub = -1L
                },
            )
            Box(Modifier.fillMaxWidth().offset(y = (-9).dp)) {
                val now = if (scrub >= 0) scrub else st.position
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(now), color = dim, style = MaterialTheme.typography.labelMedium)
                    Text("-" + formatTime((duration - now).coerceAtLeast(0)), color = dim, style = MaterialTheme.typography.labelMedium)
                }
                if (format.isNotEmpty()) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.LuHeadphones, null, tint = dim, modifier = Modifier.size(13.dp))
                        Text(" $format", color = dim, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // transport
            val canPrev = st.hasPrevious || st.position > 3_000
            val loading = st.state == Player.STATE_BUFFERING
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BcTransportGlyph(
                    icon = R.drawable.ic_player_previous,
                    description = stringResource(R.string.player_previous),
                    size = 53.dp, touch = 53.dp, heightScale = 0.85f, enabled = canPrev,
                ) {
                    tick()
                    if (player.currentPosition > 3_000 || !st.hasPrevious) player.seekTo(0) else player.seekToPreviousMediaItem()
                }
                if (loading) {
                    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BcWhite, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
                    }
                } else {
                    BcTransportGlyph(
                        icon = if (st.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        description = stringResource(if (st.isPlaying) R.string.player_pause else R.string.player_play),
                        size = 74.dp, touch = 92.dp,
                    ) {
                        tick()
                        if (st.state == Player.STATE_ENDED) player.seekTo(0)
                        if (st.isPlaying) player.pause() else player.play()
                    }
                }
                BcTransportGlyph(
                    icon = R.drawable.ic_player_next,
                    description = stringResource(R.string.player_next),
                    size = 53.dp, touch = 53.dp, heightScale = 0.85f, enabled = st.hasNext,
                ) {
                    tick()
                    player.seekToNextMediaItem()
                }
            }

            Spacer(Modifier.height(12.dp))
            BcVolumeRow()
            Spacer(Modifier.height(6.dp))

            // action row (BitChord default): lyrics | output (cast) | queue
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pillWidth = BcPillSegment * 2 + 1.dp
                val inset = ((maxWidth - BcActionSize * 2 - pillWidth) / 4).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        BcBottomGlyph(BcLyricsQuote, "Lyrics", highlighted = false) { tick() }
                    }
                    BcBottomGlyph(Lucide.LuCast, "Audio output", highlighted = false, iconSize = 24.dp) { tick() }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        BcBottomGlyph(BcQueue, stringResource(R.string.player_playlist), highlighted = false) { tick(); onPlaylist() }
                    }
                }
            }
            Text(
                text = "This phone",
                color = dim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.65f),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

// Lucide icon accessors (aliased imports can't be used as plain values in some call sites)
private val LuMusicIcon get() = Lucide.LuMusic
private val LuEllipsisIcon get() = Lucide.LuEllipsis
private val LuShuffleIcon get() = Lucide.LuShuffle
private val LuRepeatIcon get() = Lucide.LuRepeat
private val LuRepeat1Icon get() = Lucide.LuRepeat1
private val LuListMusicIcon get() = Lucide.LuListMusic

@Composable
private fun BcCircleGlyph(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(BcWhite.copy(alpha = 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = BcWhite, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun BcTransportGlyph(
    icon: Int,
    description: String,
    size: Dp,
    touch: Dp,
    heightScale: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, label = "bcTransportAlpha")
    Box(
        modifier = Modifier
            .size(touch)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = BcWhite.copy(alpha = alpha),
            modifier = Modifier
                .size(size)
                .then(if (heightScale != 1f) Modifier.graphicsLayer { scaleY = heightScale } else Modifier),
        )
    }
}

/** Action glyph; when [highlighted] the icon is cut out of a rounded tile (BitChord's active look). */
@Composable
private fun BcBottomGlyph(icon: ImageVector, description: String, highlighted: Boolean, iconSize: Dp = 26.dp, onClick: () -> Unit) {
    val tileColor = BcWhite.copy(alpha = 0.62f)
    Box(
        modifier = Modifier
            .size(BcActionSize)
            .then(
                if (highlighted) {
                    Modifier
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val side = 35.dp.toPx()
                            val corner = 7.dp.toPx()
                            drawRoundRect(
                                color = tileColor,
                                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                                size = Size(side, side),
                                cornerRadius = CornerRadius(corner, corner),
                                blendMode = BlendMode.SrcOut,
                            )
                        }
                } else Modifier,
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = BcWhite.copy(alpha = if (highlighted) 1f else 0.75f), modifier = Modifier.size(iconSize))
    }
}

/** System media volume, with the same thin slider and speaker glyphs as BitChord. */
@Composable
private fun BcVolumeRow() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        while (!dragging) {
            level = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            kotlinx.coroutines.delay(500)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(BcVolumeDown, null, tint = BcWhite.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BcThinSlider(
            value = level,
            onValueChange = {
                dragging = true
                level = it
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt().coerceIn(0, max), 0)
            },
            onValueChangeFinished = { dragging = false },
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(BcVolumeUp, null, tint = BcWhite.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

/** BitChord's ThinSlider (core): pill track that swells with a bouncy spring while dragged. */
@Composable
private fun BcThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = BcWhite.copy(alpha = 0.92f),
    inactiveColor: Color = BcWhite.copy(alpha = 0.26f),
) {
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bcSliderHeight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(activeHeight + 22.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }
                    dragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(filled.coerceAtLeast(size.height).coerceAtMost(size.width), size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}


// ---- exact BitChord icon vectors (BitChordIcons.Queue / LyricsQuote) + Material volume glyphs ----
private val BcStroke = SolidColor(Color.Black)

private val BcQueue: ImageVector by lazy {
    ImageVector.Builder(
        name = "bc_queue", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(stroke = BcStroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            for (y in listOf(6f, 12f, 18f)) {
                moveTo(8f, y)
                lineTo(21f, y)
            }
        }
        path(fill = BcStroke) {
            for (y in listOf(6f, 12f, 18f)) {
                moveTo(4.35f, y)
                arcToRelative(1.25f, 1.25f, 0f, true, true, -2.5f, 0f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 2.5f, 0f)
                close()
            }
        }
    }.build()
}

private val BcLyricsQuote: ImageVector by lazy {
    ImageVector.Builder(
        name = "bc_lyrics_quote", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = BcStroke, strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 3.5f)
            lineTo(18f, 3.5f)
            quadTo(21f, 3.5f, 21f, 6.5f)
            lineTo(21f, 15f)
            quadTo(21f, 18f, 18f, 18f)
            lineTo(10f, 18f)
            lineTo(6f, 21f)
            lineTo(6f, 18f)
            quadTo(3f, 18f, 3f, 15f)
            lineTo(3f, 6.5f)
            quadTo(3f, 3.5f, 6f, 3.5f)
            close()
        }
        path(fill = BcStroke) {
            for (x in listOf(7.6f, 12.8f)) {
                moveTo(x, 8f)
                lineTo(x + 3f, 8f)
                lineTo(x + 3f, 11.1f)
                quadTo(x + 3f, 13.2f, x + 0.5f, 13.7f)
                lineTo(x + 0.5f, 12.3f)
                quadTo(x + 1.7f, 12f, x + 1.7f, 11f)
                lineTo(x, 11f)
                close()
            }
        }
    }.build()
}

private fun bcMaterialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = BcStroke,
    ).build()

private val BcVolumeDown: ImageVector by lazy {
    bcMaterialIcon("bc_volume_down", "M18.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM5,9v6h4l5,5V4L9,9H5z")
}

private val BcVolumeUp: ImageVector by lazy {
    bcMaterialIcon(
        "bc_volume_up",
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    )
}

// ---- BitChord's FullArtworkBlurBackdrop source: 128px, box blur radius min(w,h)/12, 3 passes ----
@Composable
private fun rememberBcBlur(art: Bitmap?): ImageBitmap? {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(art) {
        image = if (art == null) null else withContext(Dispatchers.Default) {
            runCatching { bcBoxBlurred(art).asImageBitmap() }.getOrNull()
        }
    }
    return image
}

private fun bcBoxBlurred(src: Bitmap): Bitmap {
    val scale = 128f / maxOf(src.width, src.height)
    val w = (src.width * scale).roundToInt().coerceAtLeast(2)
    val h = (src.height * scale).roundToInt().coerceAtLeast(2)
    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
    var source = IntArray(w * h)
    scaled.getPixels(source, 0, w, 0, 0, w, h)
    var target = IntArray(source.size)
    val radius = (minOf(w, h) / 12).coerceAtLeast(2)
    repeat(3) {
        bcBoxBlurPass(source, target, w, h, radius, horizontal = true)
        bcBoxBlurPass(target, source, w, h, radius, horizontal = false)
    }
    return Bitmap.createBitmap(source, w, h, Bitmap.Config.ARGB_8888)
}

private fun bcBoxBlurPass(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1
    for (fixed in 0 until minor) {
        var red = 0
        var green = 0
        var blue = 0
        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) source[fixed * width + position] else source[position * width + fixed]
        }
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        for (moving in 0 until major) {
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

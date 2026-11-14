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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.lerp as dpLerp
import com.composables.icons.lucide.ChevronRight as LuChevronRight
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
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
private val BcBottomShade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.48f to Color.Transparent,
    0.80f to Color.Black.copy(alpha = 0.16f),
    1f to Color.Black.copy(alpha = 0.42f),
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
// deck height used to size the artwork (BitChord reserves a little more than the controls take)
private val BcDeckHeight = 367.dp
// actual controls-deck height (lyrics list ends here)
private val BcRealDeckHeight = 327.dp
private val BcLyricEasing = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private val BcLyricAlpha = floatArrayOf(1f, 0.74f, 0.6f, 0.48f, 0.4f, 0.34f)
private val BcLyricBlur = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)
private val BcThumb = 54.dp

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

    // ---- lyrics (LRCLIB) ----
    var lyricsOpen by remember { mutableStateOf(false) }
    val lp by animateFloatAsState(
        targetValue = if (lyricsOpen) 1f else 0f,
        animationSpec = tween(420, easing = BcLyricEasing),
        label = "bcLyricsT",
    )
    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    val lyrics by produceState<BcLyrics>(BcLyrics.Loading, title, artist, st.duration > 0) {
        value = BcLyrics.Loading
        if (st.duration <= 0) delay(1200)
        value = BcLyricsRepository.load(title, artist, (st.duration / 1000L).toInt())
    }
    val found = lyrics as? BcLyrics.Found
    var lyricPos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, st.isPlaying, found?.synced) {
        if (found?.synced == true) {
            while (isActive) {
                lyricPos = player.currentPosition
                delay(if (st.isPlaying) 80L else 250L)
            }
        }
    }
    val activeIndex by remember(found) {
        derivedStateOf {
            val f = found
            if (f == null || !f.synced) -1 else f.lines.indexOfLast { it.timeMs <= lyricPos }
        }
    }

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
        // Bottom darkening: BitChord's blurred artwork usually ends in a dark floor, which gives the
        // controls a near-black base. Reproduced explicitly so it looks the same for any artwork.
        Canvas(Modifier.fillMaxSize()) { drawRect(brush = BcBottomShade) }

        // ---- hero artwork: full width, bottom 42% eased out with smootherStep (DstIn mask).
        //      Opening lyrics collapses it into a 54dp thumbnail, exactly like BitChord. ----
        val artLeft = dpLerp(0.dp, BcGutter, lp)
        val artTop = dpLerp(0.dp, topInset + BcDismissStrip + BcArtBoxTopPad, lp)
        val artW = dpLerp(maxWidth, BcThumb, lp)
        val artH = dpLerp(heroHeight, BcThumb, lp)
        val artCorner = dpLerp(0.dp, 8.dp, lp)
        val fadeShown = 1f - lp
        Box(
            Modifier
                .offset(x = artLeft, y = artTop)
                .size(artW, artH)
                .clip(RoundedCornerShape(artCorner))
                .then(if (lp < 0.5f) dragModifier else Modifier.clickable { lyricsOpen = false })
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .background(Color.Black.copy(alpha = 0.18f))
                .drawWithContent {
                    drawContent()
                    if (fadeShown > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = Array(BcFadeCurveStops + 1) { i ->
                                    val t = i / BcFadeCurveStops.toFloat()
                                    t to Color.Black.copy(alpha = 1f - fadeShown * bcSmootherStep(t))
                                },
                                startY = size.height * (1f - BcHeroFadeFraction),
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(LuMusicIcon, null, tint = BcWhite.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
            }
        }

        // ---- lyrics list (between the header and the controls deck) ----
        if (lyricsOpen || lp > 0.01f) {
            val listTop = topInset + BcDismissStrip + BcArtBoxTopPad + BcThumb + 8.dp
            val listHeight = (maxHeight - listTop - BcRealDeckHeight - bottomInset).coerceAtLeast(80.dp)
            Box(
                Modifier
                    .offset(y = listTop)
                    .fillMaxWidth()
                    .height(listHeight)
                    .graphicsLayer { alpha = lp },
            ) {
                when (val l = lyrics) {
                    is BcLyrics.Found -> BcLyricsList(
                        lines = l.lines,
                        synced = l.synced,
                        activeIndex = activeIndex,
                        onSeek = { ms -> player.seekTo(ms); st.position = ms },
                        modifier = Modifier.fillMaxSize(),
                    )
                    BcLyrics.Loading -> BcLyricsSkeleton()
                    BcLyrics.None -> Box(
                        Modifier.fillMaxSize().padding(horizontal = BcGutter),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("Lyrics not available", color = dim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
                .offset(y = dpLerp(headerTop, topInset + BcDismissStrip + BcArtBoxTopPad - 3.dp, lp))
                .fillMaxWidth()
                .height(BcHeaderHeight)
                .padding(start = dpLerp(BcGutter, BcGutter + BcThumb + 12.dp, lp), end = BcGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 28.dp.toPx().coerceAtMost(size.width)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black, 1f to Color.Transparent,
                                startX = size.width - fade, endX = size.width,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                verticalArrangement = Arrangement.Center,
            ) {
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
            // lyric strip slot (BitChord: current line + chevron, tap opens lyrics)
            Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    error != null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.player_retry), color = BcWhite) }
                    }
                    lyricsOpen -> Text(
                        text = if (found != null) "Lyrics from ${found.source}" else "",
                        color = dim,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    else -> BcLyricStrip(lyrics, activeIndex) { tick(); lyricsOpen = true }
                }
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

            // volume bar removed, but its slot (12 + 32 + 6 dp) is kept so nothing else moves
            Spacer(Modifier.height(50.dp))

            // action row (BitChord default): lyrics | output (cast) | queue
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pillWidth = BcPillSegment * 2 + 1.dp
                val inset = ((maxWidth - BcActionSize * 2 - pillWidth) / 4).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        BcBottomGlyph(BcLyricsQuote, "Lyrics", highlighted = lyricsOpen) { tick(); lyricsOpen = !lyricsOpen }
                    }
                    BcBottomGlyph(Lucide.LuCast, "Audio output", highlighted = false, iconSize = 24.dp) { tick() }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        BcBottomGlyph(BcQueue, stringResource(R.string.player_playlist), highlighted = false) { tick(); onPlaylist() }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "This phone",
                color = dim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.65f),
            )
            Spacer(Modifier.height(26.dp))
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
    // BitChord blurs the same (square) cover that the hero shows. A wide video thumbnail would
    // otherwise be squeezed into the screen from its middle strip only, so its colours would not
    // match what is on screen: blur the centre square — exactly what the hero crop displays.
    val side = minOf(src.width, src.height)
    val square = if (src.width == src.height) src
    else Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scale = 128f / side
    val w = (side * scale).roundToInt().coerceAtLeast(2)
    val h = w
    val scaled = Bitmap.createScaledBitmap(square, w, h, true)
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


// ---------------------------------------------------------------------------------------------
// Lyrics UI (BitChord PlayerLyrics: 34sp ExtraBold lines, distance falloff in alpha + blur,
// active line eased forward, fading edges, tap a line to seek)
// ---------------------------------------------------------------------------------------------

@Composable
private fun BcLyricStrip(lyrics: BcLyrics, activeIndex: Int, onClick: () -> Unit) {
    var faded by remember(lyrics) { mutableStateOf(false) }
    LaunchedEffect(lyrics) {
        if (lyrics is BcLyrics.None) {
            delay(5_000)
            faded = true
        }
    }
    val goneAlpha by animateFloatAsState(if (faded) 0f else 1f, tween(900), label = "bcLyricGone")
    val style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp)

    when (lyrics) {
        BcLyrics.Loading -> Text(
            "Loading lyrics…",
            style = style, color = BcWhite.copy(alpha = 0.45f), maxLines = 1,
            modifier = Modifier.offset(y = 11.dp).padding(vertical = 4.dp),
        )
        BcLyrics.None -> Text(
            "Lyrics not available",
            style = style, color = BcWhite.copy(alpha = 0.45f * goneAlpha), maxLines = 1,
            modifier = Modifier.offset(y = 11.dp).padding(vertical = 4.dp),
        )
        is BcLyrics.Found -> {
            val line = if (lyrics.synced) lyrics.lines.getOrNull(activeIndex) else null
            val firstSung = remember(lyrics) { lyrics.lines.indexOfFirst { !it.isGap } }
            val instrumental = lyrics.synced && (line == null || line.isGap)
            val introLine = remember(lyrics) { BcIntroLines.random() }
            val text = when {
                !lyrics.synced -> "Lyrics"
                instrumental && firstSung >= 0 && activeIndex < firstSung -> introLine
                instrumental -> "Instrumental"
                else -> line!!.text
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset(y = 11.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp),
            ) {
                if (instrumental || !lyrics.synced) {
                    Icon(BcInstrumental, null, tint = BcWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Crossfade(
                    targetState = text,
                    animationSpec = tween(220),
                    label = "bcStripLine",
                    modifier = Modifier.weight(1f, fill = false),
                ) { shown ->
                    Text(shown, style = style, color = BcWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(6.dp))
                Icon(BcChevronRight, null, tint = BcWhite.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun BcLyricsList(
    lines: List<BcLyricLine>,
    synced: Boolean,
    activeIndex: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (synced && activeIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(activeIndex)
        }
    }
    BoxWithConstraints(modifier) {
        val bottomRoom = maxHeight * 0.8f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().bcFadingEdges(),
            contentPadding = PaddingValues(top = 40.dp, bottom = bottomRoom, start = BcGutter, end = BcGutter),
        ) {
            itemsIndexed(lines) { index, line ->
                if (line.isGap) {
                    if (synced) BcGapRow(active = index == activeIndex) else Spacer(Modifier.height(18.dp))
                } else {
                    BcLyricRow(
                        line = line,
                        synced = synced,
                        distance = if (synced && activeIndex >= 0) kotlin.math.abs(index - activeIndex) else 0,
                        active = synced && index == activeIndex,
                        onClick = { if (synced) onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BcLyricRow(line: BcLyricLine, synced: Boolean, distance: Int, active: Boolean, onClick: () -> Unit) {
    val targetAlpha = if (!synced) 0.9f else BcLyricAlpha[distance.coerceIn(0, BcLyricAlpha.lastIndex)]
    val alpha by animateFloatAsState(targetAlpha, tween(400, easing = BcLyricEasing), label = "bcLineAlpha")
    val scale by animateFloatAsState(if (!synced || active) 1f else 0.98f, tween(400, easing = BcLyricEasing), label = "bcLineScale")
    val blur by animateDpAsState(
        if (!synced) 0.dp else BcLyricBlur[distance.coerceIn(0, BcLyricBlur.lastIndex)],
        tween(400, easing = BcLyricEasing), label = "bcLineBlur",
    )
    Text(
        text = line.text,
        color = BcWhite,
        fontSize = if (synced) 34.sp else 30.sp,
        lineHeight = if (synced) 41.sp else 38.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                this.alpha = alpha
            }
            .blur(blur, BlurredEdgeTreatment.Unbounded)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun BcGapRow(active: Boolean) {
    val alpha by animateFloatAsState(if (active) 0.9f else 0.25f, tween(400), label = "bcGap")
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { Box(Modifier.size(13.dp).clip(CircleShape).background(BcWhite.copy(alpha = alpha))) }
    }
}

@Composable
private fun BcLyricsSkeleton() {
    val transition = rememberInfiniteTransition(label = "bcSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "bcSkeletonAlpha",
    )
    val blocks = remember {
        listOf(
            floatArrayOf(0.97f, 0.54f),
            floatArrayOf(0.92f, 0.99f, 0.41f),
            floatArrayOf(0.68f),
            floatArrayOf(0.95f, 0.73f),
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = BcGutter, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(35.dp),
    ) {
        blocks.forEach { block ->
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                block.forEach { w ->
                    Box(Modifier.fillMaxWidth(w).height(26.dp).clip(RoundedCornerShape(8.dp)).background(BcWhite.copy(alpha = alpha)))
                }
            }
        }
    }
}

private fun Modifier.bcFadingEdges(top: Dp = 28.dp, bottom: Dp = 56.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = top.toPx()),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - bottom.toPx(), endY = size.height),
            blendMode = BlendMode.DstIn,
        )
    }


private val BcIntroLines = listOf(
    "The beat is landing", "The song is starting", "Warming up", "Setting the mood",
    "Bass first, words later", "Wait for it", "Feel that build", "Just the groove for now",
    "The hook is on the way", "Let it breathe", "Cue the vocals", "First notes in",
)

/** BitChordIcons.Instrumental (double note) */
private val BcInstrumental: ImageVector by lazy {
    ImageVector.Builder(
        name = "bc_instrumental", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M19.952 1.651a.75.75 0 0 1 .298.599V16.303a3 3 0 0 1-2.176 2.884l-1.32.377a2.553 2.553 0 1 1-1.403-4.909" +
                "l2.311-.66a1.5 1.5 0 0 0 1.088-1.442V6.994l-9 2.572v9.737a3 3 0 0 1-2.176 2.884l-1.32.377" +
                "a2.553 2.553 0 1 1-1.402-4.909l2.31-.66a1.5 1.5 0 0 0 1.088-1.442V5.25a.75.75 0 0 1 .544-.721" +
                "l10.5-3a.75.75 0 0 1 .658.122Z",
        ).toNodes(),
        pathFillType = PathFillType.EvenOdd,
        fill = BcStroke,
    ).build()
}

/** BitChordIcons.ChevronRight */
private val BcChevronRight: ImageVector by lazy {
    ImageVector.Builder(
        name = "bc_chevron_right", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = BcStroke, strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9.5f, 6.2f)
            lineTo(15.3f, 12f)
            lineTo(9.5f, 17.8f)
        }
    }.build()
}

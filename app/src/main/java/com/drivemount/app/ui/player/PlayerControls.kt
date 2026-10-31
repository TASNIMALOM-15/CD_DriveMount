package com.drivemount.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import kotlinx.coroutines.delay
import java.util.Locale

/** Player-এর অবস্থা Compose-এ আনার ছোট আয়না: যেকোনো বদল হলেই sync() */
@Stable
class PlayerState(private val player: Player) {
    var isPlaying by mutableStateOf(false)
    var state by mutableIntStateOf(Player.STATE_IDLE)
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(0)
    var title by mutableStateOf("")
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var videoWidth by mutableIntStateOf(0)
    var videoHeight by mutableIntStateOf(0)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var hasNext by mutableStateOf(false)
    var hasPrevious by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    fun sync() {
        isPlaying = player.isPlaying
        state = player.playbackState
        index = player.currentMediaItemIndex
        count = player.mediaItemCount
        title = player.mediaMetadata.title?.toString().orEmpty()
        repeat = player.repeatMode
        shuffle = player.shuffleModeEnabled
        speed = player.playbackParameters.speed
        videoWidth = player.videoSize.width
        videoHeight = player.videoSize.height
        tracks = player.currentTracks
        hasNext = player.hasNextMediaItem()
        hasPrevious = player.hasPreviousMediaItem()
        syncPosition()
    }

    fun syncPosition() {
        position = player.currentPosition.coerceAtLeast(0L)
        buffered = player.bufferedPosition.coerceAtLeast(0L)
        duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0L) }
    }
}

@Composable
fun rememberPlayerState(player: Player): PlayerState {
    val st = remember(player) { PlayerState(player).also { it.sync() } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                st.sync()
            }
        }
        player.addListener(listener)
        st.sync()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            st.syncPosition()
            delay(if (st.isPlaying) 250 else 500)
        }
    }
    return st
}

/**
 * নিজস্ব seek bar: চলা অংশ, buffer-করা অংশ ও টানার হাতল দেখায়।
 * টানার সময় onScrub বর্তমান লক্ষ্য জানায় (শেষে null), ছাড়লে বা ট্যাপে onCommit।
 */
@Composable
fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    accent: Color,
    onScrub: (Long?) -> Unit,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = Color.White,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = when {
        dragging -> dragFraction
        duration > 0 -> (position.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) onCommit((offset.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction * duration).toLong())
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction * duration).toLong())
                    },
                    onDragEnd = {
                        dragging = false
                        onCommit((dragFraction * duration).toLong())
                        onScrub(null)
                    },
                    onDragCancel = {
                        dragging = false
                        onScrub(null)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackH = 4.dp.toPx()
            val top = (size.height - trackH) / 2f
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            drawRoundRect(track.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, trackH), radius)
            drawRoundRect(track.copy(alpha = 0.45f), Offset(0f, top), Size(size.width * bufferedFraction, trackH), radius)
            drawRoundRect(accent, Offset(0f, top), Size(size.width * fraction, trackH), radius)
            val thumbR = (if (dragging) 10.dp else 7.dp).toPx()
            drawCircle(accent, thumbR, Offset(size.width * fraction, size.height / 2f))
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

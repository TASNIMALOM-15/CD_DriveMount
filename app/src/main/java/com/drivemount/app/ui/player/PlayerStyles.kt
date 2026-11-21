package com.drivemount.app.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.Headphones as LuHeadphones
import com.composables.icons.lucide.Volume2 as LuVolume2
import com.composables.icons.lucide.X as LuX
import com.composables.icons.lucide.Bookmark as LuBookmark
import com.composables.icons.lucide.ChevronDown as LuChevronDown
import com.composables.icons.lucide.Ellipsis as LuEllipsis
import com.composables.icons.lucide.ListVideo as LuListVideo
import com.composables.icons.lucide.MessageSquareText as LuMessageSquareText
import com.composables.icons.lucide.Maximize as LuMaximize
import com.composables.icons.lucide.Minimize as LuMinimize
import com.composables.icons.lucide.Settings as LuSettings
import com.composables.icons.lucide.ThumbsDown as LuThumbsDown
import com.composables.icons.lucide.ThumbsUp as LuThumbsUp
import com.composables.icons.lucide.Gauge as LuGauge
import com.composables.icons.lucide.ListMusic as LuListMusic
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause as LuPause
import com.composables.icons.lucide.PictureInPicture2 as LuPictureInPicture2
import com.composables.icons.lucide.Play as LuPlay
import com.composables.icons.lucide.Ratio as LuRatio
import com.composables.icons.lucide.RotateCcw as LuRotateCcw
import com.composables.icons.lucide.RotateCw as LuRotateCw
import com.composables.icons.lucide.Share2 as LuShare2
import com.composables.icons.lucide.SkipBack as LuSkipBack
import com.composables.icons.lucide.SkipForward as LuSkipForward

/** স্টাইল-ভেদে নিয়ন্ত্রণ ওভারলের কলব্যাক: সব কাজ PlayerScreen-এর একই ইঞ্জিনে যায় */
class StyledActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onPrev: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekRel: (Long) -> Unit,
    val onNext: () -> Unit,
    val onScrub: (Long?) -> Unit,
    val onCommit: (Long) -> Unit,
    val onLock: () -> Unit,
    val onSpeed: () -> Unit,
    val onAspect: () -> Unit,
    val onTracks: () -> Unit,
    val onPip: () -> Unit,
    val onRotate: () -> Unit,
    val onPlaylist: () -> Unit,
    // YouTube Style-এর বাড়তি
    val onLike: () -> Unit = {},
    val onDislike: () -> Unit = {},
    val onAudio: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onCc: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val sideMode: Boolean = false,
    val portrait: Boolean = false,
    val subtitle: String = "",
    val nextThumb: (@Composable () -> Unit)? = null,
)

private const val STEP = 10_000L
private val YtRed = Color(0xFFFF0000)

@Composable
fun StyledControls(style: PlayerStyle, st: PlayerState, scrubTarget: Long, speedText: String, a: StyledActions) {
    if (style == PlayerStyle.GLASS) GlassControls(st, scrubTarget, speedText, a) else YouTubeControls(st, scrubTarget, speedText, a)
}

@Composable
private fun Ico(icon: ImageVector, desc: String, size: Int = 24, tint: Color = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, desc, tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size.dp))
    }
}

// ---------------------------------------------------------------- YouTube

private val YtDark = Color(0x99000000)

@Composable
private fun YouTubeControls(st: PlayerState, scrubTarget: Long, speedText: String, a: StyledActions) {
    val shade = Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
    val shadeUp = Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 560.dp
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp).background(shade))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(shadeUp))
        // ওপর: ⌄ | শিরোনাম + চ্যানেল | CC | ⚙
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Lucide.LuChevronDown, "Back", 30, onClick = a.onBack)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(st.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a.subtitle.isNotEmpty()) Text(a.subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CcButton(a.onCc)
            Ico(Lucide.LuSettings, "Settings", 26, onClick = a.onSettings)
        }
        // মাঝ: ⏮ ▶ ⏭ (কোনো বৃত্ত ছাড়া, YouTube-এর মতো)
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 96.dp else 48.dp)) {
            Ico(Lucide.LuSkipBack, "Previous", 34, enabled = st.hasPrevious || st.position > 3_000, onClick = a.onPrev)
            IconButton(onClick = a.onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(if (st.isPlaying) Lucide.LuPause else Lucide.LuPlay, "Play/Pause", tint = Color.White, modifier = Modifier.size(60.dp))
            }
            Ico(Lucide.LuSkipForward, "Next", 34, enabled = st.hasNext, onClick = a.onNext)
        }
        // নিচ: সময়-পিল + ফুলস্ক্রিন-বাটন, লাল প্রগ্রেস বার, অ্যাকশন সারি
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(if (scrubTarget >= 0) scrubTarget else st.position) + " / " + formatTime(st.duration),
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(YtDark).padding(horizontal = 12.dp, vertical = 6.dp),
                )
                if (st.speed != 1f) Text(speedText, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                Box(Modifier.weight(1f))
                IconButton(onClick = if (a.portrait) a.onRotate else a.onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(YtDark)) {
                    Icon(if (a.portrait) Lucide.LuMaximize else Lucide.LuMinimize, if (a.portrait) "Fullscreen" else "Exit fullscreen", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            SeekBar(
                position = st.position, buffered = st.buffered, duration = st.duration,
                accent = YtRed, onScrub = a.onScrub, onCommit = a.onCommit,
                trackHeight = 4.dp,
            )
            if (!a.sideMode) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(YtDark).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(Lucide.LuThumbsUp, "Like", tint = if (a.liked) YtBlue else Color.White, onClick = a.onLike)
                        Ico(Lucide.LuThumbsDown, "Dislike", tint = if (a.disliked) YtBlue else Color.White, onClick = a.onDislike)
                        Ico(Lucide.LuMessageSquareText, "Audio", onClick = a.onAudio)
                        Ico(Lucide.LuBookmark, "Save to playlist", onClick = a.onSave)
                        Ico(Lucide.LuShare2, "Share", onClick = a.onShare)
                        Ico(Lucide.LuEllipsis, "More", onClick = a.onMore)
                    }
                    Box(Modifier.weight(1f))
                    Row(Modifier.clip(RoundedCornerShape(50)).background(YtDark).clickable(onClick = a.onPlaylist).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (wide && a.nextThumb != null) {
                            Text("More videos", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                            Box(Modifier.size(width = 76.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp))) { a.nextThumb.invoke() }
                        }
                        Ico(Lucide.LuListVideo, "Playlist", onClick = a.onPlaylist)
                    }
                }
            }
        }
    }
}

private val YtBlue = Color(0xFF3EA6FF)

/** YouTube-এর মতো "CC" বাটন (আইকন-লাইব্রেরির ওপর নির্ভর না করে) */
@Composable
private fun CcButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(Modifier.size(width = 30.dp, height = 22.dp).border(2.dp, Color.White, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text("CC", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------------------------------------------------------- Liquid Glass

private val GlassFill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f)))
private val GlassEdge = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))))
private val Aurora = listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFF7C4DFF))

@Composable
private fun Glass(shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(shape).background(Color.Black.copy(alpha = 0.18f)).background(GlassFill).border(GlassEdge, shape)) { content() }
}

@Composable
private fun GlassCircle(size: Int, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Glass(CircleShape, Modifier.size(size.dp)) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun GlassTextPill(text: String, onClick: () -> Unit) {
    Glass(RoundedCornerShape(50)) {
        Text(
            text, color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/** ⟲10 / ⟳10: ঘোরানো-তীরের মাঝে "10" */
@Composable
private fun Replay10(forward: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Icon(if (forward) Lucide.LuRotateCw else Lucide.LuRotateCcw, if (forward) "Forward 10" else "Back 10", tint = Color.White, modifier = Modifier.size(38.dp))
        Text("10", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GlassControls(st: PlayerState, scrubTarget: Long, speedText: String, a: StyledActions) {
    // একটিমাত্র হালকা অ্যানিমেশন: ধীরে সরা অরোরা-আভা (শুধু নিয়ন্ত্রণ দেখানোর সময়)
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 560.dp
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.linearGradient(Aurora.map { it.copy(alpha = 0.16f) }, Offset(w * phase - w, h), Offset(w * phase + w, 0f)), size = androidx.compose.ui.geometry.Size(w, h * 0.2f))
            drawRect(
                Brush.linearGradient(Aurora.map { it.copy(alpha = 0.22f) }.reversed(), Offset(w * (1 - phase) - w, 0f), Offset(w * (1 - phase) + w, h)),
                topLeft = Offset(0f, h * 0.7f), size = androidx.compose.ui.geometry.Size(w, h * 0.3f),
            )
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), startY = h * 0.55f, endY = h))
        }
        // ওপর-বাঁ: ✕ + [অ্যাসপেক্ট, PiP, ঘোরানো, লক, শেয়ার]
        Row(
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassCircle(44, a.onBack) { Icon(Lucide.LuX, "Close", tint = Color.White, modifier = Modifier.size(22.dp)) }
            Glass(RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(Lucide.LuRatio, "Aspect", 22, onClick = a.onAspect)
                    Ico(Lucide.LuPictureInPicture2, "PiP", 22, onClick = a.onPip)
                    Ico(Lucide.LuRotateCw, "Rotate", 22, onClick = a.onRotate)
                    Ico(Lucide.LuLock, "Lock", 22, onClick = a.onLock)
                    Ico(Lucide.LuShare2, "Share", 22, onClick = a.onShare)
                }
            }
        }
        // ওপর-ডান: ভলিউম পিল
        if (wide) {
            Glass(RoundedCornerShape(50), Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 14.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeekBar(
                        position = am.getStreamVolume(AudioManager.STREAM_MUSIC).toLong(), buffered = 0L, duration = maxVol.toLong(),
                        accent = Color.White, showThumb = false, trackHeight = 5.dp,
                        onScrub = { v -> if (v != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0) },
                        onCommit = { v -> am.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0) },
                        modifier = Modifier.width(110.dp),
                    )
                    Icon(Lucide.LuVolume2, "Volume", tint = Color.White, modifier = Modifier.padding(start = 10.dp).size(22.dp))
                }
            }
        }
        // মাঝ: ⟲10 | ⏸ | ⟳10
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 36.dp else 20.dp)) {
            GlassCircle(66, { a.onSeekRel(-STEP) }) { Replay10(false) }
            GlassCircle(92, a.onPlayPause) {
                Icon(if (st.isPlaying) Lucide.LuPause else Lucide.LuPlay, "Play/Pause", tint = Color.White, modifier = Modifier.size(46.dp))
            }
            GlassCircle(66, { a.onSeekRel(STEP) }) { Replay10(true) }
        }
        // নিচ: শিরোনাম + ডানে [সাবটাইটেল, অডিও, গতি] পিল, তার নিচে পাতলা বার ও বাটন
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    if (a.subtitle.isNotEmpty()) Text(a.subtitle, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(st.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Glass(RoundedCornerShape(50)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
                        Ico(Lucide.LuMessageSquareText, "Subtitles", 22, onClick = a.onCc)
                        Ico(Lucide.LuHeadphones, "Audio", 22, onClick = a.onAudio)
                        Ico(Lucide.LuGauge, "Speed $speedText", 22, onClick = a.onSpeed)
                    }
                }
            }
            SeekBar(
                position = st.position, buffered = st.buffered, duration = st.duration,
                accent = Color.White, showThumb = false, onScrub = a.onScrub, onCommit = a.onCommit,
                accentBrush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFBFE6FF), Color(0xFFE1C8FF))),
                trackHeight = 6.dp,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextPill("Info", a.onMore)
                GlassTextPill("Playlist", a.onPlaylist)
                if (st.hasNext) GlassTextPill("Next", a.onNext)
                Box(Modifier.weight(1f))
                Text(
                    formatTime(if (scrubTarget >= 0) scrubTarget else st.position) + " / " + formatTime(st.duration),
                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

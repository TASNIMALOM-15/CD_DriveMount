package com.drivemount.app.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drivemount.app.R
import com.drivemount.app.data.transfer.TransferStatus
import com.drivemount.app.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

/** ভেতরের সবকিছু সাদা-কালো (ধূসর) করে আঁকে */
fun Modifier.grayscale(): Modifier = drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
        drawContent()
        canvas.restore()
    }
}

/** শুধু বাম দিক থেকে [fraction] (০–১) অংশ আঁকে; ডানের অংশ কাটা */
fun Modifier.revealFraction(fraction: () -> Float): Modifier = drawWithContent {
    val w = size.width * fraction().coerceIn(0f, 1f)
    clipRect(left = 0f, top = 0f, right = w, bottom = size.height) {
        this@drawWithContent.drawContent()
    }
}

/**
 * আপলোড চলা ফাইলের ছবি: নিচে পুরোটা ধূসর, তার ওপরে রঙিন ছবি বাম থেকে ডানে [UploadGhost.fraction] পর্যন্ত।
 * ফোনের মূল ফাইল থেকে থাম্বনেল বানায় (ছবি/ভিডিও); না পারলে [fallback] আইকন।
 * আকার ডাকার জায়গা থেকে [modifier]-এ আসে।
 */
@Composable
fun GhostThumbnail(
    ghost: UploadGhost,
    kind: ThumbKind?,
    decodePx: Int,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, ghost.entry.path, ghost.thumbUri, ghost.thumbPath, kind) {
        value = if (kind == ThumbKind.IMAGE || kind == ThumbKind.VIDEO) {
            withContext(Dispatchers.IO) {
                LocalThumbs.load(context, ghost.thumbUri, ghost.thumbPath, kind == ThumbKind.VIDEO, decodePx)
            }
        } else null
    }
    // হালনাগাদ আসে প্রায় প্রতি সেকেন্ডে; মাঝখানটা মসৃণ করতে রৈখিক অ্যানিমেশন
    val fill = animateFloatAsState(ghost.fraction, tween(900, easing = LinearEasing), label = "ghostFill")
    Box(modifier) {
        Box(Modifier.matchParentSize().grayscale()) { GhostContent(bmp, fallback) }
        Box(Modifier.matchParentSize().revealFraction { fill.value }) { GhostContent(bmp, fallback) }
    }
}

@Composable
private fun GhostContent(bmp: Bitmap?, fallback: @Composable () -> Unit) {
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() }
    }
}

/** ঘরের কোণে ছোট শতাংশ-চিহ্ন: চলার সময় "৪২%", লাইনে/প্রস্তুতিতে "…" */
@Composable
fun GhostBadge(ghost: UploadGhost, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val text = if (ghost.status == TransferStatus.RUNNING || ghost.status == TransferStatus.DONE) {
        "${(ghost.fraction * 100).toInt().coerceIn(0, 100)}%"
    } else "…"
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurface,
        modifier = modifier
            .padding(4.dp)
            .background(colors.surfaceContainerHighest.copy(alpha = 0.92f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** আপলোড-পূর্ব ধাপের নাম ("Making thumbnail…" ইত্যাদি) */
@Composable
fun stageText(stage: Int): String = stringResource(
    when (stage) {
        com.drivemount.app.data.transfer.TransferStage.THUMB -> R.string.tr_stage_thumb
        com.drivemount.app.data.transfer.TransferStage.SEEK -> R.string.tr_stage_seek
        com.drivemount.app.data.transfer.TransferStage.FAST -> R.string.tr_stage_fast
        com.drivemount.app.data.transfer.TransferStage.UPLOAD -> R.string.tr_stage_upload
        else -> R.string.tr_stage_working
    },
)

/** সারির নিচের লাইনের লেখা: "Waiting in line" / "Preparing…" / "12 MB of 80 MB · 3 MB/s" */
@Composable
fun ghostSubtitle(g: UploadGhost): String = when (g.status) {
    TransferStatus.QUEUED -> stringResource(R.string.tr_waiting)
    TransferStatus.PREPARING -> g.stage?.let { stageText(it) } ?: stringResource(R.string.tr_preparing)
    TransferStatus.RUNNING ->
        if (g.total > 0) {
            stringResource(R.string.tr_running_fmt, formatBytes(g.done), formatBytes(g.total), formatBytes(g.speed))
        } else {
            g.stage?.let { stageText(it) } ?: stringResource(R.string.tr_working)
        }
    TransferStatus.PAUSED -> stringResource(R.string.tr_paused)
    else -> stringResource(R.string.tr_done)
}

/** ফোনের মূল ফাইল (content URI বা পথ) থেকে ছোট থাম্বনেল; ডিস্কে কিছু জমা হয় না */
object LocalThumbs {
    fun load(context: Context, uri: String?, path: String?, video: Boolean, px: Int): Bitmap? = runCatching {
        if (uri == null && path == null) null
        else if (video) loadVideo(context, uri, path, px) else loadImage(context, uri, path, px)
    }.getOrNull()

    private fun loadVideo(context: Context, uri: String?, path: String?, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            if (uri != null) r.setDataSource(context, Uri.parse(uri)) else r.setDataSource(path)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val at = durMs * 1000L / 10 // ১০% জায়গার ফ্রেম (শুরুর কালো ফ্রেম এড়াতে)
            val frame = r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            return scaleDown(frame, px)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun loadImage(context: Context, uri: String?, path: String?, px: Int): Bitmap? {
        fun open(): InputStream? =
            if (uri != null) context.contentResolver.openInputStream(Uri.parse(uri)) else path?.let { FileInputStream(it) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val degrees = runCatching {
            open()?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun scaleDown(src: Bitmap, px: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= px) return src
        val k = px.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
    }
}

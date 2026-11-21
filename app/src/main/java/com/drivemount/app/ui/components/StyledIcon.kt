package com.drivemount.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.drivemount.app.data.IconStyle
import java.io.File

/** টপ-বারের আইকনের মাপ: null = ডিফল্ট (২৪dp)। [TopBarIcons]-এর ভেতরের সব [Icon] এই মাপে আঁকা হয় (বাটনের ট্যাপ-এলাকা ৪৮dp-ই থাকে) */
val LocalIconSize = staticCompositionLocalOf<Dp?> { null }

/** টপ-বার ছোট আইকনে: `topBar = { TopBarIcons { TopAppBar(...) } }`। আলাদা মাপ দেওয়া আইকনে (modifier-এ size) প্রভাব নেই */
@Composable
fun TopBarIcons(size: Dp = 20.dp, content: @Composable () -> Unit) {
    // Library ট্যাবে নিচে স্ক্রল করলে (collapse 0→1) টপ-বারের ব্যাকগ্রাউন্ড ট্রান্সপারেন্ট হয় ও জায়গা ছেড়ে দেয়
    // (কনটেন্ট ওপরে উঠে আসে); আইকনগুলো থাকে। উপরে chip-সারির জন্য [LocalLibHeader.reserve] জায়গাও এখানে রাখা হয়।
    val hdr = LocalLibHeader.current
    if (hdr == null) {
        CompositionLocalProvider(LocalIconSize provides size, content = content)
        return
    }
    // Library: ব্যাকগ্রাউন্ড সবসময় ট্রান্সপারেন্ট (কালো অংশ নেই); chip-সারির ঠিক নিচে বসে, ওপর-নিচের ফাঁকা জায়গা ছাঁটা
    val cs = MaterialTheme.colorScheme
    val scheme = cs.copy(surface = cs.surface.copy(alpha = 0f), background = cs.background.copy(alpha = 0f))
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Box(
            Modifier.graphicsLayer {
                alpha = 1f - (if (hdr.bottom.value) 0f else hdr.hide.floatValue)
            }.layout { m, constraints ->
                val p = m.measure(constraints)
                val f = if (hdr.bottom.value) 0f else hdr.hide.floatValue
                val full = hdr.reserve.intValue
                // हেডার লুকালে জায়গাও ছাড়ে: কনটেন্ট ওপরে উঠে আসে (পিছনে ফাঁকা/কালো অংশ থাকে না)
                val r = full - (f * (full - hdr.statusTop.intValue).coerceAtLeast(0)).toInt()
                val trim = 8.dp.roundToPx()
                val h = p.height - 2 * trim
                layout(p.width, r + (h * (1f - f)).toInt()) { p.placeRelative(0, r - trim - (h * f).toInt()) }
            },
        ) {
            CompositionLocalProvider(LocalIconSize provides size, content = content)
        }
    }
}

/** Library ट্যাবের হেডার অবস্থা: [reserve] = উপরে chip-সারির উচ্চতা (px), [collapse] = ০..১ */
class LibHeader(
    val reserve: androidx.compose.runtime.MutableIntState,
    /** স্ক্রলে হেডার লুকানো: ০ = পুরো দেখা, ১ = লুকানো */
    val hide: androidx.compose.runtime.MutableFloatState = androidx.compose.runtime.mutableFloatStateOf(0f),
    /** স্ট্যাটাস-বারের উচ্চতা (px); লুকানোর দূরত্ব = reserve - statusTop */
    val statusTop: androidx.compose.runtime.MutableIntState = androidx.compose.runtime.mutableIntStateOf(0),
    /** ট্যাব-বার নিচে থাকলে true (তখন টপ-বার লুকায় না) */
    val bottom: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false),
)

val LocalLibHeader = staticCompositionLocalOf<LibHeader?> { null }

/** AppIcons-এর কী -> ব্যবহারকারীর বদলানো আইকন/রং (Theme থেকে সরবরাহ হয়) */
val LocalIconStyles = staticCompositionLocalOf<Map<String, IconStyle>> { emptyMap() }

/** [image] ("file:..." import বা তালিকার কী) থাকলে সেটা, নইলে [vector] আঁকে; [color] থাকলে তা-ই রং */
@Composable
internal fun RenderIcon(
    image: String?,
    vector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    color: Color?,
) {
    if (image != null && image.startsWith(FabIcons.FILE_PREFIX)) {
        val ctx = LocalContext.current
        val file = remember(image) { File(File(ctx.filesDir, FabIcons.DIR), image.removePrefix(FabIcons.FILE_PREFIX)) }
        if (file.exists()) {
            val request = remember(file) {
                ImageRequest.Builder(ctx).data(file).decoderFactory(SvgDecoder.Factory()).build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = color?.let { ColorFilter.tint(it) },
                modifier = modifier,
            )
            return
        }
    }
    androidx.compose.material3.Icon(AppIcons.byKey[image] ?: vector, contentDescription, modifier, color ?: tint)
}

/**
 * material3 Icon-এর বদলি: অ্যাপের সব আইকন এর মধ্য দিয়ে আঁকা হয়, তাই সেটিংসে কোনো আইকন বদলালে/রং দিলে
 * সেটা সব জায়গায় প্রযোজ্য হয়। তালিকায় নেই এমন আইকন আগের মতোই আঁকা হয়।
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val styles = LocalIconStyles.current
    val st = if (styles.isEmpty()) null else AppIcons.keyOf(imageVector)?.let { styles[it] }
    val forced = LocalIconSize.current
    val m = if (forced != null) modifier.size(forced) else modifier
    if (st == null) {
        androidx.compose.material3.Icon(imageVector, contentDescription, m, tint)
    } else {
        RenderIcon(st.image, imageVector, contentDescription, m, tint, st.color?.let { Color(it) })
    }
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)

/**
 * ফ্লোটিং বাটনের কাজ-ভিত্তিক আইকন: [key] (ওই কাজের নিজস্ব আইকন) ও [color] আগে;
 * না থাকলে ডিফল্ট আইকনের সাধারণ (অ্যাপজুড়ে) সাজ।
 */
@Composable
fun FabIcon(
    key: String?,
    default: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    color: Int? = null,
) {
    val styles = LocalIconStyles.current
    val g = if (styles.isEmpty()) null else AppIcons.keyOf(default)?.let { styles[it] }
    RenderIcon(key ?: g?.image, default, contentDescription, modifier, tint, (color ?: g?.color)?.let { Color(it) })
}

package com.drivemount.app.ui.components

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.Database as LuDatabase
import com.composables.icons.lucide.FileArchive as LuFileArchive
import com.composables.icons.lucide.FileAudio as LuFileAudio
import com.composables.icons.lucide.FileText as LuFileText
import com.composables.icons.lucide.FileVideo as LuFileVideo
import com.composables.icons.lucide.Folder as LuFolder
import com.composables.icons.lucide.HardDrive as LuHardDrive
import com.composables.icons.lucide.Image as LuImage
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.Smartphone as LuSmartphone
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.drivemount.app.ui.theme.LocalOriginalIcons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.drivemount.app.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun providerIcon(type: String): ImageVector = when (type) {
    "drive" -> Lucide.LuHardDrive
    "crypt" -> Lucide.LuLock
    "mega" -> Lucide.LuCloud
    "pcloud" -> Lucide.LuCloud
    "local" -> Lucide.LuSmartphone
    "webdav" -> Lucide.LuDatabase
    else -> Lucide.LuCloud
}

/** আসল (brand) আইকনের drawable; নেই এমন ধরনে null */
fun originalIconRes(type: String): Int? = when (type) {
    "drive" -> R.drawable.ic_cloud_drive
    "mega" -> R.drawable.ic_cloud_mega
    "pcloud" -> R.drawable.ic_cloud_pcloud
    "terabox", "teraplay" -> R.drawable.ic_cloud_terabox
    else -> null
}

/**
 * ক্লাউডের আইকন। Settings-এ "আসল আইকন" চালু থাকলে এবং ধরনটির আসল আইকন থাকলে সেটি,
 * নইলে সাধারণ Material আইকন (tint সহ)।
 */
@Composable
fun ProviderIcon(
    type: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val res = if (LocalOriginalIcons.current) originalIconRes(type) else null
    if (res != null) {
        Image(painter = painterResource(res), contentDescription = null, modifier = modifier)
    } else {
        Icon(imageVector = providerIcon(type), contentDescription = null, tint = tint, modifier = modifier)
    }
}

@Composable
fun providerLabel(type: String): String = when (type) {
    "drive" -> stringResource(R.string.provider_drive)
    "mega" -> stringResource(R.string.provider_mega)
    "pcloud" -> stringResource(R.string.provider_pcloud)
    "terabox" -> stringResource(R.string.provider_terabox)
    "teraplay" -> stringResource(R.string.provider_teraplay)
    "crypt" -> stringResource(R.string.provider_crypt)
    "local" -> stringResource(R.string.provider_local)
    "webdav" -> stringResource(R.string.provider_webdav)
    else -> type.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

/** দুই দশমিকে: 5.78 TB, 512.30 KB */
fun formatBytesPrecise(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.US, "%.2f %s", value, units[index])
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/** ফাইলের ধরন অনুযায়ী আইকন */
fun fileIcon(isDir: Boolean, name: String, mimeType: String): ImageVector {
    if (isDir) return Lucide.LuFolder
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mimeType.startsWith("image/") -> Lucide.LuImage
        mimeType.startsWith("video/") -> Lucide.LuFileVideo
        mimeType.startsWith("audio/") -> Lucide.LuFileAudio
        mimeType == "application/pdf" || ext == "pdf" -> Lucide.LuFileText
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> Lucide.LuFileArchive
        else -> Lucide.LuFileText
    }
}

/** composable নয় এমন জায়গার জন্য (যেমন সিস্টেমের Files অ্যাপের তালিকা) */
fun providerName(context: android.content.Context, type: String): String = when (type) {
    "drive" -> context.getString(R.string.provider_drive)
    "mega" -> context.getString(R.string.provider_mega)
    "pcloud" -> context.getString(R.string.provider_pcloud)
    "terabox" -> context.getString(R.string.provider_terabox)
    "crypt" -> context.getString(R.string.provider_crypt)
    "local" -> context.getString(R.string.provider_local)
    "webdav" -> context.getString(R.string.provider_webdav)
    else -> type.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** ভিডিও বা অডিও ফাইল (সরাসরি চালানো যাবে) */
fun isPlayable(name: String, mimeType: String): Boolean {
    val mime = com.drivemount.app.engine.guessMime(name, mimeType)
    return mime.startsWith("video/") || mime.startsWith("audio/")
}

package com.drivemount.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudCircle
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Storage
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
    "drive" -> Icons.Rounded.AddToDrive
    "crypt" -> Icons.Rounded.Lock
    "mega" -> Icons.Rounded.CloudQueue
    "pcloud" -> Icons.Rounded.CloudCircle
    "local" -> Icons.Rounded.PhoneAndroid
    "webdav" -> Icons.Rounded.Storage
    else -> Icons.Rounded.Cloud
}

/** আসল (brand) আইকনের drawable; নেই এমন ধরনে null */
fun originalIconRes(type: String): Int? = when (type) {
    "drive" -> R.drawable.ic_cloud_drive
    "mega" -> R.drawable.ic_cloud_mega
    "pcloud" -> R.drawable.ic_cloud_pcloud
    "terabox" -> R.drawable.ic_cloud_terabox
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
    if (isDir) return Icons.Rounded.Folder
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mimeType.startsWith("image/") -> Icons.Rounded.Image
        mimeType.startsWith("video/") -> Icons.Rounded.VideoFile
        mimeType.startsWith("audio/") -> Icons.Rounded.AudioFile
        mimeType == "application/pdf" || ext == "pdf" -> Icons.Rounded.PictureAsPdf
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.FolderZip
        else -> Icons.Rounded.Description
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

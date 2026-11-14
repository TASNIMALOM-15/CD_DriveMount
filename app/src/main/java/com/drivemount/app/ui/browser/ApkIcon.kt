package com.drivemount.app.ui.browser

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.sync.withPermit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry

/**
 * APK ফাইলের আসল আইকন (সাইডকার থেকে)। আইকন না থাকলে সাধারণ আইকন (fallback)।
 * কোনো নেটওয়ার্ক কল নেই: শুধু মেমরি/এনক্রিপ্টেড ডিস্ক-ক্যাশ থেকে ছোট ছবি ডিকোড।
 */
@Composable
fun ApkMetaIcon(
    account: AccountEntity,
    entry: RemoteEntry,
    size: Dp,
    modifier: Modifier = Modifier,
    aspect: Float? = null,
    fallback: @Composable () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DriveMountApp
    val sidecar = app.sidecar
    val remote = account.remoteName
    val fileSize = entry.size ?: -1L
    // নতুন আইকন এলে (আপলোড/প্যাক আমদানি) শুধু এই ছোট অংশ আবার চলে
    val version = sidecar.version.intValue
    val icon by produceState<Bitmap?>(sidecar.cachedIcon(remote, entry.path, fileSize), remote, entry.path, fileSize, version) {
        value = sidecar.iconFor(remote, entry.path, fileSize)
    }
    Box(
        modifier = modifier.then(if (aspect != null) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.size(size)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = icon
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().padding(4.dp),
            )
        } else {
            fallback()
        }
    }
}

/**
 * গানের কভার (সাইডকার থেকে)। কভার না থাকলে বা এখনো আসেনি, সাধারণ আইকন (fallback)।
 * কোনো নেটওয়ার্ক কল নেই; ট্যাগ-প্যাক আমদানি শেষ হলে (version বদলালে) নিজে থেকে কভার আসে।
 */
@Composable
fun AudioArtThumb(
    account: AccountEntity,
    entry: RemoteEntry,
    size: Dp,
    modifier: Modifier = Modifier,
    aspect: Float? = null,
    fallback: @Composable () -> Unit,
) {
    val app = LocalContext.current.applicationContext as DriveMountApp
    val sidecar = app.sidecar
    val remote = account.remoteName
    val fileSize = entry.size ?: -1L
    val version = sidecar.version.intValue
    val art by produceState<Bitmap?>(sidecar.cachedAudioArt(remote, entry.path, fileSize), remote, entry.path, fileSize, version) {
        var b = sidecar.audioArt(remote, entry.path, fileSize)
        // সাইডকার নেই (পুরোনো ফাইল): শুরুর ছোট অংশ পড়ে কভার বের করে শুধু ফোনে রাখা; ক্লাউডে কিছু লেখা হয় না
        if (b == null && account.backendType != "local" && !sidecar.hasEntry(remote, entry.path, fileSize, entry.name)) {
            b = AudioLazyArt.fetch(app, account, entry)
        }
        value = b
    }
    Box(
        modifier = modifier.then(if (aspect != null) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.size(size)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = art
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            fallback()
        }
    }
}

/** সাইডকার-ছাড়া গানের কভার: এক সময়ে একটি, প্রতি ফাইলে সেশনে একবার; মিটার্ড নেট বা ব্যাটারি-সেভারে চলে না */
private object AudioLazyArt {
    private val gate = kotlinx.coroutines.sync.Semaphore(1)
    private val tried = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun fetch(app: DriveMountApp, account: AccountEntity, entry: RemoteEntry): Bitmap? {
        val size = entry.size ?: return null
        val k = "${account.remoteName}|${entry.path}|$size"
        val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val pm = app.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (cm.isActiveNetworkMetered || pm.isPowerSaveMode) return null
        if (!tried.add(k)) return null
        return gate.withPermit {
            val url = runCatching {
                app.rcloneApi.streamUrl(
                    com.drivemount.app.engine.fsRootFor(account.remoteName, account.backendType),
                    entry.path,
                    com.drivemount.app.engine.guessMime(entry.name, entry.mimeType),
                    background = true,
                )
            }.getOrNull() ?: return@withPermit null
            runCatching { app.sidecar.cacheAudioLocally(account.remoteName, entry.path, size, url) }
            app.sidecar.audioArt(account.remoteName, entry.path, size)
        }
    }
}

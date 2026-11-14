package com.drivemount.app.data.meta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/** APK ফাইল থেকে অ্যাপের নাম, package, version ও আইকন বের করে (ইনস্টল না করেই, Android-এর নিজস্ব parser দিয়ে) */
object ApkMetaReader {

    private const val ICON_PX = 96
    private const val MAX_APK_BYTES = 1L shl 30

    /** blocking ও CPU-ভারী (কয়েকশো মিলিসেকেন্ড): পেছনের থ্রেড থেকে ডাকতে হবে। ব্যর্থ হলে null। */
    @Suppress("DEPRECATION")
    fun read(context: Context, file: File, name: String): SidecarEntry? {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_APK_BYTES) return null
        return runCatching {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return@runCatching null
            val ai = info.applicationInfo ?: return@runCatching null
            // ইনস্টল-না-হওয়া APK-র রিসোর্স (আইকন, নাম) পড়ার জন্য পথটি জানাতে হয়
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            val label = ai.loadLabel(pm).toString().take(120)
            val icon = encodeIcon(ai.loadIcon(pm))
            SidecarEntry(
                name = name,
                size = file.length(),
                label = label,
                pkg = (info.packageName ?: "").take(200),
                version = (info.versionName ?: "").take(60),
                icon = icon,
            )
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun encodeIcon(d: Drawable): ByteArray {
        val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val px = ICON_PX.toFloat()
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            // adaptive আইকনের দৃশ্যমান অংশ ১০৮dp-র মাঝের ৭২dp; সেটুকু ভরে আঁকা আর গোল-কোণা করে কাটা
            val path = Path().apply { addRoundRect(RectF(0f, 0f, px, px), px * 0.22f, px * 0.22f, Path.Direction.CW) }
            canvas.clipPath(path)
            val inset = ICON_PX / 4
            d.setBounds(-inset, -inset, ICON_PX + inset, ICON_PX + inset)
        } else {
            d.setBounds(0, 0, ICON_PX, ICON_PX)
        }
        d.draw(canvas)
        val out = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bmp.compress(format, 85, out)
        bmp.recycle()
        return out.toByteArray()
    }
}

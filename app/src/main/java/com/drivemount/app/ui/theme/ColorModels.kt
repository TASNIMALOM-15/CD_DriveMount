package com.drivemount.app.ui.theme

import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/** Color picker-এর ভেতরের রং: Hue 0..360, Saturation/Value/Alpha 0..1 */
data class HsvaColor(val h: Float, val s: Float, val v: Float, val a: Float) {

    fun toArgb(): Int = AColor.HSVToColor(
        (a * 255f).roundToInt().coerceIn(0, 255),
        floatArrayOf(h.coerceIn(0f, 359.99f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)),
    )

    /** Hue ও Alpha ছাড়া, S=V=1 অবস্থার পূর্ণ রং (spectrum-এর কোণার রং) */
    fun pureHue(): Color = Color(
        AColor.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.99f), 1f, 1f)),
    )

    companion object {
        /** ধূসর বা কালো রঙে Hue হারিয়ে যায়, তাই আগের Hue ধরে রাখা হয় */
        fun fromArgb(argb: Int, previous: HsvaColor? = null): HsvaColor {
            val hsv = FloatArray(3)
            AColor.colorToHSV(argb, hsv)
            val hue = if ((hsv[1] == 0f || hsv[2] == 0f) && previous != null) previous.h else hsv[0]
            return HsvaColor(hue, hsv[1], hsv[2], AColor.alpha(argb) / 255f)
        }
    }
}

/** "#RRGGBB" (alpha পূর্ণ হলে) অথবা "#AARRGGBB" */
fun argbToHex(argb: Int): String =
    if ((argb ushr 24) == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)

/** "#RRGGBB" বা "#AARRGGBB" (# ঐচ্ছিক)। ভুল হলে null। */
fun parseHex(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    val full = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return full.toLongOrNull(16)?.toInt()
}

/** কোনো রঙের ওপর লেখা/আইকনের জন্য পড়ার উপযোগী রং (সাদা বা গাঢ়) */
fun readableOn(background: Color): Color {
    val bg = background.toArgb()
    val whiteContrast = ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), bg)
    return if (whiteContrast >= 3.0) Color.White else Color(0xFF1B1B1F)
}

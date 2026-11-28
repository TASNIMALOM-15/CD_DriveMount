package com.drivemount.app.ui.videos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** ফটো ভিউয়ারের নিচের থাম্বনেইল-সারি: ছবির মাপ (dp) ও সারির সংখ্যা (১ বা ২) */
object PhotoStripPrefs {
    private const val FILE = "photo_strip_prefs"
    private var loaded = false
    var sizeDp by mutableIntStateOf(52); private set
    var rows by mutableIntStateOf(1); private set

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sizeDp = p.getInt("size", 52).coerceIn(24, 72)
        rows = p.getInt("rows", 1).coerceIn(1, 2)
    }

    fun setSize(ctx: Context, v: Int) {
        sizeDp = v.coerceIn(24, 72)
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("size", sizeDp).apply()
    }

    fun setRows(ctx: Context, v: Int) {
        rows = v.coerceIn(1, 2)
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("rows", rows).apply()
    }
}

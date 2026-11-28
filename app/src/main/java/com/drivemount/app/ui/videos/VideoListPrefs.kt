package com.drivemount.app.ui.videos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Videos ট্যাবের তালিকা-ধরন: TeraPlay-স্টাইল (১/২/৩ কলাম, সাইজ, ডিউরেশন, ৩-ডট) চালু/বন্ধ; বন্ধ = আগের ২-কলাম ধরন */
object VideoListPrefs {
    private const val FILE = "video_list_prefs"
    private var loaded = false
    var tera by mutableStateOf(true); private set
    var cols by mutableIntStateOf(2); private set

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        tera = p.getBoolean("tera", true)
        cols = p.getInt("cols", 2).coerceIn(1, 3)
    }

    fun setTera(ctx: Context, v: Boolean) {
        tera = v
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("tera", v).apply()
    }

    fun cycleCols(ctx: Context) {
        cols = if (cols >= 3) 1 else cols + 1
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("cols", cols).apply()
    }
}

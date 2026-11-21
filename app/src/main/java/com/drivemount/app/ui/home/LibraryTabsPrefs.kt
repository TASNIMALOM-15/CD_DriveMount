package com.drivemount.app.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Library-র ট্যাব-বার (Videos/Photos/… ও সাব-ট্যাব) উপরে থাকবে নাকি নিচে */
object LibraryTabsPrefs {
    private const val FILE = "library_tabs_prefs"
    private var loaded = false
    var top by mutableStateOf(true); private set

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        top = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("top", true)
    }

    fun setTop(ctx: Context, v: Boolean) {
        top = v
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("top", v).apply()
    }
}

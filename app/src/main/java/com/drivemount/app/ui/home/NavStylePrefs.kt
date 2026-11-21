package com.drivemount.app.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** নিচের নেভিগেশন বারের স্টাইল। নতুন স্টাইল বানালে এখানে একটা নাম যোগ করে MainShell-এ একটা শাখা যোগ করলেই হয়। */
enum class NavStyle { ORIGINAL, LIQUID_GLASS, PILL, SMART_LIQUID }

object NavStylePrefs {
    private const val FILE = "nav_style_prefs"
    private var loaded = false
    /** null = ব্যবহারকারী এখনো বেছে নেননি (তখন আগের "Liquid Glass" সুইচ অনুযায়ী) */
    private var chosen by mutableStateOf<NavStyle?>(null)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val v = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("style", null)
        chosen = NavStyle.entries.firstOrNull { it.name == v }
        // SmartLiquid was picked but never confirmed as running OK (the app died) -> fall back to Original
        val sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (chosen == NavStyle.SMART_LIQUID && sp.getBoolean("smart_pending", true)) {
            chosen = NavStyle.ORIGINAL
            sp.edit().putString("style", NavStyle.ORIGINAL.name).putBoolean("smart_pending", false).commit()
        }
    }

    /** Called once SmartLiquid has rendered fine for a moment: it is no longer "on trial". */
    fun confirmSmart(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("smart_pending", false).apply()
    }

    /** শুধু "Glass Pill" সরাসরি বাছা থাকলে true (লাইব্রেরির উপরের ট্যাবও তখন গ্লাস স্টাইলে) */
    val isPill: Boolean get() = chosen == NavStyle.PILL

    fun effective(globalGlass: Boolean): NavStyle =
        chosen ?: if (globalGlass) NavStyle.LIQUID_GLASS else NavStyle.ORIGINAL

    fun set(ctx: Context, s: NavStyle) {
        chosen = s
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("style", s.name).putBoolean("smart_pending", s == NavStyle.SMART_LIQUID).commit()
    }
}

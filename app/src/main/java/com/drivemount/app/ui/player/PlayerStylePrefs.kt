package com.drivemount.app.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PlayerStyle { NORMAL, YOUTUBE, GLASS }

/** Player UI স্টাইল: Compose state, তাই চলমান প্লেব্যাকেও সাথে সাথে বদলায় (ইঞ্জিন একই) */
object PlayerStylePrefs {
    private const val FILE = "player_style_prefs"
    private var loaded = false
    var style by mutableStateOf(PlayerStyle.NORMAL); private set

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val v = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("style", null)
        style = PlayerStyle.entries.firstOrNull { it.name == v } ?: PlayerStyle.NORMAL
    }

    fun set(ctx: Context, s: PlayerStyle) {
        style = s
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("style", s.name).apply()
    }
}

/** Dislike চিহ্ন (ফোনে সংরক্ষিত, key = "accountId|path"); Like = Favorite */
object PlayerDislikes {
    private var loaded = false
    var keys by mutableStateOf<Set<String>>(emptySet()); private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("player_dislikes", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        keys = prefs(ctx).getStringSet("keys", emptySet())?.toSet().orEmpty()
    }

    fun toggle(ctx: Context, key: String): Boolean {
        val on = key !in keys
        keys = if (on) keys + key else keys - key
        prefs(ctx).edit().putStringSet("keys", keys).apply()
        return on
    }

    fun remove(ctx: Context, key: String) {
        if (key !in keys) return
        keys = keys - key
        prefs(ctx).edit().putStringSet("keys", keys).apply()
    }
}

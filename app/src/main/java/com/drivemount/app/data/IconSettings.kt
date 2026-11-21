package com.drivemount.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** একটি আইকনের নিজস্ব সাজ: বদলানো আইকন (তালিকার কী বা "file:..." import) এবং/অথবা রং (ARGB) */
data class IconStyle(val image: String? = null, val color: Int? = null)

/**
 * অ্যাপের যেকোনো আইকন (AppIcons-এর কী ধরে) বদলানো ও রং দেওয়ার সেটিংস।
 * যেখানেই ওই আইকন দেখানো হয় সেখানেই এটা প্রযোজ্য হয়।
 */
class IconSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("icon_style_prefs", Context.MODE_PRIVATE)

    private val _styles = MutableStateFlow(load())
    val styles: StateFlow<Map<String, IconStyle>> = _styles

    private fun load(): Map<String, IconStyle> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { e ->
            val p = e.split("|")
            if (p.size < 3 || p[0].isEmpty()) null
            else p[0] to IconStyle(p[1].ifEmpty { null }, p[2].toIntOrNull())
        }.toMap()

    private fun save(map: Map<String, IconStyle>) {
        _styles.value = map
        prefs.edit().putStringSet(
            KEY,
            map.map { "${it.key}|${it.value.image.orEmpty()}|${it.value.color?.toString().orEmpty()}" }.toSet(),
        ).apply()
    }

    private fun update(key: String, f: (IconStyle) -> IconStyle) {
        val old = _styles.value[key] ?: IconStyle()
        val new = f(old)
        if (old.image != new.image) IconFiles.delete(appContext, old.image)
        save(if (new.image == null && new.color == null) _styles.value - key else _styles.value + (key to new))
    }

    fun setImage(key: String, image: String?) = update(key) { it.copy(image = image) }

    fun setColor(key: String, color: Int?) = update(key) { it.copy(color = color) }

    fun importImage(key: String, uri: Uri): Boolean {
        val name = IconFiles.import(appContext, key, uri) ?: return false
        setImage(key, name)
        return true
    }

    fun reset(key: String) = update(key) { IconStyle() }

    fun resetAll() {
        _styles.value.values.forEach { IconFiles.delete(appContext, it.image) }
        save(emptyMap())
    }

    private companion object { const val KEY = "styles" }
}

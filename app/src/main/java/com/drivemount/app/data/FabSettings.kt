package com.drivemount.app.data

import android.content.Context
import android.net.Uri
import com.drivemount.app.ui.components.FabIcons
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ডান-নিচের ফ্লোটিং বাটনের সেটিংস: পুরো বাটন চালু/বন্ধ, আর কোন পেজে কোন কাজ (আইকন) দেখাবে না তার তালিকা।
 * ডিফল্টে সবই দেখায়। "page:actionId" আকারে লুকানোগুলো জমা থাকে।
 */
class FabSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("fab_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled

    private val _hidden = MutableStateFlow(initialHidden())
    val hidden: StateFlow<Set<String>> = _hidden

    private val _icons = MutableStateFlow(
        prefs.getStringSet(KEY_ICONS, emptySet()).orEmpty()
            .mapNotNull { e -> e.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap(),
    )
    /** "page:actionId" -> আইকনের কী (FabIcons)। নেই মানে ডিফল্ট আইকন */
    val icons: StateFlow<Map<String, String>> = _icons

    private val _colors = MutableStateFlow(
        prefs.getStringSet(KEY_COLORS, emptySet()).orEmpty()
            .mapNotNull { e -> e.split("=", limit = 2).takeIf { it.size == 2 }?.let { p -> p[1].toIntOrNull()?.let { p[0] to it } } }
            .toMap(),
    )
    /** "page:actionId" -> আইকনের রং (ARGB)। নেই মানে ডিফল্ট রং */
    val colors: StateFlow<Map<String, Int>> = _colors

    private val _orders = MutableStateFlow(
        prefs.getStringSet(KEY_ORDERS, emptySet()).orEmpty()
            .mapNotNull { e -> e.split("=", limit = 2).takeIf { it.size == 2 }?.let { p -> p[0] to p[1].split(",").filter { it.isNotBlank() } } }
            .toMap(),
    )
    /** পেজ -> কাজগুলোর সাজানো ক্রম (আইডির তালিকা)। নেই মানে ডিফল্ট ক্রম */
    val orders: StateFlow<Map<String, List<String>>> = _orders

    /**
     * নতুন ডিফল্ট (layout v2): ভাসমান বাটনে শুধু পেজের মূল কাজ থাকে; Search/View/Sort, ফাইল-সিলেকশনের কাজ ও
     * Home/Clouds-এর Search/Sync/Transfers/Settings টপ-বারে যায় (টপ-বার আপনা-আপনি ওগুলো দেখায় যেগুলো এখানে লুকানো)।
     * আগের ব্যবহারকারীর নিজের লুকানো-তালিকা অক্ষত থেকে শুধু এগুলো যোগ হয়, একবারই। সেটিংসে আবার চালু করা যায়।
     */
    private fun initialHidden(): Set<String> {
        val saved = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
        if (prefs.getBoolean(KEY_LAYOUT_V2, false)) return saved
        val next = saved + DEFAULT_HIDDEN
        prefs.edit().putStringSet(KEY_HIDDEN, next).putBoolean(KEY_LAYOUT_V2, true).apply()
        return next
    }

    fun setOrder(page: String, ids: List<String>) {
        val next = _orders.value + (page to ids)
        _orders.value = next
        prefs.edit().putStringSet(KEY_ORDERS, next.map { "${it.key}=${it.value.joinToString(",")}" }.toSet()).apply()
    }

    fun setColor(page: String, action: String, color: Int?) {
        val k = "$page:$action"
        val next = if (color == null) _colors.value - k else _colors.value + (k to color)
        _colors.value = next
        prefs.edit().putStringSet(KEY_COLORS, next.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    fun setIcon(page: String, action: String, key: String?) {
        val k = "$page:$action"
        val old = _icons.value[k]
        val next = if (key == null) _icons.value - k else _icons.value + (k to key)
        if (old != null && old != key) deleteImported(old)
        _icons.value = next
        prefs.edit().putStringSet(KEY_ICONS, next.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    /** ডিভাইসের ছবি/SVG (uri) কপি করে এই কাজের আইকন বানায়। সফল হলে true */
    fun importIcon(page: String, action: String, uri: Uri): Boolean = try {
        val dir = File(appContext.filesDir, FabIcons.DIR).apply { mkdirs() }
        val name = "${page}_${action}_${System.currentTimeMillis()}"
        val out = File(dir, name)
        var total = 0L
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ICON_BYTES) throw IllegalStateException("too big")
                    o.write(buf, 0, n)
                }
            }
        } ?: throw IllegalStateException("no stream")
        setIcon(page, action, FabIcons.FILE_PREFIX + name)
        true
    } catch (e: Exception) {
        false
    }

    private fun deleteImported(key: String) {
        if (key.startsWith(FabIcons.FILE_PREFIX)) {
            runCatching { File(File(appContext.filesDir, FabIcons.DIR), key.removePrefix(FabIcons.FILE_PREFIX)).delete() }
        }
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setVisible(page: String, action: String, visible: Boolean) {
        val k = "$page:$action"
        val next = if (visible) _hidden.value - k else _hidden.value + k
        _hidden.value = next
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
    }

    /** সব কাজ আবার ডিফল্টে (ভাসমান বাটনে শুধু মূল কাজ, বাকিগুলো টপ-বারে) */
    fun resetActions() {
        _hidden.value = DEFAULT_HIDDEN
        prefs.edit().putStringSet(KEY_HIDDEN, DEFAULT_HIDDEN).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HIDDEN = "hidden"
        const val KEY_LAYOUT_V2 = "layout_v2"
        val DEFAULT_HIDDEN: Set<String> = setOf(
            "browser:search", "browser:view", "browser:sort",
            "browser_sel:download", "browser_sel:offline", "browser_sel:copy", "browser_sel:cut",
            "browser_sel:info", "browser_sel:rename", "browser_sel:delete", "browser_sel:clear",
            "home:search", "home:sync", "home:transfers", "home:settings",
            "clouds:search", "clouds:transfers",
        )
        const val KEY_ICONS = "icons"
        const val KEY_COLORS = "colors"
        const val KEY_ORDERS = "orders"
        const val MAX_ICON_BYTES = 2L * 1024 * 1024
    }
}

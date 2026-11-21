package com.drivemount.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.drivemount.app.ui.components.providerGlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ক্লাউড-কার্ডের পাশের আলোর রং: কোন ক্লাউড-ধরনের (drive, mega...) জন্য কোন রং।
 * সেট না থাকলে ডিফল্ট ব্র্যান্ড-রং; [OFF] মানে ওই ক্লাউডের কার্ডে কোনো আলো নেই।
 */
class CloudGlowSettings(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_glow_prefs", Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(
        prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap(),
    )
    val overrides: StateFlow<Map<String, Int>> = _overrides

    fun set(type: String, argb: Int) {
        _overrides.value = _overrides.value + (type to argb)
        prefs.edit().putInt(type, argb).apply()
    }

    fun off(type: String) = set(type, OFF)

    fun reset(type: String) {
        _overrides.value = _overrides.value - type
        prefs.edit().remove(type).apply()
    }

    companion object {
        /** আলো বন্ধ */
        const val OFF = 0

        /** দেখানোর রং: null = আলো নেই */
        fun resolve(overrides: Map<String, Int>, type: String): Color? {
            val v = overrides[type] ?: return providerGlow(type)
            return if (v == OFF) null else Color(v)
        }
    }
}

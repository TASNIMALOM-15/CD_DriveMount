package com.drivemount.app.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * অ্যাপ লকের অবস্থা: চালুর সময় এবং ব্যাকগ্রাউন্ডে নির্দিষ্ট সময় থাকার পর লক হয়।
 */
class AppLock(private val security: SecurityRepository) {

    private val _locked = MutableStateFlow(security.settings.value.appLock)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt = 0L

    fun onBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForegrounded() {
        if (!security.settings.value.appLock) return
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > LOCK_AFTER_MS) {
            _locked.value = true
        }
    }

    fun unlocked() {
        _locked.value = false
    }

    private companion object {
        const val LOCK_AFTER_MS = 60_000L
    }
}

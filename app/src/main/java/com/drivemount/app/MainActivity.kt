package com.drivemount.app

import android.graphics.Color as AColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.drivemount.app.security.Biometrics
import com.drivemount.app.ui.navigation.AppNavHost
import com.drivemount.app.ui.permissions.StartupPermissionPrompts
import com.drivemount.app.ui.security.LockScreen
import com.drivemount.app.ui.theme.DriveMountTheme

class MainActivity : FragmentActivity() {

    private val app: DriveMountApp
        get() = application as DriveMountApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenIntent(intent)

        setContent {
            val settings by app.themeRepository.settings.collectAsState()
            val security by app.securityRepository.settings.collectAsState()
            val locked by app.appLock.locked.collectAsState()
            val dark = settings.isDark(isSystemInDarkTheme())

            // অ্যাপের theme সিস্টেমের থেকে আলাদা হলে status/navigation bar-এর আইকনও মিলিয়ে নেওয়া
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        AColor.TRANSPARENT,
                        AColor.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        AColor.argb(0xE6, 0xFF, 0xFF, 0xFF),
                        AColor.argb(0x80, 0x1B, 0x1B, 0x1B),
                    ) { dark },
                )
                onDispose {}
            }

            // অ্যাপ লক চালু থাকলে screenshot ও recent-apps প্রিভিউ বন্ধ
            LaunchedEffect(security.appLock) {
                if (security.appLock) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // লক হয়ে গেলে নিজে থেকেই পরিচয় যাচাই চাওয়া হয়
            LaunchedEffect(locked) {
                if (locked) unlock()
            }

            DriveMountTheme(settings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (locked) {
                        LockScreen(onUnlock = { unlock() })
                    } else {
                        AppNavHost()
                        // অ্যাপ খুললেই Notification / Background / Storage অনুমতির পপ-আপ (যেটা বাকি)
                        StartupPermissionPrompts()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    /** অফলাইন নোটিফিকেশনে ট্যাপ: সেই ফাইলের ফোল্ডারে গিয়ে ফাইলটি হাইলাইট */
    private fun handleOpenIntent(intent: android.content.Intent?) {
        val i = intent ?: return
        val id = i.getLongExtra(EXTRA_ACCOUNT, -1L)
        if (id < 0) return
        app.openTarget.value = OpenTarget(
            id,
            i.getStringExtra(EXTRA_PATH).orEmpty(),
            i.getStringExtra(EXTRA_HIGHLIGHT).orEmpty(),
        )
        // একই intent আবার (যেমন স্ক্রিন ঘোরালে) চললে দ্বিতীয়বার না খোলার জন্য
        i.removeExtra(EXTRA_ACCOUNT)
    }

    companion object {
        const val EXTRA_ACCOUNT = "open_account"
        const val EXTRA_PATH = "open_path"
        const val EXTRA_HIGHLIGHT = "open_hl"
    }

    override fun onStart() {
        super.onStart()
        app.appLock.onForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) app.appLock.onBackgrounded()
    }

    private fun unlock() {
        if (!Biometrics.isAvailable(this)) {
            // ফোনের স্ক্রিন লক সরানো হয়ে থাকলে চিরতরে আটকে না থাকার জন্য
            app.appLock.unlocked()
            return
        }
        Biometrics.authenticate(
            activity = this,
            title = getString(R.string.lock_title),
            onSuccess = { app.appLock.unlocked() },
        )
    }
}

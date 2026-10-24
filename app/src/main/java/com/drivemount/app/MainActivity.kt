package com.drivemount.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.drivemount.app.ui.navigation.AppNavHost
import com.drivemount.app.ui.theme.DriveMountTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeRepository = (application as DriveMountApp).themeRepository

        setContent {
            val settings by themeRepository.settings.collectAsState()
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

            DriveMountTheme(settings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

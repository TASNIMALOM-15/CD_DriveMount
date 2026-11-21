package com.drivemount.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.drivemount.app.DriveMountApp

/** আইকন বেছে নেওয়ার তালিকা (অ্যাপের সব আইকন) আর import করা আইকনের ফাইলের নিয়ম */
object FabIcons {
    val all: List<Pair<String, ImageVector>> get() = AppIcons.all
    val byKey: Map<String, ImageVector> get() = AppIcons.byKey

    /** import করা আইকনের কী-এর শুরু ("file:<নাম>") আর সেগুলো যে ফোল্ডারে (filesDir/fab_icons) জমা থাকে */
    const val FILE_PREFIX = "file:"
    const val DIR = "fab_icons"
}

/**
 * (page, actionId) -> এই কাজটি ফ্লোটিং বাটনে এখন দেখা যাচ্ছে কিনা।
 * দেখা গেলে উপরের মেনুতে একই বাটন আর রাখা হয় না; বাটন বন্ধ/লুকানো থাকলে উপরের বাটন ফিরে আসে।
 */
@Composable
fun rememberFabVisibility(): (String, String) -> Boolean {
    val settings = (LocalContext.current.applicationContext as DriveMountApp).fabSettings
    val enabled by settings.enabled.collectAsState()
    val hidden by settings.hidden.collectAsState()
    return { page, id -> enabled && "$page:$id" !in hidden }
}

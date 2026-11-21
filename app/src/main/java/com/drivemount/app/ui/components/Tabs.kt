package com.drivemount.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** নিচের ৫ বাটনের পরিচয়: Home | Clouds | Library | Transfers | More
 *  নিচের ট্যাবগুলোর পরিচয় (MainShell-এর ভেতরের সংখ্যার সাথে মেলে) */
object TabIds {
    const val HOME = 0
    const val CLOUDS = 1
    const val LIBRARY = 2 // Videos/Photos/Music/Docs/APKs/Favorites একসাথে (ওপরে chip)
    const val TRANSFERS = 3 // ট্যাব নয়, Transfers পর্দা খোলে
    const val MORE = 4 // Addon, Sync, Backup, Settings ইত্যাদি
}

/** অ্যাপের যেকোনো পর্দা থেকে "এই ট্যাবে যাও" অনুরোধ; [n] প্রতিবার বাড়ে যাতে একই ট্যাবের অনুরোধও আলাদা ধরা যায় */
data class TabRequest(val tab: Int, val n: Int)

/** ফ্লোটিং বাটনের ট্যাব-বাটনগুলোর জন্য: [current] এখনকার ট্যাব (জানা না থাকলে null), [go] ট্যাবে যাওয়া */
class TabNav(val current: Int?, val go: (Int) -> Unit)

val LocalTabNav = staticCompositionLocalOf<TabNav?> { null }

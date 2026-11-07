package com.drivemount.app.ui.permissions

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.BatteryCharging as LuBatteryCharging
import com.composables.icons.lucide.Bell as LuBell
import com.composables.icons.lucide.FolderOpen as LuFolderOpen
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.drivemount.app.R
import com.drivemount.app.ui.browser.hasStorageAccess
import com.drivemount.app.ui.browser.requestStorageAccess

/** অ্যাপ খুললেই যে অনুমতিগুলোর পপ-আপ আসে (যেটা এখনও দেওয়া হয়নি, সেটার) */
private enum class StartupPerm { NOTIFICATION, BACKGROUND, STORAGE }

/** "Not now" বা "Allow" চাপা অনুমতিগুলো: অ্যাপ আবার নতুন করে খোলার আগে ওই অনুমতি আর চাওয়া হয় না */
private val handledThisLaunch: SnapshotStateList<StartupPerm> = mutableStateListOf()

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

private fun backgroundAllowed(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

private fun missing(context: Context): List<StartupPerm> = buildList {
    if (!notificationsAllowed(context)) add(StartupPerm.NOTIFICATION)
    if (!backgroundAllowed(context)) add(StartupPerm.BACKGROUND)
    if (!hasStorageAccess(context)) add(StartupPerm.STORAGE)
}

/**
 * Notification, Work in Background (ব্যাটারি অপ্টিমাইজেশন থেকে ছাড়) ও Manage Storage (ভিডিও/অডিওসহ সব ফাইল)
 * এই তিনটির মধ্যে যেটা বাকি, অ্যাপ খুললেই একটা একটা করে পপ-আপ দেখায়; "Allow" চাপলে সরাসরি সংশ্লিষ্ট অনুমতির পর্দা খোলে।
 */
@Composable
fun StartupPermissionPrompts() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // সিস্টেমের অনুমতি-পর্দা খোলা থাকার সময় পরের পপ-আপ দেখানো হয় না
    var busy by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        busy = false
        tick++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        busy = false
        tick++
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        busy = false
        tick++
    }

    val next = remember(tick, handledThisLaunch.size) {
        missing(context).firstOrNull { it !in handledThisLaunch }
    }
    if (next == null || busy) return

    val (icon, title, text) = when (next) {
        StartupPerm.NOTIFICATION -> Triple(
            Lucide.LuBell,
            R.string.perm_notification_title,
            R.string.perm_notification_text,
        )
        StartupPerm.BACKGROUND -> Triple(
            Lucide.LuBatteryCharging,
            R.string.perm_background_title,
            R.string.perm_background_text,
        )
        StartupPerm.STORAGE -> Triple(
            Lucide.LuFolderOpen,
            R.string.perm_storage_title,
            R.string.perm_storage_text,
        )
    }

    AlertDialog(
        onDismissRequest = { handledThisLaunch.add(next) },
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = {
                handledThisLaunch.add(next)
                busy = true
                val launched = when (next) {
                    StartupPerm.NOTIFICATION -> askNotification(context) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    StartupPerm.BACKGROUND -> askBackground(context)
                    StartupPerm.STORAGE -> {
                        requestStorageAccess(context, storageLauncher)
                        true
                    }
                }
                if (!launched) busy = false
            }) { Text(stringResource(R.string.perm_allow)) }
        },
        dismissButton = {
            TextButton(onClick = { handledThisLaunch.add(next) }) { Text(stringResource(R.string.perm_not_now)) }
        },
    )
}

/** Android 13+ এ প্রথমবার সিস্টেমের অনুমতি-বাক্স; আগে চেয়ে থাকলে (বা নিচের ভার্সনে) সরাসরি নোটিফিকেশন সেটিংস */
private fun askNotification(context: Context, launchSystemDialog: () -> Unit): Boolean {
    val prefs = context.getSharedPreferences("startup_permissions", Context.MODE_PRIVATE)
    val canAsk = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !prefs.getBoolean("notif_requested", false)
    if (canAsk) {
        prefs.edit().putBoolean("notif_requested", true).apply()
        return runCatching { launchSystemDialog() }.isSuccess
    }
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** "ব্যাটারি অপ্টিমাইজ করবেন না" এর সরাসরি সিস্টেম-বাক্স; না পারলে ব্যাটারি সেটিংসের তালিকা */
private fun askBackground(context: Context): Boolean {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    if (runCatching { context.startActivity(direct) }.isSuccess) return true
    return runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.isSuccess
}

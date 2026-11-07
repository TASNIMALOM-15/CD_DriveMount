package com.drivemount.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.drivemount.app.MainActivity
import com.drivemount.app.R
import java.io.File

object TransferNotifications {

    const val CHANNEL_ID = "transfers"
    const val ONGOING_ID = 1001
    private const val SUMMARY_ID = 1002
    private const val OPEN_ID = 1003

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tr_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** চলমান ট্রান্সফারের নোটিফিকেশন (total অজানা হলে অনির্দিষ্ট বার) */
    fun ongoing(context: Context, title: String, text: String, done: Long, total: Long): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
        if (total > 0) {
            builder.setProgress(100, ((done * 100) / total).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun notifySafely(context: Context, id: Int, notification: Notification) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) manager.notify(id, notification)
        } catch (_: SecurityException) {
            // নোটিফিকেশনের অনুমতি নেই; কাজ চলবে, শুধু বার্তা দেখা যাবে না
        }
    }

    fun update(context: Context, notification: Notification) = notifySafely(context, ONGOING_ID, notification)

    fun summary(context: Context, ok: Int, failed: Int) {
        val text = if (failed == 0) {
            context.getString(R.string.tr_notif_done_ok, ok)
        } else {
            context.getString(R.string.tr_notif_done_mixed, ok, failed)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(context.getString(R.string.tr_notif_done_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        notifySafely(context, SUMMARY_ID, n)
    }

    /** ডাউনলোড হওয়া ফাইল খোলার নোটিফিকেশন */
    fun openFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pending = PendingIntent.getActivity(
            context,
            file.name.hashCode(),
            view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(file.name)
            .setContentText(context.getString(R.string.tr_notif_tap_open))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        notifySafely(context, OPEN_ID, n)
    }
}

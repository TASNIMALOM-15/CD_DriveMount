package com.drivemount.app.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerNotificationManager
import com.drivemount.app.DriveMountApp
import com.drivemount.app.MainActivity
import com.drivemount.app.R

/**
 * গান চলার সময় ফোরগ্রাউন্ড সার্ভিস: অ্যাপ থেকে বেরোলে বা স্ক্রিন বন্ধ করলেও গান চলতে থাকে,
 * আর নোটিফিকেশনে আগের/পরের/প্লে-পজ বাটন থাকে। নোটিফিকেশন সরালে (পজ অবস্থায়) প্লেয়ার বন্ধ হয়ে যায়।
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : Service() {

    private var manager: PlayerNotificationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as DriveMountApp).playback
        ensureChannel()

        // startForeground ৫ সেকেন্ডের মধ্যে ডাকতেই হয়; আসল নোটিফিকেশন তৈরি হলে এটি বদলে যায়
        val placeholder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(controller.player.mediaMetadata.title ?: getString(R.string.app_name))
            .setOngoing(true)
            .setSilent(true)
            .build()
        startAsForeground(placeholder)

        if (manager == null) {
            manager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
                .setSmallIconResourceId(R.drawable.ic_stat_music)
                .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): CharSequence =
                        player.mediaMetadata.title ?: getString(R.string.app_name)

                    override fun createCurrentContentIntent(player: Player): PendingIntent? =
                        PendingIntent.getActivity(
                            this@PlaybackService,
                            0,
                            Intent(this@PlaybackService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )

                    override fun getCurrentContentText(player: Player): CharSequence? =
                        getString(R.string.playback_notification_text)

                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback,
                    ): Bitmap? = null
                })
                .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                        if (ongoing) {
                            startAsForeground(notification)
                        } else {
                            // পজ অবস্থায় নোটিফিকেশন থাকে, কিন্তু সরানো যায়
                            ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_DETACH)
                        }
                    }

                    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                        if (dismissedByUser) controller.close()
                        stopSelf()
                    }
                })
                .build()
                .apply {
                    setUseFastForwardAction(false)
                    setUseRewindAction(false)
                    setUseNextActionInCompactView(true)
                    setUsePreviousActionInCompactView(true)
                    setPlayer(controller.player)
                }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (_: Exception) {
            // অ্যাপ ব্যাকগ্রাউন্ডে থাকলে সিস্টেম ফোরগ্রাউন্ড সার্ভিস চালাতে নাও দিতে পারে; গান তখন সাধারণভাবেই চলে
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        running = false
        manager?.setPlayer(null)
        manager = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "dm_playback"
        private const val NOTIFICATION_ID = 7421

        @Volatile
        var running = false
            private set
    }
}

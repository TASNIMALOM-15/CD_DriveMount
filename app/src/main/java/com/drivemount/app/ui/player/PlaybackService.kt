package com.drivemount.app.ui.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.drivemount.app.DriveMountApp
import com.drivemount.app.MainActivity
import com.drivemount.app.R

/**
 * ব্যাকগ্রাউন্ড প্লেব্যাক সার্ভিস (background-playback-kit-এর MediaSessionService পদ্ধতি)।
 *
 * কিটে প্লেয়ার সার্ভিসের ভেতরে বানানো হয়; আমাদের প্লেয়ারে লোকাল স্ট্রিমিং-সার্ভারের লিংক, সাবটাইটেল, অবস্থান-মনে-রাখা
 * ইত্যাদি লাগে এবং সেটা [PlaybackController]-এ থাকে (একই প্রসেস)। তাই এখানে সেই একই প্লেয়ারকে MediaSession-এ জুড়ে দেওয়া হয়:
 *  - নোটিফিকেশন (আগের/প্লে-পজ/পরের), লক-স্ক্রিন কন্ট্রোল, হেডফোন/ব্লুটুথ বাটন Media3 নিজেই সামলায়
 *  - ফোরগ্রাউন্ড সার্ভিস (mediaPlayback) থাকায় অ্যাপ বন্ধ/ব্যাকগ্রাউন্ডে গেলেও গান ও ভিডিওর শব্দ চলে
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val controller = (application as DriveMountApp).playback

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_stat_music) },
        )

        // নোটিফিকেশনে ট্যাপ করলে অ্যাপ খুলবে
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, controller.player)
            .setSessionActivity(sessionActivity)
            .build()
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** অ্যাপ recents থেকে swipe করলে: গান/ভিডিও চললে চলতে থাকবে, না চললে সার্ভিস বন্ধ */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        // প্লেয়ার নিজে release করা হয় না: সেটা অ্যাপের PlaybackController-এর, সার্ভিসের নয়
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        /**
         * true হলে অন্য অ্যাপ অডিও/মাইক নিলেও প্লেয়ার থামবে না (বাধাহীন প্লেব্যাক)।
         * false হলে ফোন কল বা অন্য অ্যাপের সময় নিজে পজ হবে।
         */
        const val PLAY_THROUGH_INTERRUPTIONS = true

        @Volatile
        var running = false
            private set
    }
}

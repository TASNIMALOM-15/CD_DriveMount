package com.drivemount.app.ui.browser

import android.content.Context
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry

/** ভিডিওর দৈর্ঘ্য (থাম্বনেল বানানোর সময় জানা যায়) ও প্লেয়ারে সংরক্ষিত শেষ পজিশন */
object VideoMeta {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("video_meta", Context.MODE_PRIVATE)

    fun key(account: AccountEntity, entry: RemoteEntry) =
        "${account.remoteName}|${entry.path}|${entry.modTimeMillis}|${entry.size}"

    fun duration(c: Context, key: String): Long = prefs(c).getLong(key, 0L)

    fun putDuration(c: Context, key: String, ms: Long) {
        if (ms > 0) prefs(c).edit().putLong(key, ms).apply()
    }

    fun position(c: Context, accountId: Long, path: String): Long =
        c.applicationContext.getSharedPreferences("player_positions", Context.MODE_PRIVATE)
            .getLong("$accountId|$path", 0L)

    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

package com.drivemount.app.data

import android.content.Context
import com.drivemount.app.DriveMountApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** অ্যাকাউন্টের ইমেইল (কার্ডে দেখানোর জন্য): rclone কনফিগ থেকে, Google-এর জন্য Drive-এর "about" থেকে; একবার পেলে ফোনে জমা থাকে */
object AccountEmails {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("account_emails", Context.MODE_PRIVATE)

    fun cached(ctx: Context, id: Long): String? = prefs(ctx).getString("a$id", null)?.takeIf { it.isNotBlank() }

    suspend fun resolve(app: DriveMountApp, a: AccountEntity): String? {
        cached(app, a.id)?.let { return it }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val cfg = app.rcloneApi.dumpConfig()[a.remoteName] ?: return@runCatching null
                val direct = listOf("email", "user", "username", "login", "account")
                    .map { cfg.optString(it) }.firstOrNull { it.contains('@') }
                direct ?: if (cfg.optString("type") == "drive") googleEmail(cfg.optString("token")) else null
            }.getOrNull()
        }
        if (found != null) prefs(app).edit().putString("a${a.id}", found).apply()
        return found
    }

    private fun googleEmail(tokenJson: String): String? {
        val access = JSONObject(tokenJson).optString("access_token").ifEmpty { return null }
        val conn = java.net.URL("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)").openConnection() as java.net.HttpURLConnection
        return try {
            conn.setRequestProperty("Authorization", "Bearer $access")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().readText()).optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
        } finally { conn.disconnect() }
    }
}

package com.drivemount.app.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** আলাদা "Google Photos Unlimited" প্লাগইন APK-র সঙ্গে যোগাযোগ (Messenger, bound service) */
object GPhotosPlugin {
    const val PACKAGE = "com.drivemount.plugin.gphotos"
    private const val SERVICE = "$PACKAGE.PluginService"

    // প্লাগইনের Protocol.kt-এর সঙ্গে হুবহু মিলতে হবে
    const val MSG_HELLO = 1
    const val MSG_HELLO_REPLY = 2
    const val MSG_TEST_UPLOAD = 3
    const val MSG_PROGRESS = 4
    const val MSG_DONE = 5

    sealed interface Event {
        data class Hello(val versionName: String, val stage: String, val signedIn: Boolean, val account: String) : Event
        data class Progress(val percent: Int, val text: String) : Event
        data class Done(val ok: Boolean, val message: String) : Event
        data class Error(val message: String) : Event
    }

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    /** প্লাগইনে bind করে [what] বার্তা পাঠায় এবং উত্তরগুলো (Hello / Progress / Done) ফেরত দেয় */
    fun request(context: Context, what: Int): Flow<Event> = callbackFlow {
        val handler = Handler(Looper.getMainLooper()) { m: Message ->
            val d = m.data
            when (m.what) {
                MSG_HELLO_REPLY -> {
                    trySend(Event.Hello(d.getString("versionName").orEmpty(), d.getString("stage").orEmpty(), d.getBoolean("signedIn"), d.getString("account").orEmpty()))
                    close()
                }
                MSG_PROGRESS -> trySend(Event.Progress(d.getInt("percent"), d.getString("text").orEmpty()))
                MSG_DONE -> {
                    trySend(Event.Done(d.getBoolean("ok"), d.getString("message").orEmpty()))
                    close()
                }
            }
            true
        }
        val reply = Messenger(handler)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                try {
                    Messenger(service).send(Message.obtain(null, what).apply { replyTo = reply })
                } catch (e: Exception) {
                    trySend(Event.Error(e.message ?: "Could not talk to the plugin"))
                    close()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                trySend(Event.Error("Plugin disconnected"))
                close()
            }
        }
        val bound = try {
            context.bindService(Intent().setComponent(ComponentName(PACKAGE, SERVICE)), conn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            false
        }
        if (!bound) {
            trySend(Event.Error("Could not connect to the plugin"))
            close()
        }
        awaitClose { runCatching { context.unbindService(conn) } }
    }
}

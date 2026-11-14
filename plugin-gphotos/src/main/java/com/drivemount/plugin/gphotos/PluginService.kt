package com.drivemount.plugin.gphotos

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException

class PluginService : Service() {

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // শুধু একই চাবিতে সই করা অ্যাপ (DriveMount) গ্রহণযোগ্য
            val trusted = packageManager.checkSignatures(Process.myUid(), msg.sendingUid) ==
                PackageManager.SIGNATURE_MATCH
            if (!trusted) return
            val to = msg.replyTo ?: return
            when (msg.what) {
                Protocol.MSG_HELLO -> reply(
                    to, Protocol.MSG_HELLO_REPLY,
                    Bundle().apply {
                        putInt("protocol", Protocol.VERSION)
                        putString("versionName", BuildConfig.VERSION_NAME)
                        putString("stage", "account-test")
                        val acc = AccountStore.load(this@PluginService)
                        putBoolean("signedIn", acc != null)
                        putString("account", acc?.name.orEmpty())
                    },
                )
                Protocol.MSG_TEST_UPLOAD -> runFakeUpload(to)
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun runFakeUpload(to: Messenger) {
        val steps = listOf("Preparing file", "Connecting", "Uploading", "Finalizing", "Verifying")
        steps.forEachIndexed { i, text ->
            handler.postDelayed({
                reply(to, Protocol.MSG_PROGRESS, Bundle().apply {
                    putInt("percent", (i + 1) * 100 / steps.size)
                    putString("text", text)
                })
                if (i == steps.lastIndex) {
                    reply(to, Protocol.MSG_DONE, Bundle().apply {
                        putBoolean("ok", true)
                        putString("message", "Test upload finished (simulated, nothing was uploaded)")
                    })
                }
            }, 700L * (i + 1))
        }
    }

    private fun reply(to: Messenger, what: Int, data: Bundle) {
        try {
            to.send(Message.obtain(null, what).apply { this.data = data })
        } catch (_: RemoteException) {
        }
    }
}

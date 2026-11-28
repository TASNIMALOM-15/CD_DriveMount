package com.drivemount.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivemount.app.DriveMountApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** নোটিফিকেশনের Cancel বাটন থেকে কাজ বাতিল */
class TransferCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as DriveMountApp).transferRepository.cancel(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val EXTRA_ID = "task_id" }
}

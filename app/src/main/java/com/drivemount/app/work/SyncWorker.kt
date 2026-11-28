package com.drivemount.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drivemount.app.DriveMountApp

/** নির্ধারিত সময়ে একটি sync প্রোফাইল চালু করে (আসল কাজ ট্রান্সফার লাইনে চলে) */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_PROFILE_ID, -1L)
        if (id < 0) return Result.failure()
        val app = applicationContext as DriveMountApp
        app.syncRepository.runNow(id)
        return Result.success()
    }

    companion object {
        const val KEY_PROFILE_ID = "profileId"
    }
}

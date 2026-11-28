package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.meta.META_DIR
import com.drivemount.app.data.meta.ThumbMetaReader
import com.drivemount.app.engine.fsRootFor
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * অ্যাকাউন্ট যোগ করার পর (ব্যাকগ্রাউন্ডে, নিজে থেকে): পুরো অ্যাকাউন্টের ছবির
 *  - খুব ছোট থাম্বনেল (২–১০KB) ফোনে জমানো: ফোল্ডার-প্যাক থেকে, সব নেটে
 *  - মাঝারি প্রিভিউ (১০০–৩০০KB) ফোনে নামানো: শুধু Wi-Fi-তে এবং Photos সেটিং চালু থাকলে
 * আসল ছবি নামে না; ছবিতে ট্যাপ করলে তবেই নামে। ক্লাউডে আগে থেকে সাইডকার না থাকা ছবির জন্য কিছু করা হয় না
 * (পুরোনো ছবির প্রিভিউ বানানো Backfill-এর কাজ, সেটি আলাদা অনুমতিতে)।
 */
class AccountPrepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        val label = inputData.getString(KEY_LABEL).orEmpty()
        val title = applicationContext.getString(R.string.prep_notif_title, label)
        val text = if (total == 0) applicationContext.getString(R.string.prep_notif_scanning)
        else applicationContext.getString(R.string.prep_notif_progress, done, total)
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = TransferNotifications.ongoing(applicationContext, title, text, done.toLong(), total.toLong(), null, cancel)
        val nid = TransferNotifications.ONGOING_ID + 4
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(nid, n)
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground(0, 0)) }
        val accountId = inputData.getLong(KEY_ACCOUNT, -1L)
        val account = app.database.accountDao().getById(accountId) ?: return Result.success()
        if (account.backendType == "local") return Result.success()

        val fs = fsRootFor(account.remoteName, account.backendType)
        val all = runCatching { app.rcloneApi.listFilesRecursive(fs, "") }.getOrNull() ?: return Result.retry()
        val remote = account.remoteName

        // ১) ছোট থাম্বনেল: ১৫০টি করে ফোল্ডার; কিছু বাকি না থাকলে থামে
        for (round in 0 until MAX_ROUNDS) {
            coroutineContext.ensureActive()
            if (app.sidecar.importFromListing(remote, fs, all, ROUND_FOLDERS) < ROUND_FOLDERS) break
        }

        // ২) মাঝারি প্রিভিউ: শুধু যেগুলোর প্রিভিউ ক্লাউডে আছে এবং ফোনে নেই
        val canPreview = app.photoPreviews.offlineEnabled && !metered()
        if (canPreview) {
            val prevSet = all.asSequence().map { it.path }.filter { it.contains("/$META_DIR/prev/") }.toHashSet()
            val todo = all.filter { e ->
                !e.isDir && ThumbMetaReader.isImage(e.name) && !e.path.contains(META_DIR) &&
                    app.photoPreviews.needsPreview(e.size ?: -1L) && !app.photoPreviews.has(remote, e.path, e.size ?: -1L)
            }
            var done = 0
            var last = 0L
            for (e in todo) {
                coroutineContext.ensureActive()
                runCatching { app.photoPreviews.ensureCached(remote, fs, e.path, e.size ?: -1L, prevSet) }
                done++
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - last > 1000) { last = now; runCatching { setForeground(foreground(done, todo.size)) } }
            }
        }
        app.sidecar.version.intValue++ // ব্রাউজ/গ্রিড নতুন ক্যাশ দেখে আবার আঁকবে
        return Result.success()
    }

    private fun metered(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    companion object {
        private const val KEY_ACCOUNT = "account"
        private const val KEY_LABEL = "label"
        private const val ROUND_FOLDERS = 150
        private const val MAX_ROUNDS = 20

        fun enqueue(context: Context, accountId: Long, label: String) {
            val request = OneTimeWorkRequestBuilder<AccountPrepWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_ACCOUNT to accountId, KEY_LABEL to label))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("dm-acct-prep-$accountId", ExistingWorkPolicy.KEEP, request)
        }
    }
}

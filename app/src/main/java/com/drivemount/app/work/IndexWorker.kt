package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * পুরো ড্রাইভের (সব ফোল্ডারের) নামের ইনডেক্স বানানো, যাতে গ্লোবাল সার্চ সবকিছুতে কাজ করে।
 * শুধু ব্যবহারকারীর স্পষ্ট অনুমতিতে (সেটিংসের ডায়ালগ) শুরু হয়; ফাইল নামানো হয় না, শুধু ফোল্ডারের তালিকা আনা হয়।
 * সাধারণ অ্যাকাউন্টের তালিকা লোকাল ইনডেক্সে, ভল্টের তালিকা এনক্রিপ্টেড ক্যাশে যায় (FileRepository.list নিজেই করে)।
 * আবার চালালে কিছুক্ষণ আগে মেলানো ফোল্ডারে নেটওয়ার্ক কল না করে লোকাল তালিকা থেকে এগোয়।
 */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0)

    private fun foreground(done: Int): ForegroundInfo {
        val text = if (done == 0) "" else applicationContext.getString(R.string.index_notif_text, done)
        val n = TransferNotifications.ongoing(applicationContext, applicationContext.getString(R.string.index_notif_title), text, 0L, 0L)
        val id = TransferNotifications.ONGOING_ID + 3
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground(0)) }
        val target = inputData.getLong(KEY_ACCOUNT, ALL)
        val accounts = app.accountRepository.getAll()
            .filter { it.backendType != "local" && (target == ALL || it.id == target) }
        var folders = 0
        for (acc in accounts) {
            folders += crawl(acc, folders)
            if (folders >= MAX_FOLDERS) break
        }
        return Result.success(workDataOf(KEY_DONE to folders, KEY_PARTIAL to (folders >= MAX_FOLDERS)))
    }

    private suspend fun crawl(acc: AccountEntity, already: Int): Int {
        val repo = app.fileRepository
        val queue = ArrayDeque<String>().apply { add("") }
        var n = 0
        val fresh = System.currentTimeMillis() - 6L * 3600 * 1000
        while (queue.isNotEmpty() && already + n < MAX_FOLDERS) {
            coroutineContext.ensureActive()
            val dir = queue.removeFirst()
            // কিছুক্ষণ আগে মেলানো হয়ে থাকলে নেটওয়ার্ক ছাড়াই লোকাল তালিকা (তবু সাব-ফোল্ডারে নামা হয়)
            val recent = repo.syncedAt(acc, dir)?.let { it > fresh } == true
            val list = (if (recent) repo.persisted(acc, dir) else null)
                ?: repo.list(acc, dir).getOrNull()
                ?: continue
            n++
            list.filter { it.isDir }.forEach { queue.add(it.path) }
            if (n % 10 == 0) {
                setProgress(workDataOf(KEY_DONE to already + n))
                runCatching { setForeground(foreground(already + n)) }
            }
            if (!recent) delay(100) // প্রোভাইডারে চাপ ও ব্যাটারি কম রাখতে
        }
        return n
    }

    companion object {
        const val UNIQUE_NAME = "dm-index"
        const val KEY_ACCOUNT = "account"
        const val KEY_DONE = "done"
        const val KEY_PARTIAL = "partial"
        const val ALL = -1L
        private const val MAX_FOLDERS = 20_000
    }
}

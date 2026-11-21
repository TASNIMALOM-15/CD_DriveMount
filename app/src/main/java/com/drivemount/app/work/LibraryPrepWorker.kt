package com.drivemount.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Constraints
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
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.ui.browser.prefetchThumbnail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Photos/Videos/Music ট্যাবে নতুন ফোল্ডার যোগ করার পর ব্যাকগ্রাউন্ড কাজ ও নোটিফিকেশন।
 *
 * ধাপ ১: ফোল্ডারের তালিকা আনা (ট্যাবের ViewModel আনে; এই কাজ তার শেষ পর্যন্ত "Scanning…" দেখায়)।
 * ধাপ ২: থাম্বনেল ও (ছবির) প্রিভিউ আগেভাগে বানিয়ে ফোনে জমানো; "N / মোট" প্রগ্রেস, ট্যাপে অ্যাপ খোলে, Cancel বাটন আছে।
 * অ্যাপ আড়ালে গেলেও চলে। শুধু নতুন যোগ করা ফোল্ডারে চলে; সাধারণ রিফ্রেশে চলে না (নোটিফিকেশনের উপদ্রব এড়াতে)।
 */
class LibraryPrepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as DriveMountApp
    private val label get() = inputData.getString(KEY_LABEL).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        val title = applicationContext.getString(R.string.prep_notif_title, label)
        val text = if (total == 0) applicationContext.getString(R.string.prep_notif_scanning)
        else applicationContext.getString(R.string.prep_notif_progress, done, total)
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = TransferNotifications.ongoing(applicationContext, title, text, done.toLong(), total.toLong(), null, cancel)
        val nid = TransferNotifications.ONGOING_ID + 3
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(nid, n)
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(foreground(0, 0)) }
        val key = inputData.getString(KEY_SOURCE) ?: return Result.failure()
        // তালিকা আসার অপেক্ষা (ViewModel জানায়); প্রক্রিয়া নতুন হলে সঙ্গে সঙ্গে ফাইল থেকে পড়ে
        val signal = PENDING[key]
        if (signal != null) withTimeoutOrNull(WAIT_LIST_MS) { signal.await() }
        PENDING.remove(key)

        val items = readItems(key)
        val account = items.firstOrNull()?.first?.let { app.database.accountDao().getById(it) }
        if (items.isEmpty() || account == null || account.backendType == "local") return Result.success()

        val previews = inputData.getBoolean(KEY_PREVIEWS, false)
        val canPreview = previews && app.photoPreviews.offlineEnabled && !metered()
        val fs = fsRootFor(account.remoteName, account.backendType)
        val remoteSet = app.photoPreviews.remoteSets[key].orEmpty()
        val total = items.size
        val done = AtomicInteger(0)
        val gate = Semaphore(PARALLEL)
        var lastNote = 0L
        coroutineScope {
            items.chunked(CHUNK).map { chunk ->
                async {
                    for ((_, entry) in chunk) {
                        coroutineContext.ensureActive()
                        gate.withPermit {
                            runCatching { prefetchThumbnail(app, account, entry) }
                            if (canPreview) {
                                runCatching { app.photoPreviews.ensureCached(account.remoteName, fs, entry.path, entry.size ?: -1L, remoteSet) }
                            }
                        }
                        val d = done.incrementAndGet()
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastNote > 800) {
                            lastNote = now
                            runCatching { setForeground(foreground(d, total)) }
                        }
                    }
                }
            }.awaitAll()
        }
        TransferNotifications.finished(
            applicationContext,
            DONE_ID,
            applicationContext.getString(R.string.prep_notif_done_title, label),
            applicationContext.getString(R.string.prep_notif_done_text, total),
        )
        return Result.success(workDataOf("done" to total))
    }

    private fun metered(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    /** ViewModel-এর লেখা তালিকা-ফাইল থেকে এই উৎসের আইটেম: (accountId, entry) */
    private fun readItems(key: String): List<Pair<Long, RemoteEntry>> = runCatching {
        val cache = File(applicationContext.filesDir, inputData.getString(KEY_CACHE).orEmpty())
        if (!cache.exists()) return emptyList()
        val srcs = JSONObject(cache.readText()).getJSONArray("sources")
        val out = ArrayList<Pair<Long, RemoteEntry>>()
        for (k in 0 until srcs.length()) {
            val so = srcs.getJSONObject(k)
            if (so.getString("key") != key) continue
            val accountId = so.getLong("accountId")
            val arr = so.getJSONArray("items")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += accountId to RemoteEntry(
                    name = o.getString("name"),
                    path = o.getString("path"),
                    isDir = false,
                    size = o.optLong("size", -1L).takeIf { it >= 0 },
                    modTimeMillis = if (o.has("mod")) o.getLong("mod") else null,
                    mimeType = o.optString("mime"),
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    companion object {
        const val KEY_SOURCE = "source"
        const val KEY_CACHE = "cache"
        const val KEY_LABEL = "label"
        const val KEY_PREVIEWS = "previews"
        private const val PARALLEL = 3
        private const val CHUNK = 12
        private const val WAIT_LIST_MS = 10 * 60 * 1000L
        private const val DONE_ID = TransferNotifications.ONGOING_ID + 30

        /** তালিকা আসার অপেক্ষায় থাকা কাজ: উৎসের key -> সংকেত */
        private val PENDING = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        /** নতুন ফোল্ডার যোগের সঙ্গে সঙ্গে ডাকুন: নোটিফিকেশন তখনই আসে ("Scanning…") */
        fun begin(context: Context, key: String, label: String, cacheName: String, previews: Boolean) {
            PENDING[key] = CompletableDeferred()
            val request = OneTimeWorkRequestBuilder<LibraryPrepWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_SOURCE to key, KEY_CACHE to cacheName, KEY_LABEL to label, KEY_PREVIEWS to previews))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("dm-prep-$key", ExistingWorkPolicy.REPLACE, request)
        }

        /** তালিকা ফাইলে লেখা হয়ে গেছে (বা ব্যর্থ): কাজ এগোতে পারে */
        fun listReady(key: String, ok: Boolean) {
            PENDING[key]?.complete(ok)
        }
    }
}

package com.drivemount.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.drivemount.app.engine.startPathFor
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor

/** ফাইল ব্রাউজারের কাজগুলো: তালিকা, নতুন ফোল্ডার, মোছা, নাম বদল, কপি/সরানো */
class FileRepository(private val api: RcloneApi) {

    private fun fsOf(account: AccountEntity) = fsRootFor(account.remoteName, account.backendType)

    /** সদ্য দেখা ফোল্ডারের তালিকা (মেমরিতে, সর্বোচ্চ ৬০টি); আবার খুললে সঙ্গে সঙ্গে দেখানোর জন্য */
    private val cache = object : LinkedHashMap<String, List<RemoteEntry>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RemoteEntry>>?): Boolean = size > 60
    }

    private fun cacheKey(account: AccountEntity, path: String) = "${account.id}|$path"

    @Synchronized
    fun cached(account: AccountEntity, path: String): List<RemoteEntry>? = cache[cacheKey(account, path)]

    @Synchronized
    private fun remember(account: AccountEntity, path: String, list: List<RemoteEntry>) {
        cache[cacheKey(account, path)] = list
    }

    @Synchronized
    private fun forget(account: AccountEntity) {
        val prefix = "${account.id}|"
        cache.keys.removeAll { it.startsWith(prefix) }
    }

    /**
     * অ্যাপ চালুর পর পেছনে প্রতিটি ক্লাউডের প্রথম ফোল্ডার আনে। টোকেন নবায়ন, সংযোগ ও rclone-এর
     * ক্যাশ আগেই তৈরি হয়ে যায়, তাই ক্লাউডে ক্লিক করলে সঙ্গে সঙ্গে খোলে।
     */
    suspend fun warmUp(accounts: List<AccountEntity>) = coroutineScope {
        val gate = Semaphore(2)
        accounts.filter { it.backendType != "local" }.map { acc ->
            async { gate.withPermit { runCatching { list(acc, startPathFor(acc.backendType)) } } }
        }.awaitAll()
    }

    suspend fun list(account: AccountEntity, path: String): Result<List<RemoteEntry>> =
        runCatching { api.list(fsOf(account), path).also { remember(account, path, it) } }

    suspend fun mkdir(account: AccountEntity, path: String): Result<Unit> =
        runCatching { api.mkdir(fsOf(account), path) }.also { forget(account) }

    suspend fun delete(account: AccountEntity, entry: RemoteEntry): Result<Unit> = runCatching {
        if (entry.isDir) api.purge(fsOf(account), entry.path) else api.deleteFile(fsOf(account), entry.path)
    }.also { forget(account) }

    /** একই অ্যাকাউন্টের ভেতরে নতুন পথে সরানো */
    suspend fun rename(account: AccountEntity, entry: RemoteEntry, newPath: String): Result<Unit> =
        transfer(account, entry, account, newPath, move = true)

    /** src অ্যাকাউন্টের entry-কে dst অ্যাকাউন্টের dstPath-এ কপি বা সরানো */
    suspend fun transfer(
        src: AccountEntity,
        entry: RemoteEntry,
        dst: AccountEntity,
        dstPath: String,
        move: Boolean,
    ): Result<Unit> = runCatching {
        if (entry.isDir) {
            api.transferDir(fsOf(src) + entry.path, fsOf(dst) + dstPath, move)
        } else {
            api.transferFile(fsOf(src), entry.path, fsOf(dst), dstPath, move)
        }
    }.also {
        forget(src)
        forget(dst)
    }
}

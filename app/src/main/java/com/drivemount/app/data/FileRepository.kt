package com.drivemount.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import com.drivemount.app.data.index.IndexDao
import com.drivemount.app.data.index.IndexEntry
import com.drivemount.app.data.meta.META_DIR
import com.drivemount.app.data.meta.SidecarManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.drivemount.app.security.VaultCache
import com.drivemount.app.engine.startPathFor
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor

/** ফাইল ব্রাউজারের কাজগুলো: তালিকা, নতুন ফোল্ডার, মোছা, নাম বদল, কপি/সরানো */
class FileRepository(
    private val api: RcloneApi,
    private val store: VaultCache,
    private val index: IndexDao,
    private val sidecar: SidecarManager,
) {

    /**
     * ডিস্কে লেখার একটাই কম-অগ্রাধিকারের থ্রেড: তালিকা সংরক্ষণ UI বা নেটওয়ার্ক কাজের পথ আটকায় না,
     * আর একসাথে একাধিক বড় লেখা CPU/ডিস্কে চাপ ফেলে না।
     */
    private val writer = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { r ->
            Thread(r, "index-writer").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
        }.asCoroutineDispatcher(),
    )


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
        runCatching {
            val raw = api.list(fsOf(account), path)
            // সাইডকার ফোল্ডার (.dmmeta) ব্যবহারকারীকে দেখানো হয় না; তবে তাতে APK-র আইকন থাকলে পেছনে আনা হয়
            val hasMeta = raw.any { it.isDir && it.name == META_DIR }
            val fresh = if (hasMeta) raw.filter { !(it.isDir && it.name == META_DIR) } else raw
            if (hasMeta && account.backendType != "local") {
                sidecar.onFolderListed(account.remoteName, fsOf(account), path, fresh)
            }
            // আগের তালিকার সঙ্গে হুবহু এক হলে ডিস্কে আবার লেখার দরকার নেই
            val same = cached(account, path) == fresh
            remember(account, path, fresh)
            if (!same) persist(account, path, fresh)
            fresh
        }

    /**
     * এনক্রিপ্টেড ভল্টের শেষ দেখা তালিকা ডিস্ক থেকে (অ্যাপ বন্ধ করে খুললেও থাকে)।
     * তালিকাটি পুরোনো হতে পারে; দেখানোর পর পেছনে ক্লাউড থেকে নতুন তালিকা এনে বদলে দিতে হবে।
     * ডিস্কে এটি এনক্রিপ্টেড থাকে (VaultCache)। শুধু ভল্টের জন্য; সাধারণ অ্যাকাউন্টের তালিকা ডিস্কে রাখা হয় না।
     */
    suspend fun persisted(account: AccountEntity, path: String): List<RemoteEntry>? {
        val type = account.backendType
        if (type == "local") return null // ফোনের নিজের ফাইল-সিস্টেম এমনিতেই দ্রুত
        val found = withContext(Dispatchers.IO) {
            runCatching {
                if (type == "crypt") {
                    val bytes = store.get(LIST_NS, cacheKey(account, path)) ?: return@runCatching null
                    decode(String(bytes, Charsets.UTF_8))
                } else {
                    if (index.folderSyncedAt(account.id, path) == null) {
                        null
                    } else {
                        index.children(account.id, path).map {
                            RemoteEntry(
                                name = it.name,
                                path = it.path,
                                isDir = it.isDir,
                                size = it.size.takeIf { s -> s >= 0 },
                                modTimeMillis = it.modTime.takeIf { m -> m >= 0 },
                                mimeType = it.mime,
                            )
                        }
                    }
                }
            }.getOrNull()
        }
        // মেমরিতেও রাখা: নতুন তালিকা হুবহু এক হলে ডিস্কে আবার লেখা লাগে না
        if (found != null && cached(account, path) == null) remember(account, path, found)
        return found
    }

    /** পেছনের থ্রেডে সংরক্ষণ; ডাকা জায়গা অপেক্ষা করে না */
    private fun persist(account: AccountEntity, path: String, list: List<RemoteEntry>) {
        val type = account.backendType
        if (type == "local") return
        val accountId = account.id
        val key = cacheKey(account, path)
        writer.launch {
            runCatching {
                if (type == "crypt") {
                    store.put(LIST_NS, key, encode(list).toByteArray(Charsets.UTF_8))
                    registerVaultFolder(accountId, path)
                } else {
                    val rows = list.map {
                        IndexEntry(
                            accountId = accountId,
                            path = it.path,
                            parent = path,
                            name = it.name,
                            isDir = it.isDir,
                            size = it.size ?: -1L,
                            modTime = it.modTimeMillis ?: -1L,
                            mime = it.mimeType,
                        )
                    }
                    index.replaceFolder(accountId, path, rows, System.currentTimeMillis())
                }
            }
        }
    }

    /**
     * সব সাধারণ ক্লাউডের ইনডেক্সে নাম খোঁজা (নেটওয়ার্ক লাগে না)। একাধিক শব্দ দিলে সবগুলো নামে থাকতে হবে।
     * এনক্রিপ্টেড ভল্ট এতে নেই: ভল্টের ফাইলের নাম সাদা অবস্থায় ডাটাবেসে রাখা হয় না।
     */
    suspend fun search(query: String, limit: Int = 100): List<IndexEntry> = withContext(Dispatchers.IO) {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(4)
        if (terms.isEmpty()) return@withContext emptyList()
        val longest = terms.maxByOrNull { it.length } ?: return@withContext emptyList()
        val escaped = longest.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        runCatching {
            index.search(escaped, 600)
                .filter { e -> terms.all { e.name.contains(it, ignoreCase = true) } }
                .take(limit)
        }.getOrDefault(emptyList())
    }

    // ---------------- ভল্টের এনক্রিপ্টেড সার্চ ইনডেক্স ----------------

    /** ভল্টের কোন কোন ফোল্ডারের তালিকা এনক্রিপ্টেড ক্যাশে আছে (ফোল্ডারের পথ; নিজেও এনক্রিপ্টেড ব্লবে) */
    private val vaultFolders = java.util.concurrent.ConcurrentHashMap<Long, MutableSet<String>>()

    /** খোঁজার জন্য ভল্টের সব তালিকা মেমরিতে (শুধু খোঁজার পর্দা খুললে ভরে; নামগুলো ডিস্কে সাদা অবস্থায় যায় না) */
    private val vaultIndex = java.util.concurrent.ConcurrentHashMap<Long, List<RemoteEntry>>()

    private fun foldersOf(accountId: Long): MutableSet<String> = vaultFolders.getOrPut(accountId) {
        val bytes = store.get(VFOLDERS_NS, accountId.toString())
        val set = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
        if (bytes != null) runCatching {
            val arr = JSONArray(String(bytes, Charsets.UTF_8))
            for (i in 0 until arr.length()) set.add(arr.getString(i))
        }
        set
    }

    private fun registerVaultFolder(accountId: Long, path: String) {
        val set = foldersOf(accountId)
        vaultIndex.remove(accountId) // তালিকা বদলেছে: পরের খোঁজায় আবার তৈরি
        if (set.add(path)) {
            val arr = JSONArray()
            synchronized(set) { set.forEach { arr.put(it) } }
            store.put(VFOLDERS_NS, accountId.toString(), arr.toString().toByteArray(Charsets.UTF_8))
        }
    }

    /** ভল্টের যত ফোল্ডার অ্যাপে খোলা/ইনডেক্স করা হয়েছে, তাতে নাম খোঁজা (নেটওয়ার্ক লাগে না)। ফল: (অ্যাকাউন্ট, এন্ট্রি, ফোল্ডার) */
    suspend fun searchVaults(query: String, vaultIds: List<Long>, limit: Int = 100): List<Triple<Long, RemoteEntry, String>> =
        withContext(Dispatchers.IO) {
            val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(4)
            if (terms.isEmpty()) return@withContext emptyList()
            val out = ArrayList<Triple<Long, RemoteEntry, String>>()
            for (id in vaultIds) {
                val all = vaultIndex.getOrPut(id) {
                    val acc = ArrayList<RemoteEntry>()
                    for (folder in synchronized(foldersOf(id)) { foldersOf(id).toList() }) {
                        val bytes = store.get(LIST_NS, "$id|$folder") ?: continue
                        runCatching { acc.addAll(decode(String(bytes, Charsets.UTF_8))) }
                    }
                    acc
                }
                for (e in all) {
                    if (terms.all { e.name.contains(it, ignoreCase = true) }) {
                        out.add(Triple(id, e, e.path.substringBeforeLast('/', "")))
                        if (out.size >= limit) return@withContext out
                    }
                }
            }
            out
        }

    /** ভল্ট ইনডেক্সে কতগুলো ফোল্ডার আছে (সেটিংসে দেখানোর জন্য) */
    fun vaultFolderCount(accountId: Long): Int = foldersOf(accountId).size

    /** ফোল্ডারটি শেষ কবে নেটওয়ার্ক থেকে মেলানো হয়েছে (সাধারণ অ্যাকাউন্টে; ভল্টে/অজানা হলে null) */
    suspend fun syncedAt(account: AccountEntity, path: String): Long? = withContext(Dispatchers.IO) {
        if (account.backendType == "crypt" || account.backendType == "local") null
        else runCatching { index.folderSyncedAt(account.id, path) }.getOrNull()
    }

    /** মুছে ফেলা অ্যাকাউন্টের আর ৬০ দিনের পুরোনো তালিকা সাফ করে (অ্যাপ চালুর কিছু পরে, পেছনে) */
    suspend fun pruneIndex(keepAccountIds: List<Long>) = withContext(Dispatchers.IO) {
        runCatching {
            if (keepAccountIds.isEmpty()) index.deleteAllFolders() else index.deleteFoldersOfOtherAccounts(keepAccountIds)
            index.deleteOldFolders(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
            index.deleteOrphanEntries()
        }
    }

    private fun encode(list: List<RemoteEntry>): String {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("n", e.name)
                    .put("p", e.path)
                    .put("d", e.isDir)
                    .put("s", e.size ?: -1L)
                    .put("m", e.modTimeMillis ?: -1L)
                    .put("t", e.mimeType),
            )
        }
        return arr.toString()
    }

    private fun decode(text: String): List<RemoteEntry> {
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RemoteEntry(
                name = o.getString("n"),
                path = o.getString("p"),
                isDir = o.getBoolean("d"),
                size = o.optLong("s", -1L).takeIf { it >= 0 },
                modTimeMillis = o.optLong("m", -1L).takeIf { it >= 0 },
                mimeType = o.optString("t"),
            )
        }
    }

    private companion object {
        const val LIST_NS = "list"
        const val VFOLDERS_NS = "vfolders"
    }

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
        // ফাইল হলে থাম্বনেল/আইকন/কভার/প্রিভিউ নতুন পথে বসানো, যাতে নাম বদলালে বা সরালে হারিয়ে না যায়
        if (!entry.isDir) {
            runCatching { sidecar.migrate(src.remoteName, dst.remoteName, fsOf(dst), entry.path, dstPath, entry.size ?: -1L) }
        }
    }.also {
        forget(src)
        forget(dst)
    }
}

package com.drivemount.app.data.galaxy

import android.content.Context
import com.drivemount.app.data.AccountDao
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.AccountRepository
import com.drivemount.app.data.transfer.NewTransfer
import com.drivemount.app.data.transfer.TransferRepository
import com.drivemount.app.engine.RcloneApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Galaxy Drive-এর নির্ধারিত নাম */
object Galaxy {
    /** মূল (ওপরের) union remote: প্রতিটি প্ল্যাটফর্মের পুলকে একটি করে কপি হিসেবে ধরে সবকটিতে লেখে */
    const val REMOTE = "dmgalaxy"

    /** প্ল্যাটফর্ম-পুলের ভেতরের union (অ্যাকাউন্ট তালিকায় দেখানো হয় না) */
    private const val INNER = "dmgx"

    /** ট্র্যাশ ফোল্ডারের (লুকানো) নাম ও কত দিন রাখা হবে */
    const val TRASH = ".galaxy-trash"
    const val TRASH_DAYS = 30

    /** ফাইলের কপি-অবস্থা দেখার জন্য: ভল্টের ডিক্রিপ্ট "দৃশ্য" (প্রতি প্ল্যাটফর্মে) */
    private const val VIEW = "dmgv"

    fun isInner(remote: String) = remote.startsWith(INNER) || remote.startsWith(VIEW)
    fun viewName(vaultRemote: String, type: String) =
        VIEW + vaultRemote.removePrefix("dm") + type.lowercase().replace(Regex("[^a-z0-9]"), "")
    fun viewPrefix(vaultRemote: String) = VIEW + vaultRemote.removePrefix("dm")
    fun innerName(type: String) = INNER + type.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Galaxy-তে ঢোকানো যায় না এমন ধরন */
    fun eligible(type: String) = type !in setOf("local", "crypt", "union")
}

/** on = চালু প্ল্যাটফর্ম (ধরন), members = প্রতি ধরনে বাছাই করা অ্যাকাউন্টের id */
data class GalaxyConfig(val on: Set<String> = emptySet(), val members: Map<String, List<Long>> = emptyMap()) {
    fun membersOf(type: String): List<Long> = members[type].orEmpty()
}

/** ট্র্যাশে থাকা একটি জিনিস: `.galaxy-trash/<dir>/<name>`; origPath = আগের জায়গা */
data class TrashItem(
    val accountId: Long,
    val dir: String,
    val name: String,
    val origPath: String,
    val isDir: Boolean,
    val size: Long?,
    val at: Long,
) {
    val trashPath: String get() = "$dir/$name"
}

data class GalaxyCapacity(
    /** ব্যবহারযোগ্য মোট (সবচেয়ে ছোট পুলের মাপ) */
    val total: Long,
    /** আরও যতটা ঢোকানো যাবে (সবচেয়ে কম খালি জায়গার পুল) */
    val free: Long,
    /** কয়টি প্ল্যাটফর্ম (= কয়টি কপি) */
    val platforms: Int,
    /** জায়গা সীমিত করছে যে ধরন */
    val limitingType: String?,
    /** কিছু অ্যাকাউন্টের মাপ অজানা */
    val partial: Boolean,
)

class GalaxyRepository(
    context: Context,
    private val dao: AccountDao,
    private val api: RcloneApi,
    private val accounts: AccountRepository,
    private val transfers: TransferRepository,
    private val files: com.drivemount.app.data.FileRepository,
) {
    private val prefs = context.getSharedPreferences("galaxy", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<GalaxyConfig> = _config

    // ---------------- পুরোনো ডেটা Galaxy-তে আনা ----------------

    /**
     * একটি ফোল্ডার (বা পুরো ক্লাউড/ফোন-ফোল্ডার) Galaxy বা Galaxy-ভল্টে কপি করে: `Imported/<ক্লাউডের নাম>/<ফোল্ডার>`।
     * শুধু কপি (move নয়), তাই উৎসের কিছু মোছে না। ট্রান্সফার-লাইনে চলে (Transfers-এ দেখা/বাতিল যায়);
     * আবার চালালে একই ফাইল বাদ দিয়ে বাকিগুলো নামে।
     */
    suspend fun importFolder(src: AccountEntity, srcPath: String, dest: AccountEntity): Result<Unit> = runCatching {
        if (isGalaxyBacked(src)) throw IllegalStateException("This folder is already inside Galaxy Drive")
        val folder = srcPath.substringAfterLast('/').ifEmpty { src.displayName }.replace('/', '_')
        val destPath = "Imported/${src.displayName.replace('/', '_')}/$folder"
        files.mkdir(dest, destPath).getOrThrow()
        transfers.enqueue(
            listOf(
                NewTransfer(
                    name = "Import: $folder",
                    isDir = true,
                    move = false,
                    srcFs = com.drivemount.app.engine.fsRootFor(src.remoteName, src.backendType),
                    srcPath = srcPath,
                    dstFs = com.drivemount.app.engine.fsRootFor(dest.remoteName, dest.backendType),
                    dstPath = destPath,
                ),
            ),
        )
    }

    // ---------------- ট্র্যাশ (৩০ দিন) ----------------
    // মুছলে ফাইল সব কপিতে `.galaxy-trash`-এ সরে যায়; ৩০ দিন পর বা "Delete forever"-এ আসলেই মোছে।
    // কোথা থেকে মোছা হয়েছিল তার তালিকা ফোনে (SharedPreferences) থাকে।

    private val trashPrefs = context.getSharedPreferences("galaxy_trash", Context.MODE_PRIVATE)
    private val _trash = MutableStateFlow(loadTrash())
    val trash: StateFlow<List<TrashItem>> = _trash

    private fun loadTrash(): List<TrashItem> = runCatching {
        val a = JSONArray(trashPrefs.getString("items", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            TrashItem(
                o.getLong("a"), o.getString("d"), o.getString("n"), o.getString("o"),
                o.getBoolean("f"), o.optLong("s", -1L).takeIf { it >= 0 }, o.getLong("t"),
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun setTrash(list: List<TrashItem>) {
        _trash.value = list
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("a", it.accountId).put("d", it.dir).put("n", it.name).put("o", it.origPath)
                .put("f", it.isDir).put("s", it.size ?: -1L).put("t", it.at))
        }
        trashPrefs.edit().putString("items", a.toString()).apply()
    }

    /** Galaxy নিজে, অথবা Galaxy-র ওপরে বানানো ভল্ট: এগুলোতে মোছা মানে ট্র্যাশে পাঠানো */
    suspend fun isGalaxyBacked(account: AccountEntity): Boolean {
        if (account.remoteName == Galaxy.REMOTE) return true
        if (account.backendType != "crypt") return false
        return api.dumpConfig()[account.remoteName]?.optString("remote")?.startsWith(Galaxy.REMOTE + ":") == true
    }

    /** জিনিসটি ট্র্যাশে সরায় (সব কপিতে একসঙ্গে); dirName প্রতি জিনিসে অনন্য */
    suspend fun moveToTrash(account: AccountEntity, entry: com.drivemount.app.engine.RemoteEntry, dirName: String): Result<Unit> = runCatching {
        val dir = "${Galaxy.TRASH}/$dirName"
        files.mkdir(account, dir).getOrThrow()
        files.rename(account, entry, "$dir/${entry.name}").getOrThrow()
        setTrash(_trash.value + TrashItem(account.id, dir, entry.name, entry.path, entry.isDir, entry.size, System.currentTimeMillis()))
    }

    /** আগের জায়গায় ফেরত; সেখানে একই নাম থাকলে "নাম (1)" */
    suspend fun restore(item: TrashItem): Result<Unit> = runCatching {
        val acc = dao.getById(item.accountId) ?: throw IllegalStateException("Account no longer exists")
        val parent = item.origPath.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) files.mkdir(acc, parent)
        val existing = files.list(acc, parent).getOrDefault(emptyList()).map { it.name }.toSet()
        val name = com.drivemount.app.engine.uniqueName(item.name, existing)
        val dst = if (parent.isEmpty()) name else "$parent/$name"
        files.rename(
            acc,
            com.drivemount.app.engine.RemoteEntry(
                name = item.name, path = item.trashPath, isDir = item.isDir,
                size = item.size, modTimeMillis = null, mimeType = "",
            ),
            dst,
        ).getOrThrow()
        runCatching { api.purge(com.drivemount.app.engine.fsRootFor(acc.remoteName, acc.backendType), item.dir) }
        setTrash(_trash.value - item)
    }

    /** আসলেই মোছা (আর ফেরানো যাবে না) */
    suspend fun deleteForever(item: TrashItem): Result<Unit> = runCatching {
        val acc = dao.getById(item.accountId)
        if (acc != null) api.purge(com.drivemount.app.engine.fsRootFor(acc.remoteName, acc.backendType), item.dir)
        setTrash(_trash.value - item)
    }

    suspend fun emptyTrash(): Int {
        var n = 0
        for (it in _trash.value) if (deleteForever(it).isSuccess) n++
        return n
    }

    /** ৩০ দিনের বেশি পুরোনো ট্র্যাশ মুছে দেয় */
    suspend fun purgeExpired() {
        val limit = System.currentTimeMillis() - Galaxy.TRASH_DAYS * 24L * 3600 * 1000
        for (it in _trash.value.filter { i -> i.at < limit }) deleteForever(it)
    }

    private fun load(): GalaxyConfig = runCatching {
        val o = JSONObject(prefs.getString("cfg", "{}") ?: "{}")
        val on = o.optJSONArray("on")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val m = o.optJSONObject("m")
        val members = HashMap<String, List<Long>>()
        m?.keys()?.forEach { k ->
            val a = m.getJSONArray(k)
            members[k] = (0 until a.length()).map { a.getLong(it) }
        }
        GalaxyConfig(on, members)
    }.getOrDefault(GalaxyConfig())

    private fun save(c: GalaxyConfig) {
        val m = JSONObject()
        c.members.forEach { (k, v) -> m.put(k, JSONArray(v)) }
        prefs.edit().putString("cfg", JSONObject().put("on", JSONArray(c.on.toList())).put("m", m).toString()).apply()
        _config.value = c
    }

    /** সক্রিয় প্ল্যাটফর্ম: (ধরন, সদস্য অ্যাকাউন্ট); সদস্য না থাকলে বাদ */
    private fun active(c: GalaxyConfig, all: List<AccountEntity>): List<Pair<String, List<AccountEntity>>> =
        c.on.sorted().mapNotNull { t ->
            val ms = c.membersOf(t).mapNotNull { id -> all.firstOrNull { it.id == id && it.backendType == t } }
            if (ms.isEmpty()) null else t to ms
        }

    /** সেটিং প্রয়োগ: ভেতরের union (প্রতি প্ল্যাটফর্মে) আর মূল Galaxy union তৈরি/হালনাগাদ */
    suspend fun apply(c: GalaxyConfig): Result<Unit> = runCatching {
        save(c)
        val all = dao.getAll()
        val act = active(c, all)
        val remotes = api.dumpRemotes()
        val wanted = act.map { Galaxy.innerName(it.first) }.toSet()

        if (act.isEmpty()) {
            if (Galaxy.REMOTE in remotes) api.deleteRemote(Galaxy.REMOTE)
            remotes.keys.filter { Galaxy.isInner(it) }.forEach { api.deleteRemote(it) }
            all.firstOrNull { it.remoteName == Galaxy.REMOTE }?.let { dao.delete(it.id) }
            accounts.onChanged?.invoke()
            return@runCatching
        }

        // rclone-এর union-এ কমপক্ষে ২টি upstream লাগে; একটি হলে alias (সরাসরি ওই remote-এর নামান্তর) ব্যবহার হয়
        suspend fun upsert(name: String, upstreams: List<String>, createPolicy: String) {
            val type = if (upstreams.size >= 2) "union" else "alias"
            val params = if (type == "union") {
                mapOf(
                    "upstreams" to upstreams.joinToString(" "),
                    "create_policy" to createPolicy,
                    "action_policy" to "epall",
                    "search_policy" to "ff",
                )
            } else {
                mapOf("remote" to upstreams.first())
            }
            val existing = remotes[name]
            when {
                existing == null -> api.createRemote(name, type, params, obscure = false)
                existing == type -> api.updateRemote(name, params)
                else -> {
                    // ধরন বদলেছে (যেমন অ্যাকাউন্ট ১টি থেকে ২টি হলো): নতুন করে বানানো
                    api.deleteRemote(name)
                    api.createRemote(name, type, params, obscure = false)
                }
            }
        }
        // প্ল্যাটফর্মের ভেতরে: ফাইল সবচেয়ে বেশি খালি জায়গার অ্যাকাউন্টে যায় (জায়গা যোগ হয়ে একটি বড় পুল)
        for ((type, ms) in act) {
            upsert(Galaxy.innerName(type), ms.map { it.remoteName + ":" }, "mfs")
        }
        // ওপরে: প্রতিটি প্ল্যাটফর্ম একটি কপি, লেখা সবকটিতে ("all"), মোছা/সরানো যেখানে আছে সেখানে
        upsert(Galaxy.REMOTE, act.map { Galaxy.innerName(it.first) + ":" }, "all")
        remotes.keys.filter { Galaxy.isInner(it) && it !in wanted }.forEach { api.deleteRemote(it) }

        if (all.none { it.remoteName == Galaxy.REMOTE }) {
            dao.insert(
                AccountEntity(
                    remoteName = Galaxy.REMOTE,
                    displayName = "Galaxy Drive",
                    backendType = "union",
                    createdAt = System.currentTimeMillis(),
                    sortOrder = -1,
                ),
            )
        }
        recomputeUsage()
        accounts.onChanged?.invoke()
    }

    /** সদস্য অ্যাকাউন্টগুলোর (সংরক্ষিত) তথ্য থেকে হিসাব; নেটওয়ার্ক লাগে না */
    suspend fun recomputeUsage() {
        val all = dao.getAll()
        val row = all.firstOrNull { it.remoteName == Galaxy.REMOTE } ?: return
        val cap = capacity(_config.value, all) ?: return
        dao.updateUsage(row.id, (cap.total - cap.free).coerceAtLeast(0), cap.total, System.currentTimeMillis())
    }

    /** সদস্য অ্যাকাউন্টগুলোর নতুন তথ্য এনে হিসাব হালনাগাদ (নেটওয়ার্ক লাগে) */
    suspend fun refreshAll() {
        val all = dao.getAll()
        val ids = _config.value.members.filterKeys { it in _config.value.on }.values.flatten().toSet()
        all.filter { it.id in ids }.forEach { accounts.refreshUsage(it) }
        recomputeUsage()
    }

    /**
     * কপিগুলো মিলিয়ে নেওয়া: এক প্ল্যাটফর্মে থাকা ফাইল অন্যগুলোতে নেই হলে কপি করে (copy, তাই কিছু মোছা হয় না)।
     * ট্রান্সফার-লাইনে যায়, Transfers ট্যাবে দেখা ও বাতিল করা যায়। ফেরত: কতটি কাজ লাইনে দেওয়া হলো।
     */
    suspend fun syncCopies(): Int {
        val act = active(_config.value, dao.getAll())
        if (act.size < 2) return 0
        val jobs = ArrayList<NewTransfer>()
        for ((a, _) in act) for ((b, _) in act) {
            if (a == b) continue
            jobs += NewTransfer(
                name = "Galaxy: $a → $b",
                isDir = true,
                move = false,
                srcFs = Galaxy.innerName(a) + ":",
                srcPath = "",
                dstFs = Galaxy.innerName(b) + ":",
                dstPath = "",
            )
        }
        transfers.enqueue(jobs)
        return jobs.size
    }

    /**
     * এই অ্যাকাউন্টের ফাইলগুলো কোন কোন প্ল্যাটফর্ম-পুলে আছে তা দেখার remote-গুলো (প্রতি প্ল্যাটফর্মে একটি)।
     * Galaxy নিজে হলে ভেতরের পুলগুলো; Galaxy-র ওপরের ভল্ট হলে একই চাবির ডিক্রিপ্ট "দৃশ্য" (লুকানো, প্রয়োজনে বানানো)।
     * Galaxy-সংক্রান্ত না হলে বা প্ল্যাটফর্ম ২টির কম হলে null।
     */
    suspend fun replicaRoots(account: AccountEntity): List<String>? {
        val act = active(_config.value, dao.getAll())
        if (act.size < 2) return null
        if (account.remoteName == Galaxy.REMOTE) return act.map { Galaxy.innerName(it.first) + ":" }
        if (account.backendType != "crypt") return null
        val v = api.dumpConfig()[account.remoteName] ?: return null
        val remote = v.optString("remote")
        if (!remote.startsWith(Galaxy.REMOTE + ":")) return null
        val folder = remote.substringAfter(":")
        val existing = api.dumpRemotes()
        return act.map { (t, _) ->
            val name = Galaxy.viewName(account.remoteName, t)
            if (name !in existing) {
                val params = mutableMapOf("remote" to Galaxy.innerName(t) + ":" + folder)
                for (k in listOf("password", "password2", "filename_encryption", "directory_name_encryption", "filename_encoding", "suffix")) {
                    v.optString(k).takeIf { it.isNotEmpty() }?.let { params[k] = it }
                }
                // পাসওয়ার্ড ইতিমধ্যে obscure করা আছে, তাই আবার obscure নয়
                api.createRemote(name, "crypt", params, obscure = false, noObscure = true)
            }
            "$name:"
        }
    }

    companion object {
        /** ব্যবহারযোগ্য জায়গা = সবচেয়ে ছোট পুল (প্রতিটি কপির জন্য একই জায়গা লাগে) */
        fun capacity(c: GalaxyConfig, all: List<AccountEntity>): GalaxyCapacity? {
            val pools = c.on.sorted().mapNotNull { t ->
                val ms = c.membersOf(t).mapNotNull { id -> all.firstOrNull { it.id == id && it.backendType == t } }
                if (ms.isEmpty()) return@mapNotNull null
                var total = 0L
                var free = 0L
                var unknown = false
                ms.forEach {
                    val tb = it.totalBytes
                    if (tb == null) unknown = true else {
                        total += tb
                        free += (tb - (it.usedBytes ?: 0L)).coerceAtLeast(0L)
                    }
                }
                Triple(t, total to free, unknown)
            }
            if (pools.isEmpty()) return null
            val limiting = pools.minBy { it.second.first }
            return GalaxyCapacity(
                total = limiting.second.first,
                free = pools.minOf { it.second.second },
                platforms = pools.size,
                limitingType = limiting.first,
                partial = pools.any { it.third },
            )
        }
    }
}

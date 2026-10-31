package com.drivemount.app.data

import com.drivemount.app.engine.LocalPaths
import com.drivemount.app.engine.RcloneApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/** নতুন অ্যাকাউন্ট যোগ করার অনুরোধ */
data class NewAccount(
    val displayName: String,
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val obscure: Boolean = false,
    /** true হলে browser দিয়ে সাইন-ইন (Google Drive, pCloud...) */
    val oauth: Boolean = false,
    /** true হলে যোগ করার পরপরই লগইন যাচাই করা হয় (Mega...)। ব্যর্থ হলে অ্যাকাউন্ট যোগ হয় না। */
    val verify: Boolean = false,
    /** true হলে যোগ করার পর remote-এর মূল ফোল্ডার তৈরি/যাচাই হয় (এনক্রিপ্টেড ভল্টের জন্য) */
    val makeRoot: Boolean = false,
)

/**
 * অ্যাকাউন্ট ব্যবস্থাপনার কেন্দ্র: ডাটাবেস (তালিকা) + rclone.conf (আসল সংযোগের তথ্য) একসাথে ঠিক রাখে।
 */
class AccountRepository(
    private val dao: AccountDao,
    private val api: RcloneApi,
) {
    val accounts: Flow<List<AccountEntity>> = dao.observeAll()

    /** তালিকা বদলালে (যোগ/মোছা/নাম বদল) ডাকা হয়, যেমন Files অ্যাপকে জানাতে */
    var onChanged: (() -> Unit)? = null

    /** rclone.conf আর ডাটাবেসের তালিকা মিলিয়ে নেয় (যেমন বাইরে থেকে যোগ/মোছা remote)। */
    suspend fun reconcile() {
        val remotes = api.dumpRemotes()
        val rows = dao.getAll()
        val known = rows.map { it.remoteName }.toSet()

        remotes.filterKeys { it !in known }.forEach { (name, type) ->
            dao.insert(
                AccountEntity(
                    remoteName = name,
                    displayName = name,
                    backendType = type,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        rows.filter { it.remoteName !in remotes }.forEach { dao.delete(it.id) }
    }

    suspend fun addAccount(new: NewAccount): Result<Long> = runCatching {
        // rclone-এর ভেতরের নাম নিরাপদ ও অনন্য রাখা হয়; ব্যবহারকারীর নাম আলাদা (ডাটাবেসে)
        val remote = "dm" + UUID.randomUUID().toString().replace("-", "").take(10)
        val params = if (new.oauth) new.params + OAUTH_ANSWERS else new.params
        val newId = try {
            api.createRemote(remote, new.type, params, new.obscure, nonInteractive = new.oauth)
            if (new.makeRoot) api.mkdir("$remote:", "")

            // Mega-র মতো backend-এ এখানেই লগইন যাচাই হয়; OAuth-এ শুধু ব্যবহারের তথ্য আনার চেষ্টা
            val usage = when {
                new.verify -> api.about(remote)
                new.oauth -> runCatching { api.about(remote) }.getOrNull()
                else -> null
            }

            dao.insert(
                AccountEntity(
                    remoteName = remote,
                    displayName = new.displayName.trim(),
                    backendType = new.type,
                    createdAt = System.currentTimeMillis(),
                    usedBytes = usage?.used,
                    totalBytes = usage?.total,
                    usageUpdatedAt = if (usage != null) System.currentTimeMillis() else null,
                ),
            )
        } catch (t: Throwable) {
            // অসম্পূর্ণ remote যেন rclone.conf-এ থেকে না যায়
            runCatching { api.deleteRemote(remote) }
            throw t
        }
        onChanged?.invoke()
        newId
    }

    suspend fun oauthAuthUrl(): String? = runCatching { api.oauthAuthUrl() }.getOrNull()

    suspend fun cancelOAuth() {
        api.oauthStop()
    }

    suspend fun getById(id: Long): AccountEntity? = dao.getById(id)

    suspend fun getAll(): List<AccountEntity> = dao.getAll()

    /** backup থেকে ফেরানো অ্যাকাউন্টের তালিকা-সারি (rclone-এর remote আগে থেকেই বানানো) */
    suspend fun addImported(remoteName: String, displayName: String, type: String, sortOrder: Int, createdAt: Long) {
        try {
            dao.insert(
                AccountEntity(
                    remoteName = remoteName,
                    displayName = displayName,
                    backendType = type,
                    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
                    sortOrder = sortOrder,
                ),
            )
        } catch (e: Exception) {
            // সারিটি আগেই আছে
        }
        onChanged?.invoke()
    }

    suspend fun rename(id: Long, newName: String) {
        dao.rename(id, newName.trim())
        onChanged?.invoke()
    }

    suspend fun delete(account: AccountEntity): Result<Unit> = runCatching {
        api.deleteRemote(account.remoteName)
        dao.delete(account.id)
        onChanged?.invoke()
        Unit
    }

    suspend fun refreshUsage(account: AccountEntity): Result<Unit> = runCatching {
        // ফোনের স্টোরেজে মূল "/" নয়, ব্যবহারকারীর অভ্যন্তরীণ স্টোরেজের (data) মাপ দেখাতে হয়
        val usage = if (account.backendType == "local") {
            api.about(account.remoteName, "${account.remoteName}:/${LocalPaths.internalRelative()}")
        } else {
            api.about(account.remoteName)
        }
        dao.updateUsage(account.id, usage.used, usage.total, System.currentTimeMillis())
    }

    private val usageTried: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * যে অ্যাকাউন্টের ব্যবহারের তথ্য কখনও আনা হয়নি (যেমন "Import connections" দিয়ে ফেরানো বা নতুন যোগ হওয়া),
     * তার তথ্য নিজে থেকে আনে। প্রতিটি অ্যাকাউন্টের জন্য প্রতি চালুতে একবারই চেষ্টা হয়, তাই ব্যর্থ হলে বারবার ঘোরে না।
     * তালিকা বদলালেই (যেমন import শেষে) আবার জেগে ওঠে। অনির্দিষ্টকাল চলে, তাই viewModelScope-এ ডাকতে হবে।
     */
    suspend fun keepUsageFilled() {
        dao.observeAll()
            .map { list -> list.filter { it.backendType != "crypt" && it.usageUpdatedAt == null }.map { it.id } }
            .distinctUntilChanged()
            .collect { ids ->
                ids.filter { usageTried.add(it) }.forEach { id ->
                    dao.getById(id)?.let { refreshUsage(it) }
                }
            }
    }

    /** অনেকক্ষণ আগের (maxAgeMillis-এর পুরোনো) ব্যবহারের তথ্য নতুন করে আনে */
    suspend fun refreshStaleUsage(maxAgeMillis: Long) {
        val now = System.currentTimeMillis()
        dao.getAll()
            .filter { it.backendType != "crypt" }
            .filter { val at = it.usageUpdatedAt; at != null && now - at > maxAgeMillis }
            .forEach { refreshUsage(it) }
    }

    private companion object {
        /**
         * rclone-এর সাইন-ইন প্রশ্নগুলোর আগাম উত্তর ("config_" দিয়ে শুরু হওয়া key সংরক্ষণ হয় না):
         * - config_is_local: এই ফোনেই browser আছে
         * - config_auth_no_browser: rclone নিজে browser খোলার চেষ্টা করবে না, অ্যাপ খুলবে
         * - config_shared_client_id: নিজের Client ID না দিলে rclone-এর shared ID-তে এগোবে
         * - config_change_team_drive: Shared Drive (Team Drive) হিসেবে সাজানো হবে না
         */
        val OAUTH_ANSWERS = mapOf(
            "config_is_local" to "true",
            "config_auth_no_browser" to "true",
            "config_shared_client_id" to "true",
            "config_change_team_drive" to "false",
        )
    }
}

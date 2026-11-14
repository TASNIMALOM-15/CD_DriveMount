package com.drivemount.app.backup

import com.drivemount.app.data.AccountRepository
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.security.ConfigProtector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ImportResult(val imported: Int, val skipped: Int, val failed: List<String>)

/**
 * সব অ্যাকাউন্টের সংযোগ (token, পাসওয়ার্ড, ভল্টের চাবি) একটি এনক্রিপ্টেড ফাইলে রাখা ও সেখান থেকে ফেরানো।
 * ফাইলে rclone-এর config যেমন আছে তেমনই থাকে (পাসওয়ার্ডগুলো আগে থেকেই obscured), তাই
 * ইমপোর্টে আবার ইমেইল/পাসওয়ার্ড/2FA লাগে না।
 */
class BackupManager(
    private val api: RcloneApi,
    private val accounts: AccountRepository,
    private val protector: ConfigProtector,
) {
    /** (এনক্রিপ্ট করা ফাইলের বিষয়বস্তু, অ্যাকাউন্টের সংখ্যা) */
    suspend fun export(password1: String, password2: String, backupKey: String): Pair<ByteArray, Int> {
        val config = api.dumpConfig()
        val known = accounts.getAll().associateBy { it.remoteName }
        val items = JSONArray()
        for ((name, section) in config) {
            val account = known[name]
            items.put(
                JSONObject()
                    .put("remoteName", name)
                    .put("displayName", account?.displayName ?: name)
                    .put("sortOrder", account?.sortOrder ?: 0)
                    .put("createdAt", account?.createdAt ?: System.currentTimeMillis())
                    .put("config", section),
            )
        }
        val root = JSONObject()
            .put("app", "DriveMount")
            .put("version", 2)
            .put("createdAt", System.currentTimeMillis())
            .put("accounts", items)
        val bytes = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8), password1, password2, backupKey)
        }
        return bytes to config.size
    }

    /** নতুন (v2) ফাইলে তিনটিই লাগে; পুরোনো (v1) ফাইলে শুধু password1 */
    suspend fun importBackup(blob: ByteArray, password1: String, password2: String, backupKey: String): ImportResult {
        val plain = withContext(Dispatchers.Default) {
            if (BackupCrypto.formatOf(blob) == 1) {
                BackupCrypto.decryptLegacy(blob, password1)
            } else {
                BackupCrypto.decrypt(blob, password1, password2, backupKey)
            }
        }
        val root = JSONObject(String(plain, Charsets.UTF_8))
        val items = root.optJSONArray("accounts") ?: JSONArray()

        // ইমপোর্ট করা token যেন খোলা অবস্থায় না থাকে (সম্ভব হলে আগে কনফিগ এনক্রিপ্ট করা হয়)
        runCatching { protector.ensureEnabled() }

        val existing = api.dumpRemotes().keys
        var imported = 0
        var skipped = 0
        val failed = mutableListOf<String>()

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val remote = item.getString("remoteName")
            val display = item.optString("displayName", remote).ifBlank { remote }
            val cfg = item.getJSONObject("config")
            val type = cfg.optString("type")

            if (remote in existing) {
                skipped++
                continue
            }
            val params = LinkedHashMap<String, String>()
            for (key in cfg.keys()) {
                if (key != "type") params[key] = cfg.optString(key)
            }
            try {
                api.createRemote(
                    name = remote,
                    type = type,
                    params = params + IMPORT_ANSWERS,
                    obscure = false,
                    nonInteractive = true,
                    noObscure = true,
                )
                accounts.addImported(remote, display, type, item.optInt("sortOrder"), item.optLong("createdAt"))
                imported++
            } catch (e: Exception) {
                failed += "$display: ${e.message ?: e}"
                runCatching { api.deleteRemote(remote) }
            }
        }
        return ImportResult(imported, skipped, failed)
    }

    private companion object {
        /** সাইন-ইনের প্রশ্নের আগাম উত্তর: আগের token রেখে দাও, নতুন করে লগইন চেয়ো না */
        val IMPORT_ANSWERS = mapOf(
            "config_refresh_token" to "false",
            "config_change_team_drive" to "false",
            "config_shared_client_id" to "true",
            "config_auth_no_browser" to "true",
        )
    }
}

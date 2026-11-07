package com.drivemount.app.engine

import org.json.JSONObject
import org.rclone.gomobile.Gomobile

/** rclone RPC-এর ফলাফল। status 200 মানে সফল। */
data class RcloneResult(val status: Int, val output: String) {
    val isOk: Boolean get() = status == 200
}

data class RcloneVersion(
    val version: String,
    val goVersion: String,
    val os: String,
    val arch: String,
)

class RcloneException(message: String) : Exception(message)

/**
 * rclone engine-এর একমাত্র প্রবেশপথ।
 * সব ফাংশন blocking, তাই Dispatchers.IO থেকে কল করতে হবে।
 */
object RcloneEngine {

    @Volatile
    private var initialized = false

    /** rclone.conf-এর এনক্রিপশন কী; অ্যাপ চালুর সময় Android Keystore থেকে এনে বসানো হয় */
    @Volatile
    var configPassword: String? = null

    @Synchronized
    fun initialize(configPath: String) {
        if (initialized) return
        // কনফিগ এনক্রিপ্টেড কিন্তু কী নেই: এগোলে rclone অস্তিত্বহীন টার্মিনালে পাসওয়ার্ড চাইত
        if (isConfigFileEncrypted(configPath) && configPassword == null) {
            throw RcloneException("Saved logins are locked and their key is missing on this phone")
        }
        Gomobile.rcloneInitialize()
        initialized = true
        // কখনো পাসওয়ার্ডের জন্য অপেক্ষা নয়
        rpc("options/set", JSONObject().put("main", JSONObject().put("AskPassword", false)))
        configPassword?.let { call("config/unlock", JSONObject().put("configPassword", it)) }
        call("config/setpath", JSONObject().put("path", configPath))
    }

    /** কনফিগ ফাইলের প্রথম অর্থপূর্ণ লাইন "RCLONE_ENCRYPT_V0:" হলে এনক্রিপ্টেড */
    fun isConfigFileEncrypted(path: String): Boolean {
        val file = java.io.File(path)
        if (!file.exists()) return false
        return runCatching {
            file.bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith(";") && !it.startsWith("#") }
                    ?.startsWith("RCLONE_ENCRYPT_V") == true
            }
        }.getOrDefault(false)
    }

    @Synchronized
    fun shutdown() {
        if (!initialized) return
        Gomobile.rcloneFinalize()
        initialized = false
    }

    /** rclone RC method কল করে। ব্যর্থ হলে RcloneResult-এ status != 200 থাকবে। */
    fun rpc(method: String, params: JSONObject = JSONObject()): RcloneResult {
        check(initialized) { "RcloneEngine initialize করা হয়নি" }
        val r = Gomobile.rcloneRPC(method, params.toString())
        return RcloneResult(r.status.toInt(), r.output)
    }

    /** rpc() এর মতো, তবে ব্যর্থ হলে exception ছোড়ে এবং JSON ফেরত দেয়। */
    fun call(method: String, params: JSONObject = JSONObject()): JSONObject {
        val r = rpc(method, params)
        if (!r.isOk) {
            val msg = runCatching { JSONObject(r.output).optString("error") }.getOrNull()
            throw RcloneException("$method ব্যর্থ (${r.status}): ${msg?.ifBlank { null } ?: r.output}")
        }
        return JSONObject(r.output.ifBlank { "{}" })
    }

    fun version(): RcloneVersion {
        val j = call("core/version")
        return RcloneVersion(
            version = j.optString("version"),
            goVersion = j.optString("goVersion"),
            os = j.optString("os"),
            arch = j.optString("arch"),
        )
    }

    fun listRemotes(): List<String> {
        val arr = call("config/listremotes").optJSONArray("remotes") ?: return emptyList()
        return List(arr.length()) { arr.getString(it) }
    }
}

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

    @Synchronized
    fun initialize(configPath: String) {
        if (initialized) return
        Gomobile.rcloneInitialize()
        initialized = true
        call("config/setpath", JSONObject().put("path", configPath))
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

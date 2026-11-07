package com.drivemount.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.OffsetDateTime

/** operations/about-এর ফলাফল। কোনো backend যে মান দেয় না সেটি null। */
data class StorageUsage(val total: Long?, val used: Long?, val free: Long?)

/** পেছনে চলা rclone কাজের অবস্থা */
data class JobStatus(val finished: Boolean, val success: Boolean, val error: String)

/** একটি কাজের গ্রুপের পরিসংখ্যান (বাইট, গতি...) */
data class GroupStats(val bytes: Long, val totalBytes: Long, val speed: Double)

/**
 * rclone engine-এর suspend ভিত্তিক, টাইপ-করা প্রবেশপথ।
 * প্রতিটি কল IO thread-এ চলে এবং দরকারে engine নিজেই চালু করে নেয়।
 */
class RcloneApi(private val configPath: String) {

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        RcloneEngine.initialize(configPath)
        block()
    }

    /** config ফাইলের সব remote: নাম -> backend type */
    suspend fun dumpRemotes(): Map<String, String> = io {
        val json = RcloneEngine.call("config/dump")
        val result = LinkedHashMap<String, String>()
        for (key in json.keys()) {
            result[key] = json.optJSONObject(key)?.optString("type").orEmpty()
        }
        result
    }

    /** সব remote-এর পূর্ণ সেটিং (backup-এর জন্য): নাম -> সেটিং-এর JSON */
    suspend fun dumpConfig(): Map<String, JSONObject> = io {
        val json = RcloneEngine.call("config/dump")
        val result = LinkedHashMap<String, JSONObject>()
        for (key in json.keys()) {
            json.optJSONObject(key)?.let { result[key] = it }
        }
        result
    }

    /**
     * নতুন remote বানায়। obscure = true হলে password-গুলো rclone নিজে obscure করে।
     *
     * nonInteractive = true হলে rclone কোনো প্রশ্ন করে না (stdin নেই); আগে থেকে "config_..."
     * নামের উত্তর params-এ দিয়ে রাখতে হয়। browser দিয়ে সাইন-ইন (OAuth) হলে এই কল ততক্ষণ
     * অপেক্ষা করে (block করে) যতক্ষণ না সাইন-ইন শেষ হয় বা oauthStop() চালানো হয়।
     */
    suspend fun createRemote(
        name: String,
        type: String,
        params: Map<String, String>,
        obscure: Boolean,
        nonInteractive: Boolean = false,
        noObscure: Boolean = false,
    ) {
        io {
            val p = JSONObject()
            params.forEach { (k, v) -> p.put(k, v) }
            val opt = JSONObject().put("obscure", obscure).put("noOutput", true)
            if (nonInteractive) opt.put("nonInteractive", true)
            if (noObscure) opt.put("noObscure", true)

            val out = RcloneEngine.call(
                "config/create",
                JSONObject()
                    .put("name", name)
                    .put("type", type)
                    .put("parameters", p)
                    .put("opt", opt),
            )

            if (nonInteractive) {
                val error = out.optString("Error")
                if (error.isNotBlank()) throw RcloneException(error)
                if (out.optString("State").isNotBlank()) {
                    val question = out.optJSONObject("Option")?.optString("Name").orEmpty()
                    throw RcloneException("Unexpected extra setup step: $question")
                }
            }
            Unit
        }
    }

    /** কনফিগ ফাইলের এনক্রিপশন কী বসায়; এরপর প্রতিটি সংরক্ষণ এনক্রিপ্টেড হয় */
    suspend fun unlockConfig(password: String) {
        io { RcloneEngine.call("config/unlock", JSONObject().put("configPassword", password)) }
    }

    /** কনফিগ ফাইল নতুন করে সংরক্ষণ করায় (অস্থায়ী remote বানিয়ে মুছে) */
    suspend fun touchConfig() {
        io {
            RcloneEngine.call(
                "config/create",
                JSONObject()
                    .put("name", "dmtouch")
                    .put("type", "local")
                    .put("parameters", JSONObject())
                    .put("opt", JSONObject().put("noOutput", true)),
            )
            RcloneEngine.call("config/delete", JSONObject().put("name", "dmtouch"))
        }
    }

    suspend fun deleteRemote(name: String) {
        io { RcloneEngine.call("config/delete", JSONObject().put("name", name)) }
    }

    /** সঞ্চয়স্থানের ব্যবহার। সব backend এটি সমর্থন করে না, তখন exception ছোড়ে। */
    suspend fun about(remoteName: String, fs: String = "$remoteName:"): StorageUsage = io {
        val j = RcloneEngine.call("operations/about", JSONObject().put("fs", fs))
        fun field(key: String): Long? = if (j.has(key) && !j.isNull(key)) j.getLong(key) else null
        StorageUsage(field("total"), field("used"), field("free"))
    }

    // ----------------------------------------------------------------------
    // ফাইল ও ফোল্ডারের কাজ
    // ----------------------------------------------------------------------

    /** একটি ফোল্ডারের সরাসরি ভেতরের জিনিসগুলো */
    suspend fun list(fs: String, path: String): List<RemoteEntry> = io {
        val j = RcloneEngine.call(
            "operations/list",
            JSONObject().put("fs", fs).put("remote", path).put("opt", JSONObject()),
        )
        val arr = j.optJSONArray("list")
        val result = ArrayList<RemoteEntry>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("Name")
                if (name.isEmpty()) continue
                val isDir = o.optBoolean("IsDir")
                result += RemoteEntry(
                    name = name,
                    path = joinPath(path, name),
                    isDir = isDir,
                    size = if (isDir) null else o.optLong("Size", -1L).takeIf { it >= 0 },
                    modTimeMillis = parseModTime(o.optString("ModTime")),
                    mimeType = o.optString("MimeType"),
                )
            }
        }
        result
    }

    /** ফোল্ডারের ভেতরের সব ফাইল (সাব-ফোল্ডার সহ); path = রুট থেকে পূর্ণ পথ */
    suspend fun listFilesRecursive(fs: String, path: String): List<RemoteEntry> = io {
        val j = RcloneEngine.call(
            "operations/list",
            JSONObject().put("fs", fs).put("remote", path)
                .put("opt", JSONObject().put("recurse", true).put("filesOnly", true)),
        )
        val arr = j.optJSONArray("list")
        val result = ArrayList<RemoteEntry>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("Name")
                val rel = o.optString("Path")
                if (name.isEmpty() || rel.isEmpty()) continue
                result += RemoteEntry(
                    name = name,
                    // rclone-এর "Path" রুট থেকেই পূর্ণ পথ; আবার ফোল্ডারের পথ জুড়তে হয় না
                    path = rel,
                    isDir = false,
                    size = o.optLong("Size", -1L).takeIf { it >= 0 },
                    modTimeMillis = parseModTime(o.optString("ModTime")),
                    mimeType = o.optString("MimeType"),
                )
            }
        }
        result
    }

    /** একটি ফাইল বা ফোল্ডারের তথ্য; না থাকলে null */
    suspend fun stat(fs: String, path: String): RemoteEntry? = io {
        val item = RcloneEngine.call(
            "operations/stat",
            JSONObject().put("fs", fs).put("remote", path).put("opt", JSONObject()),
        ).optJSONObject("item")
        if (item == null) {
            null
        } else {
            val isDir = item.optBoolean("IsDir")
            RemoteEntry(
                name = item.optString("Name"),
                path = path,
                isDir = isDir,
                size = if (isDir) null else item.optLong("Size", -1L).takeIf { it >= 0 },
                modTimeMillis = parseModTime(item.optString("ModTime")),
                mimeType = item.optString("MimeType"),
            )
        }
    }

    suspend fun mkdir(fs: String, path: String) {
        io { RcloneEngine.call("operations/mkdir", JSONObject().put("fs", fs).put("remote", path)) }
    }

    suspend fun deleteFile(fs: String, path: String) {
        io { RcloneEngine.call("operations/deletefile", JSONObject().put("fs", fs).put("remote", path)) }
    }

    /** ফোল্ডার ও তার ভেতরের সব কিছু মোছে */
    suspend fun purge(fs: String, path: String) {
        io { RcloneEngine.call("operations/purge", JSONObject().put("fs", fs).put("remote", path)) }
    }

    /** একটি ফাইল কপি বা সরানো (নাম বদলও এটি দিয়েই, একই fs-এ সরিয়ে) */
    suspend fun transferFile(srcFs: String, srcPath: String, dstFs: String, dstPath: String, move: Boolean) {
        io {
            RcloneEngine.call(
                if (move) "operations/movefile" else "operations/copyfile",
                JSONObject()
                    .put("srcFs", srcFs)
                    .put("srcRemote", srcPath)
                    .put("dstFs", dstFs)
                    .put("dstRemote", dstPath),
            )
        }
    }

    /** পুরো ফোল্ডার কপি বা সরানো। srcFs/dstFs-এ ফোল্ডারের পথসহ দিতে হয় (যেমন "r:Docs/a")। */
    suspend fun transferDir(srcFs: String, dstFs: String, move: Boolean) {
        io {
            val p = JSONObject()
                .put("srcFs", srcFs)
                .put("dstFs", dstFs)
                .put("createEmptySrcDirs", true)
            if (move) p.put("deleteEmptySrcDirs", true)
            RcloneEngine.call(if (move) "sync/move" else "sync/copy", p)
        }
    }

    // ----------------------------------------------------------------------
    // ব্যাকগ্রাউন্ড কাজ (ট্রান্সফার): শুরু, অবস্থা, বাতিল
    // ----------------------------------------------------------------------

    private suspend fun startJob(method: String, params: JSONObject, group: String): Long = io {
        val p = JSONObject(params.toString()).put("_async", true).put("_group", group)
        RcloneEngine.call(method, p).getLong("jobid")
    }

    suspend fun startFileJob(
        srcFs: String,
        srcPath: String,
        dstFs: String,
        dstPath: String,
        move: Boolean,
        group: String,
    ): Long = startJob(
        if (move) "operations/movefile" else "operations/copyfile",
        JSONObject()
            .put("srcFs", srcFs)
            .put("srcRemote", srcPath)
            .put("dstFs", dstFs)
            .put("dstRemote", dstPath),
        group,
    )

    /** srcFs/dstFs-এ ফোল্ডারের পথসহ দিতে হয় */
    suspend fun startDirJob(srcFs: String, dstFs: String, move: Boolean, group: String, mirror: Boolean = false): Long {
        val p = JSONObject()
            .put("srcFs", srcFs)
            .put("dstFs", dstFs)
            .put("createEmptySrcDirs", true)
        if (move) p.put("deleteEmptySrcDirs", true)
        return startJob(
            when {
                mirror -> "sync/sync"
                move -> "sync/move"
                else -> "sync/copy"
            },
            p,
            group,
        )
    }

    suspend fun jobStatus(jobId: Long): JobStatus = io {
        val j = RcloneEngine.call("job/status", JSONObject().put("jobid", jobId))
        JobStatus(j.optBoolean("finished"), j.optBoolean("success"), j.optString("error"))
    }

    suspend fun groupStats(group: String): GroupStats = io {
        val j = RcloneEngine.call("core/stats", JSONObject().put("group", group))
        GroupStats(j.optLong("bytes"), j.optLong("totalBytes"), j.optDouble("speed", 0.0))
    }

    suspend fun stopJob(jobId: Long) {
        io { runCatching { RcloneEngine.call("job/stop", JSONObject().put("jobid", jobId)) } }
    }

    suspend fun deleteStats(group: String) {
        io { runCatching { RcloneEngine.call("core/stats-delete", JSONObject().put("group", group)) } }
    }

    private fun parseModTime(text: String): Long? {
        if (text.isBlank()) return null
        val millis = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        // শূন্য সময় ("0001-01-01...") মানে অজানা
        return millis?.takeIf { it > 0L }
    }

    /** ফাইলটি সরাসরি চালানোর (streaming) লোকাল URL; ইঞ্জিন চালু না থাকলে আগে চালু করে নেয় */
    suspend fun streamUrl(fsSpec: String, remote: String, mime: String): String = io {
        StreamServer.urlFor(fsSpec, remote, mime)
    }

    /** Terabox-এর নিজস্ব HLS স্ট্রিমের লোকাল URL (দ্রুত চালু হয়); না পারলে exception */
    suspend fun hlsUrl(fsSpec: String, remote: String, quality: String): String = io {
        StreamServer.hlsUrlFor(fsSpec, remote, quality)
    }

    /** OAuth সাইন-ইন চলছে কি না; চললে browser-এ খোলার URL (rclone-এর নিজস্ব local server)। */
    suspend fun oauthAuthUrl(): String? = io {
        val j = RcloneEngine.call("config/oauthstatus")
        if (j.optString("status") == "running") j.optString("authUrl").ifBlank { null } else null
    }

    /** চলমান OAuth সাইন-ইন বাতিল করে (অপেক্ষারত createRemote কল তখন error দিয়ে ফেরে)। */
    suspend fun oauthStop() {
        io { runCatching { RcloneEngine.call("config/oauthstop") } }
    }
}

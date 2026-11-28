package com.drivemount.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * ফোল্ডারের আনুমানিক আকার: প্রতিবার হিসাব না করে, আপলোড/কপি/মোছার সময় ফাইলের আকার থেকে জোড়া-বিয়োগ করা হয়।
 * যে ফোল্ডারের আকার জানা নেই (আগে থেকে ক্লাউডে থাকা), তার কিছু দেখানো হয় না।
 * কী = "remoteName|path" (path-এ শুরু/শেষের '/' থাকে না)।
 */
class FolderSizes(context: Context) {
    private val file = File(context.filesDir, "folder_sizes.json")
    private val map = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var writePending = false

    /** বদলালে বাড়ে; UI এটি দেখে নতুন করে আঁকে */
    val version = MutableStateFlow(0)

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                o.keys().forEach { map[it] = o.getLong(it) }
            }
        }
    }

    private fun key(remote: String, path: String) = "$remote|${path.trim('/')}"

    fun get(remote: String, path: String): Long? = map[key(remote, path)]

    private fun ancestors(path: String): List<String> {
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        return (1 until parts.size).map { parts.take(it).joinToString("/") }
    }

    /** [path] (ফাইল বা নতুন ফোল্ডার) যোগ হলো: তার ওপরের সব ফোল্ডারে [bytes] যোগ; ফোল্ডার হলে সেটির নিজের আকারও */
    fun added(remote: String, path: String, bytes: Long, isDir: Boolean) {
        if (bytes <= 0) return
        if (isDir) map.merge(key(remote, path), bytes, Long::plus)
        // ওপরের ফোল্ডারে যোগ শুধু তখনই, যখন তার আকার আগে থেকে জানা (নইলে অসম্পূর্ণ সংখ্যা দেখাত) বা এটিই প্রথম ফাইল
        for (a in ancestors(path)) map.merge(key(remote, a), bytes, Long::plus)
        changed()
    }

    /** [path] মুছল বা সরে গেল: ওপরের ফোল্ডার থেকে বিয়োগ; ফোল্ডার হলে তার ও ভেতরের সবার হিসাব মোছা */
    fun removed(remote: String, path: String, bytes: Long?, isDir: Boolean) {
        val own = if (isDir) map[key(remote, path)] else null
        val delta = bytes?.takeIf { it > 0 } ?: own ?: 0L
        if (isDir) {
            val k = key(remote, path)
            map.keys.removeIf { it == k || it.startsWith("$k/") }
        }
        if (delta > 0) for (a in ancestors(path)) {
            val k = key(remote, a)
            map.computeIfPresent(k) { _, v -> (v - delta).coerceAtLeast(0L) }
        }
        changed()
    }

    private fun changed() {
        version.update { it + 1 }
        synchronized(this) {
            if (writePending) return
            writePending = true
        }
        scope.launch {
            delay(1500)
            synchronized(this@FolderSizes) { writePending = false }
            runCatching {
                val o = JSONObject()
                map.forEach { (k, v) -> o.put(k, v) }
                file.writeText(o.toString())
            }
        }
    }
}

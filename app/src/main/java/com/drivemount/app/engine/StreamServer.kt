package com.drivemount.app.engine

import java.lang.reflect.InvocationTargetException

/**
 * rclone-এর ভেতরের লোকালহোস্ট streaming সার্ভার (Go প্যাকেজ dmstream)।
 * প্যাকেজটি build-এ compile না হলে AAR-এ থাকে না; তাই reflection দিয়ে ডাকা হয় এবং না থাকলে স্পষ্ট বার্তা দেয়।
 */
object StreamServer {

    private const val CLASS_NAME = "org.rclone.dmstream.Dmstream"

    @Volatile
    private var port = 0L

    private val clazz: Class<*>? by lazy { runCatching { Class.forName(CLASS_NAME) }.getOrNull() }

    fun isAvailable(): Boolean = clazz != null

    /** fsSpec-এর remote পথের ফাইলের জন্য একটি গোপন লোকাল URL দেয় (শুধু এই ফোনেই চলে) */
    @Synchronized
    fun urlFor(fsSpec: String, remote: String, mime: String): String {
        val c = clazz ?: throw RcloneException("Instant playback is not available in this build")
        try {
            if (port == 0L) {
                port = c.getMethod("startStreamServer").invoke(null) as Long
            }
            val token = c.getMethod(
                "prepareStream",
                String::class.java,
                String::class.java,
                String::class.java,
            ).invoke(null, fsSpec, remote, mime) as String
            return "http://127.0.0.1:$port/s/$token"
        } catch (e: InvocationTargetException) {
            throw RcloneException(e.targetException?.message ?: e.toString())
        }
    }

    /**
     * দূরবর্তী সার্ভারের নিজস্ব HLS তালিকার লোকাল URL (এখন শুধু Terabox)।
     * ব্যর্থ হলে exception দেয়; তখন মূল ফাইল চালানো হয়।
     */
    @Synchronized
    fun hlsUrlFor(fsSpec: String, remote: String, quality: String): String {
        val c = clazz ?: throw RcloneException("Instant playback is not available in this build")
        try {
            if (port == 0L) {
                port = c.getMethod("startStreamServer").invoke(null) as Long
            }
            val token = c.getMethod(
                "prepareHls",
                String::class.java,
                String::class.java,
                String::class.java,
            ).invoke(null, fsSpec, remote, quality) as String
            return "http://127.0.0.1:$port/h/$token"
        } catch (e: InvocationTargetException) {
            throw RcloneException(e.targetException?.message ?: e.toString())
        }
    }
}

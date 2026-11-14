package com.drivemount.app.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Mega-র নিজস্ব থাম্বনেল (সার্ভারে আগে থেকে তৈরি ছোট ছবি) সরাসরি আনে: ভিডিও/ছবি নামাতে হয় না।
 * কোনো ধাপে ব্যর্থ হলে null দেয়; তখন অ্যাপ আগের পদ্ধতিতে (ফাইল থেকে ফ্রেম) থাম্বনেল বানায়।
 */
object MegaThumbs {
    private const val API = "https://g.api.mega.co.nz/cs"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private class Node(val handle: String, val parent: String, val type: Int, val name: String, val aesKey: ByteArray, val fa: String?)

    private class Session(val sid: String, val files: Map<String, Node>)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val gate = Semaphore(8)
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Mega অ্যাকাউন্টের ফাইলের থাম্বনেল; না পেলে null */
    suspend fun thumbnail(context: Context, account: AccountEntity, entry: RemoteEntry, cacheKey: String): Bitmap? {
        memory.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val disk = diskFile(context, cacheKey)
            if (disk.exists()) {
                BitmapFactory.decodeFile(disk.path)?.let { memory.put(cacheKey, it); return@withContext it }
            }
            val bmp = runCatching { fetch(context, account, entry) }.getOrNull()
            if (bmp != null) {
                memory.put(cacheKey, bmp)
                runCatching { FileOutputStream(disk).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            }
            bmp
        }
    }

    private fun diskFile(context: Context, key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(("mega|$key").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "thumbs_mega").apply { mkdirs() }, "$hash.jpg")
    }

    private suspend fun fetch(context: Context, account: AccountEntity, entry: RemoteEntry): Bitmap? {
        val session = session(context, account) ?: return null
        val node = session.files[entry.path.trim('/')] ?: return null
        val fa = node.fa ?: return null
        val handle = Regex("(?:^|/)(?:\\d+:)?0\\*([A-Za-z0-9_-]+)").find(fa)?.groupValues?.get(1) ?: return null
        return gate.withPermit {
            val res = call(session.sid, JSONObject().put("a", "ufa").put("fah", handle).put("ssl", 2).put("r", 1))
            val url = (res as? JSONObject)?.optString("p").orEmpty()
            if (url.isEmpty()) return@withPermit null
            val body = b64d(handle).toRequestBody("application/octet-stream".toMediaType())
            val data = http.newCall(Request.Builder().url("$url/0").post(body).build()).execute().use {
                if (!it.isSuccessful) return@use null
                it.body?.bytes()
            } ?: return@withPermit null
            // [8 byte handle][4 byte LE length][data]
            if (data.size < 12) return@withPermit null
            val len = (data[8].toInt() and 0xff) or ((data[9].toInt() and 0xff) shl 8) or
                ((data[10].toInt() and 0xff) shl 16) or ((data[11].toInt() and 0xff) shl 24)
            if (len <= 0 || 12 + len > data.size) return@withPermit null
            val enc = data.copyOfRange(12, 12 + len)
            val dec = cbc(node.aesKey, enc.copyOf(enc.size and 15.inv()))
            BitmapFactory.decodeByteArray(dec, 0, dec.size)
        }
    }

    // ------------------------------------------------------------------ session

    private suspend fun session(context: Context, account: AccountEntity): Session? {
        val name = account.remoteName
        sessions[name]?.let { return it }
        val last = failedAt[name]
        if (last != null && System.currentTimeMillis() - last < 10 * 60_000L) return null
        val lock = locks.getOrPut(name) { Mutex() }
        return lock.withLock {
            sessions[name]?.let { return@withLock it }
            val s = runCatching { login(context, name) }.getOrNull()
            if (s == null) failedAt[name] = System.currentTimeMillis() else sessions[name] = s
            s
        }
    }

    private suspend fun login(context: Context, remoteName: String): Session = withContext(Dispatchers.IO) {
        val app = context.applicationContext as DriveMountApp
        val conf = app.rcloneApi.dumpConfig()[remoteName] ?: error("no config")
        val email = conf.optString("user").trim().lowercase()
        val password = reveal(conf.optString("pass"))
        val mfa = conf.optString("2fa")

        val pre = call(null, JSONObject().put("a", "us0").put("user", email)) as JSONObject
        val passKey: ByteArray
        val uh: String
        if (pre.optInt("v", 1) == 2) {
            val dk = pbkdf2Sha512(password.toByteArray(Charsets.UTF_8), b64d(pre.getString("s")), 100_000, 32)
            passKey = dk.copyOfRange(0, 16)
            uh = b64e(dk.copyOfRange(16, 32))
        } else {
            passKey = prepareKey(password)
            uh = stringHash(email, passKey)
        }
        val us = JSONObject().put("a", "us").put("user", email).put("uh", uh)
        if (mfa.isNotBlank()) us.put("mfa", mfa)
        val r = call(null, us) as JSONObject
        val master = ecb(passKey, b64d(r.getString("k")), false)
        val sid = when {
            r.has("tsid") -> r.getString("tsid")
            r.has("csid") -> {
                val priv = b64d(r.getString("privk"))
                val padded = priv.copyOf((priv.size + 15) / 16 * 16)
                val pk = ecb(master, padded, false)
                var pos = 0
                val (p, p1) = readMpi(pk, pos); pos = p1
                val (q, p2) = readMpi(pk, pos); pos = p2
                val (d, _) = readMpi(pk, pos)
                val (c, _) = readMpi(b64d(r.getString("csid")), 0)
                var m = c.modPow(d, p.multiply(q)).toByteArray()
                if (m.isNotEmpty() && m[0] == 0.toByte()) m = m.copyOfRange(1, m.size)
                b64e(m.copyOfRange(0, 43))
            }
            else -> error("no session")
        }
        val me = r.optString("u")

        // সব নোড আনা: নাম, key, থাম্বনেল-হ্যান্ডেল
        val f = call(sid, JSONObject().put("a", "f").put("c", 1).put("r", 1)) as JSONObject
        val arr = f.getJSONArray("f")
        val raw = HashMap<String, Node>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.optInt("t", 0)
            val ks = o.optString("k")
            val keyPart = ks.split('/').firstOrNull { it.startsWith("$me:") }?.substringAfter(':') ?: continue
            val nodeKey = runCatching { ecb(master, b64d(keyPart), false) }.getOrNull() ?: continue
            val aes = if (t == 0 && nodeKey.size >= 32) {
                ByteArray(16) { (nodeKey[it].toInt() xor nodeKey[it + 16].toInt()).toByte() }
            } else {
                nodeKey.copyOf(16)
            }
            val nm = if (t >= 2) "" else runCatching {
                val attr = cbc(aes, b64d(o.getString("a")).let { it.copyOf(it.size and 15.inv()) })
                val txt = String(attr, Charsets.UTF_8).trimEnd('\u0000')
                JSONObject(txt.removePrefix("MEGA")).optString("n")
            }.getOrDefault("")
            val h = o.getString("h")
            raw[h] = Node(h, o.optString("p"), t, nm, aes, o.optString("fa").ifEmpty { null })
        }
        // পথ বানানো (শুধু রুট "Cloud Drive"-এর ভেতরের ফাইল)
        val result = HashMap<String, Node>()
        for (n in raw.values) {
            if (n.type != 0 || n.name.isEmpty()) continue
            val parts = ArrayList<String>()
            parts += n.name
            var cur = raw[n.parent]
            var guard = 0
            var ok = false
            while (cur != null && guard++ < 200) {
                if (cur.type == 2) { ok = true; break }
                if (cur.type != 1) break
                parts += cur.name
                cur = raw[cur.parent]
            }
            if (ok) result.putIfAbsent(parts.asReversed().joinToString("/"), n)
        }
        Session(sid, result)
    }

    // ------------------------------------------------------------------ API

    private fun call(sid: String?, req: JSONObject): Any {
        val url = API + "?id=" + (System.nanoTime() and 0x7fffffff) + (if (sid != null) "&sid=$sid" else "")
        val body = JSONArray().put(req).toString().toRequestBody("application/json".toMediaType())
        val text = http.newCall(Request.Builder().url(url).post(body).build()).execute().use {
            it.body?.string().orEmpty()
        }
        val trimmed = text.trim()
        if (!trimmed.startsWith("[")) error("mega error $trimmed")
        val first = JSONArray(trimmed).get(0)
        if (first is Int) error("mega error $first")
        return first
    }

    // ------------------------------------------------------------------ crypto

    private fun b64d(s: String): ByteArray = Base64.decode(s.replace('+', '-').replace('/', '_'), Base64.URL_SAFE)
    private fun b64e(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun ecb(key: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray {
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(data)
    }

    private fun cbc(key: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return c.doFinal(data)
    }

    private fun readMpi(buf: ByteArray, pos: Int): Pair<BigInteger, Int> {
        val bits = ((buf[pos].toInt() and 0xff) shl 8) or (buf[pos + 1].toInt() and 0xff)
        val len = (bits + 7) / 8
        return BigInteger(1, buf.copyOfRange(pos + 2, pos + 2 + len)) to (pos + 2 + len)
    }

    /** Mega account v2: PBKDF2-HMAC-SHA512 */
    private fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(if (password.isEmpty()) byteArrayOf(0) else password, "HmacSHA512"))
        val out = ByteArray(outLen)
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        System.arraycopy(t, 0, out, 0, outLen)
        return out
    }

    /** Mega account v1: পুরনো key-derivation */
    private fun prepareKey(password: String): ByteArray {
        val pw = password.toByteArray(Charsets.UTF_8)
        val padded = pw.copyOf(maxOf(16, (pw.size + 15) / 16 * 16))
        var pkey = intsToBytes(intArrayOf(0x93C467E3.toInt(), 0x7DB0C7A4, 0xD1BE3F81.toInt(), 0x0152CB56))
        val ciphers = (0 until padded.size step 16).map {
            Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(padded.copyOfRange(it, it + 16), "AES")) }
        }
        repeat(0x10000) { for (c in ciphers) pkey = c.doFinal(pkey) }
        return pkey
    }

    private fun stringHash(s: String, key: ByteArray): String {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val padded = bytes.copyOf((bytes.size + 3) / 4 * 4)
        val h = IntArray(4)
        for (i in 0 until padded.size / 4) {
            val w = ((padded[i * 4].toInt() and 0xff) shl 24) or ((padded[i * 4 + 1].toInt() and 0xff) shl 16) or
                ((padded[i * 4 + 2].toInt() and 0xff) shl 8) or (padded[i * 4 + 3].toInt() and 0xff)
            h[i and 3] = h[i and 3] xor w
        }
        var hb = intsToBytes(h)
        val c = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        repeat(0x4000) { hb = c.doFinal(hb) }
        return b64e(hb.copyOfRange(0, 4) + hb.copyOfRange(8, 12))
    }

    private fun intsToBytes(a: IntArray): ByteArray {
        val out = ByteArray(a.size * 4)
        for (i in a.indices) for (j in 0 until 4) out[i * 4 + j] = (a[i] ushr (24 - 8 * j)).toByte()
        return out
    }

    /** rclone-এর obscure করা পাসওয়ার্ড ফিরিয়ে আনা (AES-CTR, নির্দিষ্ট key) */
    private fun reveal(obscured: String): String {
        val key = intArrayOf(
            0x9c, 0x93, 0x5b, 0x48, 0x73, 0x0a, 0x55, 0x4d, 0x6b, 0xfd, 0x7c, 0x63, 0xc8, 0x86, 0xa9, 0x2b,
            0xd3, 0x90, 0x19, 0x8e, 0xb8, 0x12, 0x8a, 0xfb, 0xf4, 0xde, 0x16, 0x2b, 0x8b, 0x95, 0xf6, 0x38,
        ).map { it.toByte() }.toByteArray()
        val raw = b64d(obscured)
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(raw.copyOfRange(0, 16)))
        return String(c.doFinal(raw.copyOfRange(16, raw.size)), Charsets.UTF_8)
    }
}

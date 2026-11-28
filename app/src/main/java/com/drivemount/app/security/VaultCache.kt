package com.drivemount.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * এনক্রিপ্টেড ভল্টের লোকাল ক্যাশ (ফোল্ডার-তালিকা, থাম্বনেল)।
 *
 * ভল্টের ফাইলের নাম ও ছবি ডিস্কে সাদা অবস্থায় থাকে না:
 *  - প্রতিটি ফাইলের মান AES-256-GCM দিয়ে এনক্রিপ্ট করা
 *  - ফাইলের নাম হলো (namespace + key)-এর SHA-256 হ্যাশ, তাই আসল পথ ডিস্কে দেখা যায় না
 *  - ডেটা-কী র‍্যান্ডম; সেটি Android Keystore-এর কী দিয়ে মুড়ে রাখা হয় (ফাইলটি অন্য ফোনে কপি করলে খোলে না)
 *
 * বড় পরিমাণ ডেটা প্রতিবার Keystore-এ না পাঠিয়ে মেমরিতে থাকা ডেটা-কী দিয়ে এনক্রিপ্ট হয়, তাই দ্রুত।
 */
class VaultCache(
    context: Context,
    dirName: String = "vault_cache",
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    /** true হলে পড়ার সময় ফাইলের সময় নতুন হয়, তাই ছাঁটাইয়ের সময় সবচেয়ে কম-ব্যবহৃতগুলো আগে যায় */
    private val touchOnRead: Boolean = false,
) {

    private val root = File(context.applicationContext.filesDir, dirName).apply { mkdirs() }
    private val keyFile = File(context.applicationContext.filesDir, "vaultcache.key")
    private val random = SecureRandom()
    private val writes = AtomicInteger(0)

    private val dataKey: SecretKey? by lazy { runCatching { loadOrCreateDataKey() }.getOrNull() }

    /** কী না পাওয়া গেলে (Keystore সমস্যা) কিছুই ডিস্কে লেখা হয় না: সাদা অবস্থায় রাখার চেয়ে ক্যাশ না থাকাই ভালো */
    fun get(namespace: String, key: String): ByteArray? {
        val k = dataKey ?: return null
        val file = fileFor(namespace, key)
        if (!file.exists()) return null
        if (touchOnRead) file.setLastModified(System.currentTimeMillis())
        return runCatching { decrypt(k, file.readBytes()) }.getOrElse {
            file.delete() // খারাপ/অন্য কী-তে লেখা ফাইল: মুছে দিলে আবার বানানো হবে
            null
        }
    }

    /** ক্যাশে মানটি আছে কি (ডিক্রিপ্ট না করে, শুধু ফাইল আছে কি না দেখে) */
    fun has(namespace: String, key: String): Boolean = dataKey != null && fileFor(namespace, key).exists()

    fun put(namespace: String, key: String, data: ByteArray) {
        val k = dataKey ?: return
        runCatching {
            val file = fileFor(namespace, key)
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(encrypt(k, data))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        // মাঝেমধ্যে মোট আকার দেখে পুরোনোগুলো মুছে ফেলা
        if (writes.incrementAndGet() % 64 == 0) trim()
    }

    fun remove(namespace: String, key: String) {
        runCatching { fileFor(namespace, key).delete() }
    }

    /** ক্যাশের মোট আকার (বাইট) */
    fun sizeBytes(): Long = runCatching { root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    /** সব ক্যাশ মুছে ফেলে (থাম্বনেল ও তালিকা আবার বানাতে হবে) */
    fun clear() {
        runCatching { root.deleteRecursively() }
        root.mkdirs()
    }

    private fun fileFor(namespace: String, key: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$namespace\u0000$key".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(root, namespace).apply { mkdirs() }, hash)
    }

    private fun trim() {
        runCatching {
            val files = root.walkTopDown().filter { it.isFile }.toList()
            var total = files.sumOf { it.length() }
            if (total <= maxBytes) return
            for (f in files.sortedBy { it.lastModified() }) {
                total -= f.length()
                f.delete()
                if (total <= maxBytes * 3 / 4) break
            }
        }
    }

    // ---------------- এনক্রিপশন ----------------

    private fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }

    // ---------------- Android Keystore ----------------

    private fun loadOrCreateDataKey(): SecretKey {
        if (keyFile.exists()) {
            val raw = unwrap(keyFile.readBytes())
            return SecretKeySpec(raw, "AES")
        }
        val raw = ByteArray(32).also { random.nextBytes(it) }
        keyFile.writeBytes(wrap(raw))
        return SecretKeySpec(raw, "AES")
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }

    companion object {
        private const val KEY_ALIAS = "drivemount_vaultcache_key"

        /** ক্যাশের মোট সীমা (ডিফল্ট) */
        const val DEFAULT_MAX_BYTES = 256L * 1024 * 1024
    }
}

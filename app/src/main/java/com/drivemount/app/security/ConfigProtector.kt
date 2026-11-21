package com.drivemount.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RcloneEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * সংরক্ষিত লগইন (rclone.conf) এনক্রিপ্ট করে রাখে।
 * কনফিগের চাবি র‍্যান্ডম; সেটি Android Keystore-এর (নিরাপদ হার্ডওয়্যারে থাকা) কী দিয়ে মুড়ে ফোনে রাখা হয়।
 * ফলে ফাইলটি অন্য কোথাও কপি করলে খোলা যায় না।
 */
class ConfigProtector(
    private val context: Context,
    private val configFile: File,
    private val api: RcloneApi,
) {
    private val keyFile = File(context.filesDir, "cfgkey.bin")

    fun isProtected(): Boolean = RcloneEngine.isConfigFileEncrypted(configFile.absolutePath)

    /** সংরক্ষিত চাবি; না থাকলে বা খোলা না গেলে null */
    fun loadKey(): String? {
        if (!keyFile.exists()) return null
        return runCatching { String(unwrap(keyFile.readBytes()), Charsets.UTF_8) }.getOrNull()
    }

    /** এনক্রিপশন চালু না থাকলে চালু করে। ব্যর্থ হলে আগের ফাইল ফেরত রাখে। */
    suspend fun ensureEnabled(): Result<Unit> = runCatching {
        if (isProtected() && RcloneEngine.configPassword != null) return@runCatching
        withContext(Dispatchers.IO) {
            // ইঞ্জিন চালু করে এখনকার remote-এর তালিকা মনে রাখা (পরে মিলিয়ে দেখার জন্য)
            val before = api.dumpRemotes().keys.toSet()

            val raw = ByteArray(32)
            SecureRandom().nextBytes(raw)
            val key = Base64.encodeToString(raw, Base64.NO_WRAP or Base64.NO_PADDING)
            // চাবি আগে নিরাপদে রাখা, তারপর ফাইল বদলানো
            keyFile.writeBytes(wrap(key.toByteArray(Charsets.UTF_8)))

            val backup = File(configFile.path + ".bak")
            val hadFile = configFile.exists()
            if (hadFile) configFile.copyTo(backup, overwrite = true)

            try {
                api.unlockConfig(key)
                api.touchConfig()
                check(isProtected()) { "The saved logins could not be encrypted" }
                val after = api.dumpRemotes().keys.toSet()
                check(after == before) { "Verification failed after encrypting the saved logins" }
            } catch (t: Throwable) {
                if (hadFile && backup.exists()) backup.copyTo(configFile, overwrite = true)
                throw t
            }
            if (hadFile) backup.delete()
            RcloneEngine.configPassword = key
        }
    }

    // ---------------- Android Keystore ----------------

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

    private companion object {
        const val KEY_ALIAS = "drivemount_config_key"
    }
}

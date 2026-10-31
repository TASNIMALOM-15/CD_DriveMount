package com.drivemount.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticator (TOTP) গেট: ব্যাকআপ export/import খুলতে ৬ সংখ্যার কোড চায়।
 * Google Authenticator, Aegis, 2FAS ইত্যাদি যেকোনো অ্যাপের সাথে চলে (RFC 6238: SHA-1, ৩০ সেকেন্ড, ৬ অঙ্ক)।
 *
 * secret ফোনে থাকে Android Keystore-এর চাবিতে মুড়ে, তাই ফাইল কপি করে নেওয়া যায় না।
 * মনে রাখা দরকার: এটি অ্যাপ হাতে পাওয়া লোকের বিরুদ্ধে ঢাল। ব্যাকআপ ফাইল চুরির বিরুদ্ধে সুরক্ষা আসে
 * দুই পাসওয়ার্ড ও Backup Key থেকে (BackupCrypto দেখুন)।
 */
class TotpGate(context: Context) {

    enum class Result { OK, WRONG, LOCKED }

    private val prefs = context.applicationContext.getSharedPreferences("totp_gate", Context.MODE_PRIVATE)

    private var failures = 0
    private var lockedUntil = 0L

    fun isEnabled(): Boolean = prefs.contains(KEY_SECRET)

    /** নতুন গোপন (setup-এর সময় দেখানোর জন্য); যাচাই না হওয়া পর্যন্ত সংরক্ষিত হয় না */
    fun newSecret(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        return base32Encode(bytes)
    }

    /** Authenticator অ্যাপ খোলার লিংক (otpauth://) */
    fun otpauthUri(secret: String): String =
        "otpauth://totp/DriveMount:Backup?secret=$secret&issuer=DriveMount&algorithm=SHA1&digits=6&period=30"

    /** setup শেষ করা: কোড মিললে secret সংরক্ষণ ও গেট চালু */
    fun enable(secret: String, code: String): Boolean {
        val key = base32Decode(secret) ?: return false
        val step = matchingStep(key, code) ?: return false
        prefs.edit()
            .putString(KEY_SECRET, Base64.encodeToString(wrap(key), Base64.NO_WRAP))
            .putLong(KEY_LAST_STEP, step)
            .apply()
        return true
    }

    /** কোড যাচাই। একই কোড দ্বিতীয়বার চলে না; ৫ বার ভুল হলে ৩০ সেকেন্ড বন্ধ */
    fun verify(code: String): Result {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return Result.LOCKED
        val stored = prefs.getString(KEY_SECRET, null) ?: return Result.OK
        val key = runCatching { unwrap(Base64.decode(stored, Base64.NO_WRAP)) }.getOrNull() ?: return Result.WRONG
        val step = matchingStep(key, code)
        val last = prefs.getLong(KEY_LAST_STEP, -1L)
        if (step == null || step <= last) {
            failures++
            if (failures >= 5) {
                failures = 0
                lockedUntil = now + 30_000
                return Result.LOCKED
            }
            return Result.WRONG
        }
        failures = 0
        prefs.edit().putLong(KEY_LAST_STEP, step).apply()
        return Result.OK
    }

    fun disable() {
        prefs.edit().remove(KEY_SECRET).remove(KEY_LAST_STEP).apply()
    }

    // ---------------- TOTP ----------------

    /** এই কোডটি এখনকার (±১ ধাপ) কোন ধাপের সাথে মেলে; না মিললে null */
    private fun matchingStep(key: ByteArray, code: String): Long? {
        val clean = code.filter { it.isDigit() }
        if (clean.length != 6) return null
        val current = System.currentTimeMillis() / 1000 / PERIOD
        for (offset in longArrayOf(0, -1, 1)) {
            val step = current + offset
            if (totp(key, step) == clean) return step
        }
        return null
    }

    private fun totp(key: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    // ---------------- base32 ----------------

    private fun base32Encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun base32Decode(text: String): ByteArray? {
        val clean = text.uppercase().filter { it != ' ' && it != '-' && it != '=' }
        if (clean.isEmpty() || clean.any { it !in B32 }) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            buffer = (buffer shl 5) or B32.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    // ---------------- Android Keystore ----------------

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        const val PERIOD = 30L
        const val KEY_SECRET = "secret"
        const val KEY_LAST_STEP = "last_step"
        const val KEY_ALIAS = "drivemount_totp_key"
    }
}

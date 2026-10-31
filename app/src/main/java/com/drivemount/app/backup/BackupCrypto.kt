package com.drivemount.app.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ব্যাকআপ ফাইলের এনক্রিপশন।
 *
 * v2 ("DMBK2") — তিনটি জিনিস একসাথে না থাকলে চাবিই তৈরি হয় না:
 *   ১. প্রথম পাসওয়ার্ড   → PBKDF2 (৬ লক্ষ ধাপ) → K1
 *   ২. দ্বিতীয় পাসওয়ার্ড → PBKDF2 (৬ লক্ষ ধাপ, আলাদা salt) → K2
 *   ৩. Backup Key (২৫৬ বিটের র‍্যান্ডম, ফাইলে থাকে না)
 * চূড়ান্ত চাবি = HKDF-SHA256(K1 ‖ K2 ‖ BackupKey), তারপর AES-256-GCM।
 * ফাইলের গঠন: "DMBK2" + salt1(16) + salt2(16) + salt3(16) + iv(12) + এনক্রিপ্ট করা তথ্য।
 * Backup Key ২৫৬ বিট হওয়ায় পাসওয়ার্ড দুর্বল হলেও ফাইল চুরি করে অনুমান করে ভাঙা অসম্ভব।
 *
 * v1 ("DMBK1", একটি পাসওয়ার্ড) শুধু পুরোনো ফাইল খোলার জন্য রাখা আছে; নতুন ব্যাকআপ সবসময় v2।
 */
object BackupCrypto {

    class WrongPasswordException : Exception()
    class NotBackupException : Exception()

    private val MAGIC_V1 = "DMBK1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "DMBK2".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 600_000
    private const val HKDF_INFO = "DriveMount backup v2"
    const val MIN_PASSWORD_LENGTH = 8

    /** ফাইলের সংস্করণ: 1 (পুরোনো), 2 (নতুন), নইলে null */
    fun formatOf(blob: ByteArray): Int? = when {
        blob.startsWith(MAGIC_V2) -> 2
        blob.startsWith(MAGIC_V1) -> 1
        else -> null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    // ---------------- v2 ----------------

    fun encrypt(plain: ByteArray, password1: String, password2: String, backupKey: String): ByteArray {
        val random = SecureRandom()
        val salt1 = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val salt2 = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val salt3 = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val header = MAGIC_V2 + salt1 + salt2 + salt3
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, combinedKey(password1, password2, backupKey, salt1, salt2, salt3), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, password1: String, password2: String, backupKey: String): ByteArray {
        val headerLen = MAGIC_V2.size + 3 * SALT_LEN
        if (blob.size < headerLen + IV_LEN + 16 || !blob.startsWith(MAGIC_V2)) throw NotBackupException()
        val salt1 = blob.copyOfRange(MAGIC_V2.size, MAGIC_V2.size + SALT_LEN)
        val salt2 = blob.copyOfRange(MAGIC_V2.size + SALT_LEN, MAGIC_V2.size + 2 * SALT_LEN)
        val salt3 = blob.copyOfRange(MAGIC_V2.size + 2 * SALT_LEN, headerLen)
        val iv = blob.copyOfRange(headerLen, headerLen + IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, combinedKey(password1, password2, backupKey, salt1, salt2, salt3), GCMParameterSpec(128, iv))
        cipher.updateAAD(blob.copyOfRange(0, headerLen))
        return try {
            cipher.doFinal(blob, headerLen + IV_LEN, blob.size - headerLen - IV_LEN)
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        }
    }

    private fun combinedKey(
        password1: String,
        password2: String,
        backupKey: String,
        salt1: ByteArray,
        salt2: ByteArray,
        salt3: ByteArray,
    ): SecretKeySpec {
        val k1 = pbkdf2(password1, salt1)
        val k2 = pbkdf2(password2, salt2)
        // K1 ও K2 দুটোই ৩২ বাইট, তাই জোড়া লাগালে কোনো অস্পষ্টতা নেই
        val ikm = k1 + k2 + backupKey.toByteArray(Charsets.UTF_8)
        val prk = hmac(salt3, ikm)
        val okm = hmac(prk, HKDF_INFO.toByteArray(Charsets.UTF_8) + byteArrayOf(1))
        return SecretKeySpec(okm, "AES")
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ---------------- v1 (শুধু পুরোনো ফাইল খুলতে) ----------------

    fun decryptLegacy(blob: ByteArray, password: String): ByteArray {
        val headerLen = MAGIC_V1.size + SALT_LEN + IV_LEN
        if (blob.size < headerLen + 16 || !blob.startsWith(MAGIC_V1)) throw NotBackupException()
        val salt = blob.copyOfRange(MAGIC_V1.size, MAGIC_V1.size + SALT_LEN)
        val iv = blob.copyOfRange(MAGIC_V1.size + SALT_LEN, headerLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(pbkdf2(password, salt), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC_V1)
        return try {
            cipher.doFinal(blob, headerLen, blob.size - headerLen)
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        }
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

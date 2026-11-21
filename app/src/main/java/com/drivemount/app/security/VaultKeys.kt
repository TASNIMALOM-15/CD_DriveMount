package com.drivemount.app.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * এনক্রিপ্টেড ভল্টের চাবি।
 * একটি "গোপন" (recovery code বা ব্যবহারকারীর নিজের পাসওয়ার্ড) থেকে rclone crypt-এর password ও salt বানানো হয়।
 * একই গোপন থেকে সবসময় একই চাবি আসে, তাই শুধু সেটি থাকলেই ভল্ট যেকোনো ফোনে খোলা যায়।
 */
object VaultKeys {

    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val ITERATIONS = 210_000
    private const val CONTEXT = "DriveMount vault v1"
    const val RECOVERY_LENGTH = 52 // 256 bit, base32
    const val MIN_PASSWORD_LENGTH = 12

    /** ২৫৬ বিটের র‍্যান্ডম recovery code, ৪ অক্ষরের গ্রুপে (ABCD-EFGH-...) */
    fun generateRecoveryCode(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base32(bytes).chunked(4).joinToString("-")
    }

    /** ড্যাশ/স্পেস সরিয়ে বড় হাতের করে; recovery code সঠিক আকারের না হলে null */
    fun normalizeRecoveryCode(input: String): String? {
        val clean = input.uppercase().filter { it != '-' && !it.isWhitespace() }
        if (clean.length != RECOVERY_LENGTH || clean.any { it !in B32 }) return null
        return clean
    }

    /** গোপন থেকে rclone crypt-এর (password, password2/salt) বানায়। সময় নেয় (~১ সেকেন্ড), তাই ব্যাকগ্রাউন্ডে চালাতে হবে। */
    fun derive(secret: String): Pair<String, String> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret.toCharArray(), CONTEXT.toByteArray(Charsets.UTF_8), ITERATIONS, 1024)
        val bytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val flags = Base64.NO_WRAP or Base64.NO_PADDING
        return Base64.encodeToString(bytes.copyOfRange(0, 64), flags) to
            Base64.encodeToString(bytes.copyOfRange(64, 128), flags)
    }

    private fun base32(bytes: ByteArray): String {
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
}

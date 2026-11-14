package com.drivemount.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Filen লগইন: email + password (+2FA) থেকে API key বের করে।
 * rclone-এর filen backend-এ api_key লাগে (সাধারণত Filen CLI থেকে নিতে হয়);
 * অ্যাপ নিজেই সেটা এনে দেয়, তাই ব্যবহারকারীকে CLI চালাতে হয় না।
 */
object FilenAuth {
    private const val GATEWAY = "https://gateway.filen.io"

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun sha512Hex(s: String) = hex(MessageDigest.getInstance("SHA-512").digest(s.toByteArray()))

    private fun post(path: String, body: JSONObject): JSONObject {
        val c = URL(GATEWAY + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (c.responseCode in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    /** সফল হলে API key ফেরত দেয়; ব্যর্থ হলে Exception ছোড়ে */
    fun login(email: String, password: String, twoFa: String): String {
        val info = post("/v3/auth/info", JSONObject().put("email", email))
        if (!info.optBoolean("status")) throw IllegalStateException(info.optString("message", "Filen login failed"))
        val data = info.getJSONObject("data")
        val version = data.optInt("authVersion", 2)
        val derivedPassword = when (version) {
            2 -> {
                val salt = data.getString("salt")
                val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 200000, 512)
                val derived = hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded)
                sha512Hex(derived.substring(derived.length / 2))
            }
            else -> throw IllegalStateException("This Filen account uses an older auth version ($version), which is not supported")
        }
        val res = post(
            "/v3/login",
            JSONObject()
                .put("email", email)
                .put("password", derivedPassword)
                .put("twoFactorCode", twoFa.ifBlank { "XXXXXX" })
                .put("authVersion", version),
        )
        if (!res.optBoolean("status")) {
            throw IllegalStateException(res.optString("message", "Filen login failed"))
        }
        val key = res.getJSONObject("data").optString("apiKey")
        if (key.isBlank()) throw IllegalStateException("Filen API key was not returned")
        return key
    }
}

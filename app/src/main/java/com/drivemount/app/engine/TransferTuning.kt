package com.drivemount.app.engine

import android.content.Context
import org.json.JSONObject

/**
 * ট্রান্সফারের গতির সেটিং (rclone-এর প্রতি-কাজের `_config`)।
 * বড় ফাইল (৩২MB+) একাধিক অংশে একসঙ্গে নামে/ওঠে (multi-thread streams); প্রোভাইডার ভেদে নিরাপদ সংখ্যা।
 * মেমরি সীমিত রাখতে প্রতি-স্ট্রিম বাফার ছোট (৪MB)। কাজ শুরুতেই এই সেটিং নিয়ে ব্যর্থ হলে
 * (RcloneApi.startJob) সেটিং ছাড়া আবার চেষ্টা হয়, তাই এটি কখনো ট্রান্সফার আটকে দেয় না।
 */
object TransferTuning {

    private const val PREFS = "transfer_ui"
    private const val KEY_FAST = "fast"

    fun isFast(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FAST, true)

    fun setFast(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FAST, value).apply()
    }

    /** প্রোভাইডার ভেদে প্রতি-ফাইলে সমান্তরাল স্ট্রিম (বেশি দিলে কিছু প্রোভাইডার থ্রটল করে) */
    private fun streamsFor(type: String?): Int = when (type) {
        "drive", "s3", "b2", "gcs", "azureblob", "pcloud", "box" -> 6
        "ftp" -> 2
        else -> 4 // onedrive, dropbox, mega, webdav, sftp, crypt (ভেতরের প্রোভাইডার অজানা) ইত্যাদি
    }

    /**
     * নেট কেটে গেলে rclone নিজে আবার চেষ্টা করে: নিচু স্তরের (প্রতি অংশ/chunk) ১০ বার ও পুরো ফাইলের ৩ বার।
     * Drive/OneDrive-এর মতো chunked-resumable ব্যাকএন্ডে সেশন চলতে থাকে, তাই ছোট বিচ্ছিন্নতায় আপলোড শুরু থেকে হয় না।
     */
    fun baseConfig(): JSONObject = JSONObject()
        .put("LowLevelRetries", 10)
        .put("Retries", 3)

    fun config(type: String?): JSONObject = baseConfig()
        .put("MultiThreadStreams", streamsFor(type))
        .put("MultiThreadCutoff", 32L * 1024 * 1024)
        .put("BufferSize", 4L * 1024 * 1024)
}

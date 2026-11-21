package com.drivemount.app.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/**
 * ফোনের স্টোরেজের পথ। পথ কখনো স্থির ধরা হয় না, কারণ ব্যবহারকারীভেদে বদলায়:
 * ব্যক্তিগত প্রোফাইলে /storage/emulated/0, কাজের (work) প্রোফাইলে সাধারণত /storage/emulated/10।
 */
object LocalPaths {

    data class Place(val label: String, val relativePath: String)

    /** চলমান ব্যবহারকারীর অভ্যন্তরীণ স্টোরেজ (যেমন /storage/emulated/10) */
    fun internalStorage(): String = Environment.getExternalStorageDirectory().absolutePath

    /** rclone-এর "name:/" ভিত্তিক আপেক্ষিক রূপ (শুরুর "/" ছাড়া) */
    fun internalRelative(): String = internalStorage().trim('/')

    /** ডাউনলোডের ফোল্ডার */
    fun downloadDir(): String = File(internalStorage(), "Download/DriveMount").absolutePath

    /**
     * ব্যবহারকারীর লেখা পূর্ণ পথকে rclone-এর আপেক্ষিক পথে বদলায়।
     * symlink মিলিয়ে নেয়, যেমন /sdcard -> /storage/emulated/N, /data/data -> /data/user/0।
     * পথ "/" দিয়ে শুরু না হলে null।
     */
    fun resolveToRelative(input: String): String? {
        val file = File(input.trim())
        if (!file.isAbsolute) return null
        val resolved = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return resolved.trim('/')
    }

    /** SD কার্ড ও USB ড্রাইভ (অভ্যন্তরীণ স্টোরেজ বাদে) */
    fun removableVolumes(context: Context): List<Place> {
        val result = mutableListOf<Place>()
        if (Build.VERSION.SDK_INT >= 30) {
            val manager = context.getSystemService(StorageManager::class.java)
            manager?.storageVolumes?.forEach { volume ->
                val dir = volume.directory
                if (!volume.isPrimary && dir != null) {
                    result += Place(volume.getDescription(context), dir.absolutePath.trim('/'))
                }
            }
        } else {
            context.getExternalFilesDirs(null).drop(1).forEach { dir ->
                val root = dir?.absolutePath?.substringBefore("/Android/data")
                if (!root.isNullOrEmpty()) result += Place(File(root).name, root.trim('/'))
            }
        }
        return result
    }
}

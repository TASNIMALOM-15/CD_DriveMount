package com.drivemount.app.ui.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * একাধিক ফাইল বাছাই (সিস্টেমের picker)। EXTRA_LOCAL_ONLY দেওয়া হয়, যাতে picker-এর তালিকায়
 * DriveMount-এর নিজের ক্লাউড-অ্যাকাউন্টগুলো না আসে (শুধু ফোনের ফাইল)।
 */
class PickManyFiles : ActivityResultContract<Array<String>, List<Uri>>() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            .setType("*/*")

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val uris = mutableListOf<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        }
        intent.data?.let { if (it !in uris) uris += it }
        return uris
    }
}

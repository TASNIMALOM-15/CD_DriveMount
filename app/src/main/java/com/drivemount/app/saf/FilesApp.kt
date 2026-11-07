package com.drivemount.app.saf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract

/** সিস্টেমের Files অ্যাপে এই অ্যাকাউন্টের ফোল্ডার খোলে; Files অ্যাপ না থাকলে false */
fun openInFilesApp(context: Context, accountId: Long): Boolean = try {
    val uri = DocumentsContract.buildRootUri("${context.packageName}.documents", "acc$accountId")
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, DocumentsContract.Root.MIME_TYPE_ITEM)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

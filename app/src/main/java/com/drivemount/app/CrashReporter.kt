package com.drivemount.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * অ্যাপ ক্র্যাশ করলে কারণটি ফাইলে লিখে রাখে; পরের বার অ্যাপ খুললে Home-এ দেখানো হয়,
 * যাতে কম্পিউটার ছাড়াই ক্র্যাশের বিবরণ কপি করে পাঠানো যায়।
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter()
                throwable.printStackTrace(PrintWriter(trace))
                File(appContext.filesDir, FILE_NAME).writeText(
                    "DriveMount crash report\n" +
                        "Time: ${Date()}\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "Thread: ${thread.name}\n\n" +
                        trace.toString(),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** আগের ক্র্যাশের বিবরণ (থাকলে) একবার পড়ে মুছে দেয় */
    fun readAndClear(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text
    }
}

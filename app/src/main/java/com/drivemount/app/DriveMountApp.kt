package com.drivemount.app

import android.app.Application
import android.provider.DocumentsContract
import android.system.Os
import androidx.room.Room
import com.drivemount.app.data.AccountRepository
import com.drivemount.app.data.AppDatabase
import com.drivemount.app.data.FileClipboard
import com.drivemount.app.data.FileRepository
import com.drivemount.app.data.transfer.SyncRepository
import com.drivemount.app.data.transfer.TransferDatabase
import com.drivemount.app.data.transfer.TransferRepository
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.ui.theme.ThemeRepository
import com.drivemount.app.work.TransferNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DriveMountApp : Application() {

    lateinit var themeRepository: ThemeRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var accountRepository: AccountRepository
        private set

    lateinit var fileRepository: FileRepository
        private set

    lateinit var transferRepository: TransferRepository
        private set

    lateinit var syncRepository: SyncRepository
        private set

    lateinit var rcloneApi: RcloneApi
        private set

    /** কপি/কাটা ফাইলের তালিকা (পর্দা বদলালেও থাকে) */
    val clipboard = FileClipboard()

    override fun onCreate() {
        super.onCreate()

        val configFile = File(filesDir, "rclone.conf")

        // rclone (Go) চালু হওয়ার সময়ই environment পড়ে নেয়।
        // তাই Gomobile class প্রথমবার ব্যবহারের আগেই এগুলো সেট করতে হবে।
        Os.setenv("HOME", filesDir.absolutePath, true)
        Os.setenv("XDG_CONFIG_HOME", filesDir.absolutePath, true)
        Os.setenv("XDG_CACHE_HOME", cacheDir.absolutePath, true)
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        Os.setenv("RCLONE_CONFIG", configFile.absolutePath, true)
        // Android-এ /etc/resolv.conf নেই; Go-র নিজস্ব DNS না নিয়ে সিস্টেমের resolver ব্যবহার করা হয়
        Os.setenv("GODEBUG", "netdns=cgo", true)

        themeRepository = ThemeRepository(this)

        database = Room.databaseBuilder(this, AppDatabase::class.java, "drivemount.db").build()
        val api = RcloneApi(configFile.absolutePath)
        rcloneApi = api
        accountRepository = AccountRepository(dao = database.accountDao(), api = api)
        fileRepository = FileRepository(api)

        // অ্যাকাউন্ট বদলালে সিস্টেমের Files অ্যাপকে জানানো হয়, যাতে তার তালিকা হালনাগাদ হয়
        accountRepository.onChanged = {
            contentResolver.notifyChange(DocumentsContract.buildRootsUri("$packageName.documents"), null)
        }

        val transferDb = Room.databaseBuilder(this, TransferDatabase::class.java, "transfers.db")
            .fallbackToDestructiveMigration() // শুধু ট্রান্সফারের ইতিহাস; অ্যাকাউন্টের তালিকা আলাদা ডাটাবেসে
            .build()
        transferRepository = TransferRepository(this, transferDb.transferDao(), transferDb.syncProfileDao(), api)
        syncRepository = SyncRepository(this, transferDb.syncProfileDao(), accountRepository, transferRepository)
        TransferNotifications.ensureChannel(this)
        // অ্যাপ বন্ধ হওয়ার সময় অসমাপ্ত কাজ থাকলে আবার চালু করা
        CoroutineScope(Dispatchers.IO).launch { transferRepository.resumeIfNeeded() }
    }
}

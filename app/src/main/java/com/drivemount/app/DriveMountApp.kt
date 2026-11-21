package com.drivemount.app

import android.app.Application
import android.provider.DocumentsContract
import android.system.Os
import androidx.room.Room
import com.drivemount.app.data.AccountRepository
import com.drivemount.app.data.AppDatabase
import com.drivemount.app.data.FileClipboard
import com.drivemount.app.data.FileRepository
import com.drivemount.app.data.index.IndexDatabase
import com.drivemount.app.data.meta.PhotoPreviews
import com.drivemount.app.data.offline.OfflineDatabase
import com.drivemount.app.data.offline.OfflineStore
import com.drivemount.app.data.meta.SidecarManager
import com.drivemount.app.data.transfer.SyncRepository
import com.drivemount.app.data.transfer.TransferDatabase
import com.drivemount.app.data.transfer.TransferRepository
import com.drivemount.app.backup.BackupManager
import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RcloneEngine
import com.drivemount.app.security.AppLock
import com.drivemount.app.security.ConfigProtector
import com.drivemount.app.security.SecurityRepository
import com.drivemount.app.security.VaultCache
import com.drivemount.app.ui.player.PlaybackController
import com.drivemount.app.ui.theme.ThemeRepository
import com.drivemount.app.work.TransferNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** নোটিফিকেশন বা অন্য জায়গা থেকে "এই ফোল্ডারে গিয়ে এই ফাইল হাইলাইট করো" অনুরোধ */
data class OpenTarget(val accountId: Long, val path: String, val highlight: String)

class DriveMountApp : Application() {

    /** নেভিগেশন এটি দেখে সেই ফোল্ডারে খোলে, তারপর খালি করে দেয় */
    val openTarget = kotlinx.coroutines.flow.MutableStateFlow<OpenTarget?>(null)

    lateinit var themeRepository: ThemeRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var accountRepository: AccountRepository
        private set

    lateinit var fileRepository: FileRepository
        private set

    /** সাইডকার মেটাডেটা (এখন APK আইকন): আপলোডের সময় বানানো, ফোল্ডার-প্যাকে ক্লাউডে জমা */
    lateinit var sidecar: SidecarManager

    /** ছবির মাঝারি প্রিভিউ (ফুল-স্ক্রিন, অফলাইন) */
    lateinit var photoPreviews: PhotoPreviews
    lateinit var seekSheets: com.drivemount.app.data.meta.SeekSheets
    lateinit var zipIndex: com.drivemount.app.data.meta.ZipIndexStore
    lateinit var mediaMarks: com.drivemount.app.data.library.MediaMarks
    lateinit var fabSettings: com.drivemount.app.data.FabSettings
    /** "এই ট্যাবে যাও" অনুরোধ (বাঁ-সোয়াইপ ও ফ্লোটিং বাটনের ট্যাব-বাটন থেকে) */
    val tabRequest = kotlinx.coroutines.flow.MutableStateFlow(com.drivemount.app.ui.components.TabRequest(com.drivemount.app.ui.components.TabIds.HOME, 0))
    lateinit var cloudOrder: com.drivemount.app.data.CloudOrderSettings
    lateinit var iconSettings: com.drivemount.app.data.IconSettings
    lateinit var cloudGlow: com.drivemount.app.data.CloudGlowSettings
    lateinit var fileDates: com.drivemount.app.data.meta.FileDates

    /** "অফলাইনের জন্য রাখো": ফোনে পুরো নামানো ফাইল */
    /** Home-এর "Recent" তালিকা */
    val recent by lazy { com.drivemount.app.data.RecentStore(this) }

    /** "Galaxy-তে এনক্রিপ্টেড ভল্ট বানাও" অনুরোধ (অ্যাকাউন্ট id); AppNavHost ধরে ভল্ট-পাতা খোলে */
    val vaultRequest = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    /** ভল্ট-পাতা খুললে আগে থেকে বাছা থাকবে যে অ্যাকাউন্ট (একবার পড়লেই মুছে যায়) */
    @Volatile
    var vaultPreset: Long? = null

    /** Galaxy Drive: একাধিক ক্লাউডে ব্যাকআপ, দেখতে একটি ক্লাউড */
    lateinit var galaxy: com.drivemount.app.data.galaxy.GalaxyRepository
        private set

    lateinit var offlineStore: OfflineStore
        private set

    /** এনক্রিপ্টেড ভল্টের লোকাল ক্যাশ (তালিকা ও থাম্বনেল): ডিস্কে সবসময় এনক্রিপ্টেড */
    lateinit var vaultCache: VaultCache
        private set

    /** এনক্রিপ্টেড ভল্টের আসল ছবির ক্যাশ (ডিস্কে শুধু এনক্রিপ্টেড); সীমা = Settings-এর অফলাইন সীমা, সবচেয়ে কম-ব্যবহৃত আগে যায় */
    val viewerVault: VaultCache by lazy { VaultCache(this, "vault_orig", offlineStore.limitBytes, touchOnRead = true) }

    lateinit var transferRepository: TransferRepository
        private set

    lateinit var syncRepository: SyncRepository
        private set

    lateinit var rcloneApi: RcloneApi
        private set

    lateinit var configProtector: ConfigProtector
        private set

    lateinit var securityRepository: SecurityRepository
        private set

    lateinit var backupManager: BackupManager
        private set

    lateinit var appLock: AppLock
        private set

    /** অ্যাপ-জুড়ে একটাই প্লেয়ার (বড়/মিনি/গান): পর্দা বদলালেও চলতে থাকে */
    lateinit var playback: PlaybackController
        private set

    /** কপি/কাটা ফাইলের তালিকা (পর্দা বদলালেও থাকে) */
    val clipboard = FileClipboard()

    /** Application.onCreate শেষ হলে true (ContentProvider এর আগেই ডাক পেতে পারে) */
    @Volatile
    var ready = false
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)

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
        fabSettings = com.drivemount.app.data.FabSettings(this)
        iconSettings = com.drivemount.app.data.IconSettings(this)
        cloudOrder = com.drivemount.app.data.CloudOrderSettings(this)
        cloudGlow = com.drivemount.app.data.CloudGlowSettings(this)
        playback = PlaybackController(this)

        database = Room.databaseBuilder(this, AppDatabase::class.java, "drivemount.db").build()
        val api = RcloneApi(configFile.absolutePath)
        rcloneApi = api

        // সংরক্ষিত লগইন এনক্রিপ্টেড থাকলে তার চাবি ইঞ্জিন চালুর আগেই বসাতে হয়
        configProtector = ConfigProtector(this, configFile, api)
        RcloneEngine.configPassword = configProtector.loadKey()

        securityRepository = SecurityRepository(this)
        appLock = AppLock(securityRepository)
        accountRepository = AccountRepository(dao = database.accountDao(), api = api)
        vaultCache = VaultCache(this)
        // ফোল্ডার-তালিকার ক্যাশ ডাটাবেস: শুধু ক্যাশ, তাই নষ্ট/বদলালে মুছে আবার তৈরি হয়
        val indexDb = Room.databaseBuilder(this, IndexDatabase::class.java, "index.db")
            .fallbackToDestructiveMigration()
            .build()
        // প্রিভিউ আলাদা ও বড় (১GB) এনক্রিপ্টেড ক্যাশে; কম-ব্যবহৃতগুলো আগে বাদ যায়
        photoPreviews = PhotoPreviews(this, api, VaultCache(this, "preview_cache", 1024L * 1024 * 1024, touchOnRead = true))
        seekSheets = com.drivemount.app.data.meta.SeekSheets(this, api, VaultCache(this, "seek_cache", 256L * 1024 * 1024, touchOnRead = true))
        fileDates = com.drivemount.app.data.meta.FileDates(this, api, VaultCache(this, "dates_cache", 64L * 1024 * 1024, touchOnRead = true))
        mediaMarks = com.drivemount.app.data.library.MediaMarks(this)
        zipIndex = com.drivemount.app.data.meta.ZipIndexStore(this, api, VaultCache(this, "zidx_cache", 64L * 1024 * 1024, touchOnRead = true))
        sidecar = SidecarManager(this, api, vaultCache, photoPreviews, seekSheets, fileDates)
        fileRepository = FileRepository(api, vaultCache, indexDb.indexDao(), sidecar)
        backupManager = BackupManager(api, accountRepository, configProtector)

        // অ্যাকাউন্ট বদলালে সিস্টেমের Files অ্যাপকে জানানো হয়, যাতে তার তালিকা হালনাগাদ হয়
        accountRepository.onChanged = {
            contentResolver.notifyChange(DocumentsContract.buildRootsUri("$packageName.documents"), null)
        }

        val offlineDb = Room.databaseBuilder(this, OfflineDatabase::class.java, "offline.db")
            .fallbackToDestructiveMigration()
            .build()
        offlineStore = OfflineStore(this, offlineDb.offlineDao())

        val transferDb = Room.databaseBuilder(this, TransferDatabase::class.java, "transfers.db")
            .fallbackToDestructiveMigration() // শুধু ট্রান্সফারের ইতিহাস; অ্যাকাউন্টের তালিকা আলাদা ডাটাবেসে
            .build()
        transferRepository = TransferRepository(this, transferDb.transferDao(), transferDb.syncProfileDao(), api)
        galaxy = com.drivemount.app.data.galaxy.GalaxyRepository(this, database.accountDao(), api, accountRepository, transferRepository, fileRepository)
        accountRepository.galaxy = galaxy
        syncRepository = SyncRepository(this, transferDb.syncProfileDao(), accountRepository, transferRepository)
        TransferNotifications.ensureChannel(this)
        // ব্যাকগ্রাউন্ড মেটাডেটা সিঙ্ক: ৩০ মিনিট পরপর (নেট ও ব্যাটারি ঠিক থাকলে); অন্য ডিভাইস/প্রোভাইডারের যোগ-মোছা-নাম বদল ধরে
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.drivemount.app.work.LibrarySyncWorker.UNIQUE_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.drivemount.app.work.LibrarySyncWorker>(30, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build(),
        )
        // অ্যাপ বন্ধ হওয়ার সময় অসমাপ্ত কাজ থাকলে আবার চালু করা
        CoroutineScope(Dispatchers.IO).launch { transferRepository.resumeIfNeeded() }
        // সব ক্লাউডের সংযোগ আগে থেকেই গরম করে রাখা: প্রথম ক্লিকে দেরি হয় না
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { fileRepository.warmUp(accountRepository.getAll()) }
        }
        // পুরোনো/মুছে ফেলা অ্যাকাউন্টের ক্যাশ সাফ: অ্যাপ চালুর ভিড় কাটার পর, পেছনে
        CoroutineScope(Dispatchers.IO).launch {
            delay(30_000)
            runCatching { fileRepository.pruneIndex(accountRepository.getAll().map { it.id }) }
        }
        // Galaxy ট্র্যাশের ৩০ দিন পার হওয়া জিনিস মোছা: ভিড় কাটার পর, পেছনে, হালকা কাজ
        CoroutineScope(Dispatchers.IO).launch {
            delay(90_000)
            runCatching { galaxy.purgeExpired() }
        }
        ready = true
    }
}

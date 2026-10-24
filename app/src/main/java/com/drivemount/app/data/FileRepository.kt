package com.drivemount.app.data

import com.drivemount.app.engine.RcloneApi
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor

/** ফাইল ব্রাউজারের কাজগুলো: তালিকা, নতুন ফোল্ডার, মোছা, নাম বদল, কপি/সরানো */
class FileRepository(private val api: RcloneApi) {

    private fun fsOf(account: AccountEntity) = fsRootFor(account.remoteName, account.backendType)

    suspend fun list(account: AccountEntity, path: String): Result<List<RemoteEntry>> =
        runCatching { api.list(fsOf(account), path) }

    suspend fun mkdir(account: AccountEntity, path: String): Result<Unit> =
        runCatching { api.mkdir(fsOf(account), path) }

    suspend fun delete(account: AccountEntity, entry: RemoteEntry): Result<Unit> = runCatching {
        if (entry.isDir) api.purge(fsOf(account), entry.path) else api.deleteFile(fsOf(account), entry.path)
    }

    /** একই অ্যাকাউন্টের ভেতরে নতুন পথে সরানো */
    suspend fun rename(account: AccountEntity, entry: RemoteEntry, newPath: String): Result<Unit> =
        transfer(account, entry, account, newPath, move = true)

    /** src অ্যাকাউন্টের entry-কে dst অ্যাকাউন্টের dstPath-এ কপি বা সরানো */
    suspend fun transfer(
        src: AccountEntity,
        entry: RemoteEntry,
        dst: AccountEntity,
        dstPath: String,
        move: Boolean,
    ): Result<Unit> = runCatching {
        if (entry.isDir) {
            api.transferDir(fsOf(src) + entry.path, fsOf(dst) + dstPath, move)
        } else {
            api.transferFile(fsOf(src), entry.path, fsOf(dst), dstPath, move)
        }
    }
}

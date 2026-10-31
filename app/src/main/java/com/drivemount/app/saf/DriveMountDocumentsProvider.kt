package com.drivemount.app.saf

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.transfer.NewTransfer
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import com.drivemount.app.engine.joinPath
import com.drivemount.app.engine.parentPath
import com.drivemount.app.engine.uniqueName
import com.drivemount.app.ui.components.providerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * সিস্টেমের Files অ্যাপে DriveMount-এর অ্যাকাউন্টগুলো ফোল্ডারের মতো দেখায় (root ছাড়া "mount")।
 *
 * - দেখা: ফোল্ডারের তালিকা rclone থেকে আনা হয় (অল্প সময় মনে রাখা হয়)
 * - পড়া/খোলা: ফাইলটি আগে পুরোটা ফোনে নামানো হয় (অস্থায়ী cache), তারপর খোলা হয়
 * - লেখা/সংরক্ষণ: লেখা শেষ হলে ফাইলটি ট্রান্সফার লাইনে আপলোডের জন্য যায়
 * document id = "<অ্যাকাউন্ট id>:<পথ>"; অ্যাকাউন্টের root-এর পথ খালি।
 */
class DriveMountDocumentsProvider : DocumentsProvider() {

    private data class Pending(val name: String, val mime: String, val time: Long)

    private val listCache = ConcurrentHashMap<String, Pair<Long, List<RemoteEntry>>>()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    // ContentProvider, Application.onCreate-এর আগে তৈরি হয়; তাই এগুলো দরকারের সময় পড়া হয়
    private val app: DriveMountApp
        get() {
            val application = context?.applicationContext as? DriveMountApp
            if (application == null || !application.ready) {
                throw IllegalStateException("DriveMount is still starting")
            }
            return application
        }

    override fun onCreate(): Boolean = true

    // ---------------- সাহায্যকারী ----------------

    private fun docId(accountId: Long, path: String) = "$accountId:$path"

    private fun parse(documentId: String): Pair<Long, String> {
        val i = documentId.indexOf(':')
        val id = if (i > 0) documentId.substring(0, i).toLongOrNull() else null
        if (id == null) throw FileNotFoundException(documentId)
        return id to documentId.substring(i + 1)
    }

    private fun account(id: Long): AccountEntity =
        runBlocking { app.accountRepository.getById(id) } ?: throw FileNotFoundException("account $id")

    private fun fsOf(a: AccountEntity) = fsRootFor(a.remoteName, a.backendType)

    private fun listDir(account: AccountEntity, path: String, force: Boolean = false): List<RemoteEntry> {
        val key = docId(account.id, path)
        val now = SystemClock.elapsedRealtime()
        if (!force) {
            val cached = listCache[key]
            if (cached != null && now - cached.first < CACHE_MS) return cached.second
        }
        val list = try {
            runBlocking { app.rcloneApi.list(fsOf(account), path) }
        } catch (e: Exception) {
            throw FileNotFoundException(e.message ?: "cannot list folder")
        }
        listCache[key] = now to list
        return list
    }

    private fun invalidate(account: AccountEntity, dirPath: String) {
        listCache.remove(docId(account.id, dirPath))
    }

    private fun statEntry(account: AccountEntity, path: String): RemoteEntry? {
        val cached = listCache[docId(account.id, parentPath(path))]
        if (cached != null && SystemClock.elapsedRealtime() - cached.first < CACHE_MS) {
            val hit = cached.second.firstOrNull { it.path == path }
            if (hit != null) return hit
        }
        return try {
            runBlocking { app.rcloneApi.stat(fsOf(account), path) }
        } catch (e: Exception) {
            null
        }
    }

    private fun addRow(
        c: MatrixCursor,
        id: String,
        name: String,
        mime: String,
        size: Long?,
        modified: Long?,
        flags: Int,
    ) {
        c.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, name)
            .add(Document.COLUMN_SIZE, size)
            .add(Document.COLUMN_MIME_TYPE, mime)
            .add(Document.COLUMN_LAST_MODIFIED, modified)
            .add(Document.COLUMN_FLAGS, flags)
    }

    private fun addEntryRow(c: MatrixCursor, accountId: Long, e: RemoteEntry) {
        val flags = if (e.isDir) {
            Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        } else {
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        val mime = if (e.isDir) Document.MIME_TYPE_DIR else guessMime(e.name, e.mimeType)
        addRow(c, docId(accountId, e.path), e.name, mime, e.size, e.modTimeMillis, flags)
    }

    // ---------------- তালিকা ----------------

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val ctx = context ?: return cursor
        val accounts = try {
            runBlocking { app.accountRepository.getAll() }
        } catch (e: Exception) {
            emptyList()
        }
        // ফোনের নিজের স্টোরেজ Files অ্যাপে আগে থেকেই আছে, তাই Local অ্যাকাউন্ট বাদ
        accounts.filter { it.backendType != "local" }.forEach { a ->
            cursor.newRow()
                .add(Root.COLUMN_ROOT_ID, "acc${a.id}")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                .add(Root.COLUMN_TITLE, a.displayName)
                .add(Root.COLUMN_SUMMARY, providerName(ctx, a.backendType))
                .add(Root.COLUMN_DOCUMENT_ID, docId(a.id, ""))
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOC_PROJECTION)
        val (accountId, path) = parse(documentId)
        val account = account(accountId)

        if (path.isEmpty()) {
            addRow(cursor, documentId, account.displayName, Document.MIME_TYPE_DIR, null, null, Document.FLAG_DIR_SUPPORTS_CREATE)
            return cursor
        }
        val entry = statEntry(account, path)
        val waiting = pending[documentId]
        when {
            entry != null -> {
                pending.remove(documentId)
                addEntryRow(cursor, accountId, entry)
            }
            waiting != null -> addRow(
                cursor, documentId, waiting.name, waiting.mime, 0L, waiting.time,
                Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE,
            )
            else -> throw FileNotFoundException(documentId)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOC_PROJECTION)
        val (accountId, path) = parse(parentDocumentId)
        val account = account(accountId)
        listDir(account, path).forEach { addEntryRow(cursor, accountId, it) }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = runCatching { parse(parentDocumentId) }.getOrNull() ?: return false
        val child = runCatching { parse(documentId) }.getOrNull() ?: return false
        if (parent.first != child.first) return false
        return if (parent.second.isEmpty()) child.second.isNotEmpty() else child.second.startsWith(parent.second + "/")
    }

    // ---------------- পড়া ও লেখা ----------------

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val (accountId, path) = parse(documentId)
        val account = account(accountId)
        val writing = mode.contains('w') || mode.contains('a')
        if (!writing) {
            return ParcelFileDescriptor.open(ensureCached(account, path), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return openForWrite(account, path, mode)
    }

    /** ফাইলটি অস্থায়ীভাবে ফোনে নামায় (আগে নামানো ও অপরিবর্তিত থাকলে সেটিই ব্যবহার করে) */
    private fun ensureCached(account: AccountEntity, path: String): File {
        val ctx = context ?: throw FileNotFoundException()
        val entry = statEntry(account, path) ?: throw FileNotFoundException(path)
        if (entry.isDir) throw FileNotFoundException("$path is a folder")

        val dir = File(ctx.cacheDir, "saf/${account.id}/${Integer.toHexString(path.hashCode())}").apply { mkdirs() }
        val file = File(dir, entry.name)
        val size = entry.size
        val remoteTime = entry.modTimeMillis
        val fresh = file.exists() && size != null && file.length() == size &&
            (remoteTime == null || kotlin.math.abs(file.lastModified() - remoteTime) < 2_000)

        if (!fresh) {
            file.delete()
            try {
                runBlocking {
                    app.rcloneApi.transferFile(fsOf(account), path, dir.absolutePath, entry.name, move = false)
                }
            } catch (e: Exception) {
                throw FileNotFoundException(e.message ?: "download failed")
            }
        }
        if (!file.exists()) throw FileNotFoundException("download failed")
        return file
    }

    /** অস্থায়ী ফাইলে লেখা হয়; বন্ধ হলে আপলোডের জন্য ট্রান্সফার লাইনে যায় */
    private fun openForWrite(account: AccountEntity, path: String, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val name = path.substringAfterLast('/')
        val dir = File(ctx.cacheDir, "saf-up/${UUID.randomUUID()}").apply { mkdirs() }
        val tmp = File(dir, name)

        // "rw"/"wa": আগের বিষয়বস্তু রেখে সম্পাদনা
        if ((mode.contains('r') || mode.contains('a')) && statEntry(account, path) != null) {
            ensureCached(account, path).copyTo(tmp, overwrite = true)
        }

        return try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.parseMode(mode), handler) { error ->
                if (error == null) queueUpload(account, path, dir, name) else dir.deleteRecursively()
            }
        } catch (e: IOException) {
            throw FileNotFoundException(e.message ?: "cannot open for writing")
        }
    }

    private fun queueUpload(account: AccountEntity, path: String, dir: File, name: String) {
        val transfers = app.transferRepository
        val dstFs = fsOf(account)
        CoroutineScope(Dispatchers.IO).launch {
            transfers.enqueue(
                listOf(
                    NewTransfer(
                        name = name,
                        isDir = false,
                        move = true, // আপলোড শেষে অস্থায়ী ফাইল মুছে যায়
                        srcFs = dir.absolutePath,
                        srcPath = name,
                        dstFs = dstFs,
                        dstPath = path,
                    ),
                ),
            )
            invalidate(account, parentPath(path))
        }
    }

    // ---------------- তৈরি, মোছা, নাম বদল ----------------

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val (accountId, parent) = parse(parentDocumentId)
        val account = account(accountId)
        val existing = listDir(account, parent, force = true).map { it.name }.toSet()
        val name = uniqueName(displayName, existing)
        val path = joinPath(parent, name)
        val id = docId(accountId, path)

        if (mimeType == Document.MIME_TYPE_DIR) {
            try {
                runBlocking { app.rcloneApi.mkdir(fsOf(account), path) }
            } catch (e: Exception) {
                throw FileNotFoundException(e.message ?: "cannot create folder")
            }
        } else {
            // ফাইলের আসল অংশ পরে openDocument("w") দিয়ে আসে; তার আগে শুধু "অপেক্ষারত" হিসেবে মনে রাখা হয়
            pending[id] = Pending(name, mimeType, System.currentTimeMillis())
        }
        invalidate(account, parent)
        return id
    }

    override fun deleteDocument(documentId: String) {
        val (accountId, path) = parse(documentId)
        val account = account(accountId)
        pending.remove(documentId)
        val entry = statEntry(account, path) ?: return
        try {
            runBlocking {
                if (entry.isDir) app.rcloneApi.purge(fsOf(account), path) else app.rcloneApi.deleteFile(fsOf(account), path)
            }
        } catch (e: Exception) {
            throw FileNotFoundException(e.message ?: "cannot delete")
        }
        invalidate(account, parentPath(path))
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        val (accountId, path) = parse(documentId)
        val account = account(accountId)
        val entry = statEntry(account, path) ?: throw FileNotFoundException(documentId)
        val newPath = joinPath(parentPath(path), displayName)
        val fs = fsOf(account)
        try {
            runBlocking {
                if (entry.isDir) {
                    app.rcloneApi.transferDir(fs + path, fs + newPath, move = true)
                } else {
                    app.rcloneApi.transferFile(fs, path, fs, newPath, move = true)
                }
            }
        } catch (e: Exception) {
            throw FileNotFoundException(e.message ?: "cannot rename")
        }
        invalidate(account, parentPath(path))
        return docId(accountId, newPath)
    }

    private companion object {
        const val CACHE_MS = 15_000L

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
        )

        val DEFAULT_DOC_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}

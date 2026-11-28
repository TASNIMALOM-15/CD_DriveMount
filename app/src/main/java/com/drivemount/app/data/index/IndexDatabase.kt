package com.drivemount.app.data.index

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * ফাইল-ইনডেক্স: সাধারণ (এনক্রিপ্ট-না-করা) ক্লাউডের প্রতিটি ফোল্ডারের শেষ দেখা তালিকা।
 * ফোল্ডার খুললে এখান থেকে সঙ্গে সঙ্গে দেখানো হয়, পেছনে ক্লাউড থেকে নতুন তালিকা এনে মিলিয়ে নেওয়া হয়।
 *
 * এনক্রিপ্টেড ভল্টের তালিকা এখানে রাখা হয় না (ফাইলের নাম সাদা অবস্থায় ডিস্কে যেত); সেটি VaultCache-এ এনক্রিপ্টেড থাকে।
 * এটি শুধু ক্যাশ, তাই আলাদা ডাটাবেস: বদলালে বা নষ্ট হলে মুছে আবার তৈরি হয়, অ্যাকাউন্টের কিছু হারায় না।
 */
@Entity(
    tableName = "index_entries",
    primaryKeys = ["accountId", "path"],
    indices = [Index(value = ["accountId", "parent"])],
)
data class IndexEntry(
    val accountId: Long,
    /** অ্যাকাউন্টের root থেকে পূর্ণ পথ */
    val path: String,
    /** যে ফোল্ডারের তালিকায় এটি এসেছে (তার পথ) */
    val parent: String,
    val name: String,
    val isDir: Boolean,
    /** -1 মানে অজানা */
    val size: Long,
    /** -1 মানে অজানা */
    val modTime: Long,
    val mime: String,
)

/** কোন ফোল্ডারের তালিকা শেষবার কখন আনা হয়েছে (খালি ফোল্ডার আর "কখনো দেখা হয়নি" আলাদা করার জন্য) */
@Entity(tableName = "index_folders", primaryKeys = ["accountId", "path"])
data class IndexFolder(
    val accountId: Long,
    val path: String,
    val syncedAt: Long,
)

@Dao
abstract class IndexDao {

    @Query("SELECT syncedAt FROM index_folders WHERE accountId = :accountId AND path = :path")
    abstract suspend fun folderSyncedAt(accountId: Long, path: String): Long?

    @Query("SELECT * FROM index_entries WHERE accountId = :accountId AND parent = :parent")
    abstract suspend fun children(accountId: Long, parent: String): List<IndexEntry>

    @Query("DELETE FROM index_entries WHERE accountId = :accountId AND parent = :parent")
    abstract suspend fun clearChildren(accountId: Long, parent: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEntries(entries: List<IndexEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFolder(folder: IndexFolder)

    /** একটি ফোল্ডারের তালিকা একটাই transaction-এ বদলায়: পাঠক কখনো অর্ধেক তালিকা দেখে না */
    @Transaction
    open suspend fun replaceFolder(accountId: Long, path: String, entries: List<IndexEntry>, now: Long) {
        clearChildren(accountId, path)
        if (entries.isNotEmpty()) insertEntries(entries)
        upsertFolder(IndexFolder(accountId, path, now))
    }

    /** নামে অংশ-মিল (LIKE; q-তে % _ \\ আগেই escape করা)। ফোল্ডার আগে, তারপর নাম অনুযায়ী। */
    @Query(
        "SELECT * FROM index_entries WHERE name LIKE '%' || :q || '%' ESCAPE '\\' " +
            "ORDER BY isDir DESC, name COLLATE NOCASE LIMIT :limit",
    )
    abstract suspend fun search(q: String, limit: Int): List<IndexEntry>

    @Query("DELETE FROM index_folders WHERE accountId NOT IN (:keep)")
    abstract suspend fun deleteFoldersOfOtherAccounts(keep: List<Long>)

    @Query("DELETE FROM index_folders")
    abstract suspend fun deleteAllFolders()

    @Query("DELETE FROM index_folders WHERE syncedAt < :before")
    abstract suspend fun deleteOldFolders(before: Long)

    /** তালিকা-রেকর্ড নেই এমন ফোল্ডারের এন্ট্রি (মুছে ফেলা অ্যাকাউন্ট, পুরোনো তালিকা) সাফ করে */
    @Query(
        "DELETE FROM index_entries WHERE NOT EXISTS " +
            "(SELECT 1 FROM index_folders f WHERE f.accountId = index_entries.accountId AND f.path = index_entries.parent)",
    )
    abstract suspend fun deleteOrphanEntries()
}

@Database(entities = [IndexEntry::class, IndexFolder::class], version = 1, exportSchema = false)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao
}
